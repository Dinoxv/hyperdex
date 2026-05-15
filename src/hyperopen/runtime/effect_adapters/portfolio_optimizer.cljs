(ns hyperopen.runtime.effect-adapters.portfolio-optimizer
  (:require [nexus.registry :as nxr]
            [hyperopen.api.default :as api]
            [hyperopen.api.trading :as trading-api]
            [hyperopen.config :as app-config]
            [hyperopen.portfolio.optimizer.application.history-loader.api-v2 :as history-api-v2]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]
            [hyperopen.portfolio.optimizer.infrastructure.history-api-v2-client :as history-api-v2-client]
            [hyperopen.portfolio.optimizer.infrastructure.history-client :as history-client]
            [hyperopen.portfolio.optimizer.infrastructure.persistence :as persistence]
            [hyperopen.portfolio.optimizer.infrastructure.run-bridge :as run-bridge]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer.execution :as execution]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer.history :as history]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer.tracking :as tracking]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer-pipeline :as pipeline]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer-scenarios :as scenario-effects]))

(def ^:dynamic *request-run!* run-bridge/request-run!)
(def ^:dynamic *request-history-bundle!* history-client/request-history-bundle!)
(def ^:dynamic *request-candle-snapshot!* api/request-candle-snapshot!)
(def ^:dynamic *request-market-funding-history!* api/request-market-funding-history!)
(def ^:dynamic *request-vault-details!* api/request-vault-details!)
(def ^:dynamic *optimizer-history-api-config*
  (:optimizer-history-api app-config/config))
(def ^:dynamic *optimizer-history-api-fetch* js/fetch)
(def ^:dynamic *optimizer-history-api-request-id*
  (fn []
    (str "optimizer-history-"
         (.now js/Date)
         "-"
         (js/Math.floor (* 1000000000 (js/Math.random))))))
(def ^:dynamic *load-scenario-index!* persistence/load-scenario-index!)
(def ^:dynamic *load-scenario!* persistence/load-scenario!)
(def ^:dynamic *save-scenario!* persistence/save-scenario!)
(def ^:dynamic *save-scenario-index!* persistence/save-scenario-index!)
(def ^:dynamic *load-tracking!* persistence/load-tracking!)
(def ^:dynamic *save-tracking!* persistence/save-tracking!)
(def ^:dynamic *next-scenario-id* (fn [now-ms] (str "scn_" now-ms)))
(def ^:dynamic *now-ms* #(.now js/Date))
(def ^:dynamic *submit-order!* trading-api/submit-order!)
(def ^:dynamic *dispatch!* nxr/dispatch)

(defn- override-bool
  [m key-a key-b fallback]
  (let [value (if (contains? m key-a)
                (get m key-a)
                (get m key-b))]
    (if (boolean? value)
      value
      fallback)))

(defn- optimizer-history-api-browser-override
  []
  (when-let [raw (aget js/globalThis "__HYPEROPEN_OPTIMIZER_HISTORY_API__")]
    (let [m (js->clj raw :keywordize-keys true)]
      (cond-> {}
        (or (contains? m :enabled?) (contains? m :enabled))
        (assoc :enabled? (override-bool m :enabled? :enabled false))
        (or (:base-url m) (:baseUrl m))
        (assoc :base-url (or (:base-url m) (:baseUrl m)))
        (or (:proxy-policy m) (:proxyPolicy m))
        (assoc :proxy-policy (coercion/normalize-keyword-like
                              (or (:proxy-policy m) (:proxyPolicy m))))
        (or (contains? m :include-aligned-returns?)
            (contains? m :includeAlignedReturns))
        (assoc :include-aligned-returns?
               (override-bool m
                              :include-aligned-returns?
                              :includeAlignedReturns
                              true))
        (or (contains? m :fallback-to-legacy?)
            (contains? m :fallbackToLegacy))
        (assoc :fallback-to-legacy?
               (override-bool m
                              :fallback-to-legacy?
                              :fallbackToLegacy
                              true))))))

(defn- optimizer-history-api-config
  []
  (merge *optimizer-history-api-config*
         (optimizer-history-api-browser-override)))

(defn- request-candle-snapshot!
  [coin opts]
  (*request-candle-snapshot!* coin
                             :interval (:interval opts)
                             :bars (:bars opts)
                             :priority (:priority opts)))

(defn- history-env
  []
  {:now-ms *now-ms*
   :request-history-bundle! *request-history-bundle!*
   :request-candle-snapshot! request-candle-snapshot!
   :request-market-funding-history! *request-market-funding-history!*
   :request-vault-details! *request-vault-details!*
   :optimizer-history-api (optimizer-history-api-config)
   :fetch-fn *optimizer-history-api-fetch*
   :request-id *optimizer-history-api-request-id*})

(defn- scenario-env
  []
  {:now-ms *now-ms*
   :next-scenario-id *next-scenario-id*
   :load-scenario-index! *load-scenario-index!*
   :load-scenario! *load-scenario!*
   :load-tracking! *load-tracking!*
   :save-scenario! *save-scenario!*
   :save-scenario-index! *save-scenario-index!*})

(defn- execution-env
  []
  {:now-ms *now-ms*
   :submit-order! *submit-order!*
   :dispatch! *dispatch!*
   :load-scenario! *load-scenario!*
   :load-scenario-index! *load-scenario-index!*
   :save-scenario! *save-scenario!*
   :save-scenario-index! *save-scenario-index!*})

(defn- tracking-env
  []
  {:now-ms *now-ms*
   :load-tracking! *load-tracking!*
   :save-tracking! *save-tracking!*})

(defn make-portfolio-optimizer-controller-resolver
  [_runtime]
  (run-bridge/make-controller-resolver))

(defn- request-run-with-controller!
  [controller store request request-signature opts]
  (*request-run!*
   (cond-> {:request request
            :request-signature request-signature
            :store store}
     controller
     (assoc :controller controller)
     (contains? opts :computed-at-ms)
     (assoc :computed-at-ms (:computed-at-ms opts))
     (contains? opts :run-id)
     (assoc :run-id (:run-id opts)))))

(defn run-portfolio-optimizer-effect
  ([_ store request request-signature]
   (run-portfolio-optimizer-effect nil store request request-signature nil))
  ([_ store request request-signature opts]
   (request-run-with-controller! nil store request request-signature (or opts {}))))

(defn make-run-portfolio-optimizer
  ([runtime]
   (make-run-portfolio-optimizer
    runtime
    (make-portfolio-optimizer-controller-resolver runtime)))
  ([_runtime controller-resolver]
   (fn [_ store request request-signature & [opts]]
     (request-run-with-controller! (controller-resolver store)
                                   store
                                   request
                                   request-signature
                                   (or opts {})))))

(defn load-portfolio-optimizer-history-effect
  ([_ store]
   (load-portfolio-optimizer-history-effect nil store nil))
  ([_ store opts]
   (history/load-portfolio-optimizer-history-effect
    (history-env)
    nil
    store
    opts)))

(defn- discovery-loading-state
  [request-id started-at-ms]
  (assoc (optimizer-defaults/default-history-discovery-state)
         :status :loading
         :request-id request-id
         :loaded-at-ms started-at-ms))

(defn- discovery-failed-state
  [current err completed-at-ms]
  (assoc (merge (optimizer-defaults/default-history-discovery-state)
                current)
         :status :failed
         :loaded-at-ms completed-at-ms
         :error {:message (or (some-> err .-message)
                              (str err))}))

(defn- apply-discovery!
  [store value]
  (swap! store assoc-in contracts/history-discovery-path value)
  value)

(defn load-portfolio-optimizer-history-discovery-effect
  [_ store]
  (let [config (optimizer-history-api-config)
        fetch-fn *optimizer-history-api-fetch*
        request-id-fn *optimizer-history-api-request-id*
        now-ms-fn *now-ms*]
    (if-not (:enabled? config)
      (js/Promise.resolve nil)
      (let [request-id (request-id-fn)
            started-at-ms (now-ms-fn)]
        (apply-discovery! store (discovery-loading-state request-id started-at-ms))
        (-> (history-api-v2-client/request-instruments!
             {:fetch-fn fetch-fn
              :base-url (:base-url config)
              :request-id request-id})
            (.then (fn [body]
                     (let [discovery (assoc (history-api-v2/normalize-discovery body)
                                            :loaded-at-ms (now-ms-fn)
                                            :error nil)]
                       (apply-discovery! store discovery)
                       discovery)))
            (.catch (fn [err]
                      (apply-discovery!
                       store
                        (discovery-failed-state
                         (get-in @store contracts/history-discovery-path)
                         err
                         (now-ms-fn))))))))))

(defn- run-portfolio-optimizer-pipeline-effect*
  [controller-resolver _ store]
  (pipeline/run-portfolio-optimizer-pipeline-effect
   {:now-ms *now-ms*
    :next-run-id run-bridge/next-run-id
    :request-run! (fn [payload]
                    (*request-run!*
                     (cond-> payload
                       (and controller-resolver (:store payload))
                       (assoc :controller
                              (controller-resolver (:store payload))))))
    :load-history! (fn [store* opts]
                     (load-portfolio-optimizer-history-effect nil store* opts))}
   nil
   store))

(defn run-portfolio-optimizer-pipeline-effect
  [_ store]
  (run-portfolio-optimizer-pipeline-effect*
   (run-bridge/make-controller-resolver)
   nil
   store))

(defn make-run-portfolio-optimizer-pipeline
  ([runtime]
   (make-run-portfolio-optimizer-pipeline
    runtime
    (make-portfolio-optimizer-controller-resolver runtime)))
  ([_runtime controller-resolver]
   (fn [_ store]
     (run-portfolio-optimizer-pipeline-effect*
      controller-resolver
      nil
      store))))

(defn execute-portfolio-optimizer-plan-effect
  ([_ store plan]
   (execution/execute-portfolio-optimizer-plan-effect
    (execution-env)
    nil
    store
    plan)))

(defn refresh-portfolio-optimizer-tracking-effect
  ([_ store]
   (tracking/refresh-portfolio-optimizer-tracking-effect
    (tracking-env)
    nil
    store)))

(defn load-portfolio-optimizer-scenario-index-effect
  ([_ store]
   (load-portfolio-optimizer-scenario-index-effect nil store nil))
  ([_ store opts]
   (scenario-effects/load-portfolio-optimizer-scenario-index-effect
    (scenario-env)
    store
    opts)))

(defn load-portfolio-optimizer-scenario-effect
  ([_ store scenario-id]
   (load-portfolio-optimizer-scenario-effect nil store scenario-id nil))
  ([_ store scenario-id opts]
   (scenario-effects/load-portfolio-optimizer-scenario-effect
    (scenario-env)
    store
    scenario-id
    opts)))

(defn archive-portfolio-optimizer-scenario-effect
  ([_ store scenario-id]
   (archive-portfolio-optimizer-scenario-effect nil store scenario-id nil))
  ([_ store scenario-id opts]
   (scenario-effects/archive-portfolio-optimizer-scenario-effect
    (scenario-env)
    store
    scenario-id
    opts)))

(defn duplicate-portfolio-optimizer-scenario-effect
  ([_ store scenario-id]
   (duplicate-portfolio-optimizer-scenario-effect nil store scenario-id nil))
  ([_ store scenario-id opts]
   (scenario-effects/duplicate-portfolio-optimizer-scenario-effect
    (scenario-env)
    store
    scenario-id
    opts)))

(defn save-portfolio-optimizer-scenario-effect
  ([_ store]
   (save-portfolio-optimizer-scenario-effect nil store nil))
  ([_ store opts]
   (scenario-effects/save-portfolio-optimizer-scenario-effect
    (scenario-env)
    store
    opts)))

(defn enable-portfolio-optimizer-manual-tracking-effect
  ([_ store]
   (scenario-effects/enable-portfolio-optimizer-manual-tracking-effect
    (scenario-env)
    store)))
