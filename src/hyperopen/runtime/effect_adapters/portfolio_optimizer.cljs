(ns hyperopen.runtime.effect-adapters.portfolio-optimizer
  (:require [nexus.registry :as nxr]
            [hyperopen.api.default :as api]
            [hyperopen.api.trading :as trading-api]
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
   :request-vault-details! *request-vault-details!*})

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
