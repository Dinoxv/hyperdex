(ns hyperopen.portfolio.optimizer.contracts
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def draft-schema-version 1)
(def scenario-record-schema-version 1)
(def tracking-record-schema-version 1)
(def request-signature-schema-version 1)
(def result-payload-schema-version 1)
(def worker-wire-schema-version 1)

(def optimizer-path [:portfolio :optimizer])
(def draft-path (conj optimizer-path :draft))
(def active-scenario-path (conj optimizer-path :active-scenario))
(def run-state-path (conj optimizer-path :run-state))
(def history-load-state-path (conj optimizer-path :history-load-state))
(def history-prefetch-path (conj optimizer-path :history-prefetch))
(def optimization-progress-path (conj optimizer-path :optimization-progress))
(def scenario-index-path (conj optimizer-path :scenario-index))
(def scenario-save-state-path (conj optimizer-path :scenario-save-state))
(def scenario-load-state-path (conj optimizer-path :scenario-load-state))
(def scenario-index-load-state-path (conj optimizer-path :scenario-index-load-state))
(def scenario-archive-state-path (conj optimizer-path :scenario-archive-state))
(def scenario-duplicate-state-path (conj optimizer-path :scenario-duplicate-state))
(def execution-modal-path (conj optimizer-path :execution-modal))
(def execution-path (conj optimizer-path :execution))
(def tracking-path (conj optimizer-path :tracking))
(def optimizer-ui-path [:portfolio-ui :optimizer])

(defn- contains-keys?
  [value ks]
  (and (map? value)
       (every? #(contains? value %) ks)))

(s/def ::draft
  (s/and map?
         #(= draft-schema-version (:schema-version %))
         #(contains-keys? % [:universe
                             :objective
                             :return-model
                             :risk-model
                             :constraints
                             :execution-assumptions
                             :metadata])))

(s/def ::engine-request
  (s/and map?
         #(contains-keys? % [:universe
                             :requested-universe
                             :current-portfolio
                             :return-model
                             :risk-model
                             :objective
                             :constraints
                             :execution-assumptions
                             :history])))

(s/def ::request-signature
  (s/and map?
         #(= request-signature-schema-version (:schema-version %))
         #(contains-keys? % [:request :input-signature])))

(s/def ::scenario-record
  (s/and map?
         #(= scenario-record-schema-version (:schema-version %))
         #(contains-keys? % [:id :name :status :config :updated-at-ms])
         #(s/valid? ::draft (:config %))))

(s/def ::tracking-snapshot
  (s/and map?
         #(contains-keys? % [:scenario-id :as-of-ms :status])))

(s/def ::tracking-record
  (s/and map?
         #(= tracking-record-schema-version (:schema-version %))
         #(contains-keys? % [:scenario-id :updated-at-ms :snapshots])
         #(vector? (:snapshots %))))

(s/def ::result-payload
  (s/and map?
         #(contains? % :status)))

(s/def ::worker-envelope
  (s/and map?
         #(contains-keys? % [:type :payload])))

(def contract-specs
  {:optimizer/draft ::draft
   :optimizer/engine-request ::engine-request
   :optimizer/request-signature ::request-signature
   :optimizer/scenario-record ::scenario-record
   :optimizer/tracking-record ::tracking-record
   :optimizer/tracking-snapshot ::tracking-snapshot
   :optimizer/result-payload ::result-payload
   :optimizer/worker-envelope ::worker-envelope})

(defn migrate-draft
  [draft]
  (let [draft* (or draft {})
        version (or (:schema-version draft*) draft-schema-version)]
    (case version
      1 (assoc draft* :schema-version draft-schema-version)
      (throw (ex-info "Unsupported optimizer draft schema version."
                      {:contract :optimizer/draft
                       :schema-version version})))))

(defn migrate-scenario-record
  [scenario-record]
  (let [record* (or scenario-record {})
        version (or (:schema-version record*) scenario-record-schema-version)]
    (case version
      1 (cond-> (assoc record* :schema-version scenario-record-schema-version)
          (map? (:config record*))
          (update :config migrate-draft))
      (throw (ex-info "Unsupported optimizer scenario record schema version."
                      {:contract :optimizer/scenario-record
                       :schema-version version})))))

(defn migrate-tracking-record
  [tracking-record]
  (let [record* (or tracking-record {})
        version (or (:schema-version record*) tracking-record-schema-version)]
    (case version
      1 (-> record*
            (assoc :schema-version tracking-record-schema-version)
            (update :snapshots #(vec (or % []))))
      (throw (ex-info "Unsupported optimizer tracking record schema version."
                      {:contract :optimizer/tracking-record
                       :schema-version version})))))

(defn migrate-contract
  [contract-id value]
  (case contract-id
    :optimizer/draft (migrate-draft value)
    :optimizer/scenario-record (migrate-scenario-record value)
    :optimizer/tracking-record (migrate-tracking-record value)
    (throw (ex-info "Unsupported optimizer contract migration."
                    {:contract contract-id}))))

(def optimizer-input-keys
  [:requested-universe
   :universe
   :current-portfolio
   :return-model
   :risk-model
   :objective
   :constraints
   :execution-assumptions
   :history
   :black-litterman-prior])

(defn- stable-execution-assumptions
  [execution-assumptions]
  (when (map? execution-assumptions)
    (dissoc execution-assumptions :cost-contexts-by-id)))

(defn- stable-history
  [history]
  (when (map? history)
    (dissoc history :freshness)))

(defn optimizer-input-signature
  [request]
  (when (map? request)
    (-> (select-keys request optimizer-input-keys)
        (update :execution-assumptions stable-execution-assumptions)
        (update :history stable-history))))

(defn build-request-signature
  [request]
  {:schema-version request-signature-schema-version
   :scenario-id (:scenario-id request)
   :as-of-ms (:as-of-ms request)
   :request request
   :input-signature (optimizer-input-signature request)})

(def enum-value-keys
  #{:code
    :default-order-type
    :fee-mode
    :funding-source
    :instrument-type
    :kind
    :market-type
    :model
    :objective-kind
    :order-type
    :reason
    :side
    :source
    :status
    :strategy
    :type})

(defn- keyword-value
  [value]
  (cond
    (keyword? value) value
    (string? value) (let [text (str/trim value)]
                      (when (seq text)
                        (keyword text)))
    :else value))

(defn instrument-id-key
  [key]
  (cond
    (keyword? key) (subs (str key) 1)
    (string? key) key
    :else (str key)))

(defn stringify-instrument-keyed-map
  [value]
  (if (map? value)
    (into {}
          (map (fn [[key item]]
                 [(instrument-id-key key) item]))
          value)
    value))

(def instrument-keyed-map-paths
  [[:current-portfolio :by-instrument]
   [:history :return-series-by-instrument]
   [:history :price-series-by-instrument]
   [:history :funding-by-instrument]
   [:black-litterman-prior :weights-by-instrument]
   [:constraints :per-asset-overrides]
   [:constraints :per-perp-leverage-caps]
   [:execution-assumptions :prices-by-id]
   [:execution-assumptions :cost-contexts-by-id]
   [:execution-assumptions :fee-bps-by-id]
   [:payload :return-decomposition-by-instrument]
   [:payload :expected-returns-by-instrument]
   [:payload :current-weights-by-instrument]
   [:payload :target-weights-by-instrument]
   [:payload :diagnostics :weight-sensitivity-by-instrument]
   [:return-decomposition-by-instrument]
   [:expected-returns-by-instrument]
   [:current-weights-by-instrument]
   [:target-weights-by-instrument]
   [:diagnostics :weight-sensitivity-by-instrument]])

(defn normalize-wire-values
  [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[key item]]
                 (let [item* (normalize-wire-values item)]
                   [key (if (contains? enum-value-keys key)
                          (keyword-value item*)
                          item*)])))
          value)

    (vector? value)
    (mapv normalize-wire-values value)

    (seq? value)
    (doall (map normalize-wire-values value))

    :else value))

(defn- update-existing-in
  [value path f]
  (if (nil? (get-in value path))
    value
    (update-in value path f)))

(defn normalize-instrument-keyed-maps
  [value]
  (reduce (fn [value* path]
            (update-existing-in value* path stringify-instrument-keyed-map))
          value
          instrument-keyed-map-paths))

(defn- normalize-black-litterman-view-weights
  [value]
  (update-existing-in
   value
   [:return-model :views]
   (fn [views]
     (mapv (fn [view]
             (update-existing-in view [:weights] stringify-instrument-keyed-map))
           views))))

(defn normalize-worker-boundary
  [value]
  (-> value
      normalize-wire-values
      normalize-instrument-keyed-maps
      normalize-black-litterman-view-weights))
