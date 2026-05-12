(ns hyperopen.portfolio.optimizer.contracts
  (:require [clojure.spec.alpha :as s]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.instrument-keyed-codec :as instrument-keyed-codec]))

(def draft-schema-version 1)
(def scenario-record-schema-version 1)
(def tracking-record-schema-version 1)
(def request-signature-schema-version 1)
(def result-payload-schema-version 1)
(def worker-wire-schema-version 1)

(def optimizer-path [:portfolio :optimizer])
(def draft-path (conj optimizer-path :draft))
(def draft-id-path (conj draft-path :id))
(def draft-status-path (conj draft-path :status))
(def draft-universe-path (conj draft-path :universe))
(def draft-objective-path (conj draft-path :objective))
(def draft-return-model-path (conj draft-path :return-model))
(def draft-return-model-views-path (conj draft-return-model-path :views))
(def draft-risk-model-path (conj draft-path :risk-model))
(def draft-constraints-path (conj draft-path :constraints))
(def draft-execution-assumptions-path (conj draft-path :execution-assumptions))
(def draft-metadata-path (conj draft-path :metadata))
(def draft-dirty-path (conj draft-metadata-path :dirty?))
(def active-scenario-path (conj optimizer-path :active-scenario))
(def active-scenario-loaded-id-path (conj active-scenario-path :loaded-id))
(def active-scenario-status-path (conj active-scenario-path :status))
(def active-scenario-read-only-path (conj active-scenario-path :read-only?))
(def run-state-path (conj optimizer-path :run-state))
(def run-state-status-path (conj run-state-path :status))
(def runtime-path (conj optimizer-path :runtime))
(def runtime-as-of-ms-path (conj runtime-path :as-of-ms))
(def runtime-stale-after-ms-path (conj runtime-path :stale-after-ms))
(def runtime-funding-periods-per-year-path
  (conj runtime-path :funding-periods-per-year))
(def runtime-orderbook-stale-after-ms-path
  (conj runtime-path :orderbook-stale-after-ms))
(def history-data-path (conj optimizer-path :history-data))
(def market-cap-by-coin-path (conj optimizer-path :market-cap-by-coin))
(def history-load-state-path (conj optimizer-path :history-load-state))
(def history-load-state-status-path (conj history-load-state-path :status))
(def history-load-state-request-signature-path
  (conj history-load-state-path :request-signature))
(def history-prefetch-path (conj optimizer-path :history-prefetch))
(def history-prefetch-active-instrument-id-path
  (conj history-prefetch-path :active-instrument-id))
(def optimization-progress-path (conj optimizer-path :optimization-progress))
(def optimization-progress-status-path (conj optimization-progress-path :status))
(def scenario-index-path (conj optimizer-path :scenario-index))
(def scenario-save-state-path (conj optimizer-path :scenario-save-state))
(def scenario-load-state-path (conj optimizer-path :scenario-load-state))
(def scenario-index-load-state-path (conj optimizer-path :scenario-index-load-state))
(def scenario-archive-state-path (conj optimizer-path :scenario-archive-state))
(def scenario-duplicate-state-path (conj optimizer-path :scenario-duplicate-state))
(def last-successful-run-path (conj optimizer-path :last-successful-run))
(def last-successful-run-result-path (conj last-successful-run-path :result))
(def execution-modal-path (conj optimizer-path :execution-modal))
(def execution-modal-error-path (conj execution-modal-path :error))
(def execution-modal-submitting-path (conj execution-modal-path :submitting?))
(def execution-path (conj optimizer-path :execution))
(def execution-history-path (conj execution-path :history))
(def execution-persistence-error-path (conj execution-path :persistence-error))
(def tracking-path (conj optimizer-path :tracking))
(def tracking-error-path (conj tracking-path :error))
(def optimizer-ui-path [:portfolio-ui :optimizer])
(def ui-list-filter-path (conj optimizer-ui-path :list-filter))
(def ui-list-sort-path (conj optimizer-ui-path :list-sort))
(def ui-workspace-panel-path (conj optimizer-ui-path :workspace-panel))
(def ui-results-tab-path (conj optimizer-ui-path :results-tab))
(def ui-diagnostics-tab-path (conj optimizer-ui-path :diagnostics-tab))
(def ui-universe-search-query-path (conj optimizer-ui-path :universe-search-query))
(def ui-universe-search-active-index-path
  (conj optimizer-ui-path :universe-search-active-index))
(def ui-black-litterman-editor-path
  (conj optimizer-ui-path :black-litterman-editor))
(def ui-frontier-overlay-mode-path
  (conj optimizer-ui-path :frontier-overlay-mode))
(def ui-constrain-frontier-path (conj optimizer-ui-path :constrain-frontier?))

(declare optimizer-input-signature)

(defn- contains-keys?
  [value ks]
  (and (map? value)
       (every? #(contains? value %) ks)))

(defn- non-blank-string?
  [value]
  (and (string? value)
       (coercion/non-blank-text value)))

(def draft-statuses
  #{:draft :saved :archived :tracking})

(def scenario-record-statuses
  #{:saved :archived :executed :partially-executed :tracking :failed})

(def tracking-snapshot-statuses
  #{:tracked :not-trackable})

(def result-payload-statuses
  #{:solved :infeasible :error :failed})

(defn- absent-or-allowed?
  [allowed value]
  (or (nil? value)
      (contains? allowed value)))

(defn- valid-instrument-map?
  [instrument-ids value]
  (and (map? value)
       (every? #(contains? value %) instrument-ids)))

(defn- solved-result-payload?
  [value]
  (let [instrument-ids (:instrument-ids value)
        target-weights (:target-weights value)
        current-weights (:current-weights value)
        instrument-count (count instrument-ids)]
    (and (vector? instrument-ids)
         (every? non-blank-string? instrument-ids)
         (vector? target-weights)
         (vector? current-weights)
         (= instrument-count (count target-weights))
         (= instrument-count (count current-weights))
         (valid-instrument-map? instrument-ids
                                (:target-weights-by-instrument value))
         (valid-instrument-map? instrument-ids
                                (:current-weights-by-instrument value))
         (valid-instrument-map? instrument-ids
                                (:expected-returns-by-instrument value))
         (or (nil? (:return-decomposition-by-instrument value))
             (map? (:return-decomposition-by-instrument value)))
         (or (nil? (:diagnostics value))
             (map? (:diagnostics value)))
         (or (nil? (:rebalance-preview value))
             (map? (:rebalance-preview value))))))

(s/def ::draft
  (s/and map?
         #(= draft-schema-version (:schema-version %))
         #(contains-keys? % [:universe
                             :objective
                             :return-model
                             :risk-model
                             :constraints
                             :execution-assumptions
                             :metadata])
         #(absent-or-allowed? draft-statuses (:status %))
         #(vector? (:universe %))
         #(map? (:objective %))
         #(map? (:return-model %))
         #(map? (:risk-model %))
         #(map? (:constraints %))
         #(map? (:execution-assumptions %))
         #(map? (:metadata %))))

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
                             :history])
         #(vector? (:universe %))
         #(vector? (:requested-universe %))
         #(map? (:current-portfolio %))
         #(map? (:return-model %))
         #(map? (:risk-model %))
         #(map? (:objective %))
         #(map? (:constraints %))
         #(map? (:execution-assumptions %))
         #(map? (:history %))))

(s/def ::request-signature
  (s/and map?
         #(= request-signature-schema-version (:schema-version %))
         #(contains-keys? % [:request :input-signature])
         #(map? (:request %))
         #(= (optimizer-input-signature (:request %))
             (:input-signature %))))

(s/def ::scenario-record
  (s/and map?
         #(= scenario-record-schema-version (:schema-version %))
         #(contains-keys? % [:id :name :status :config :updated-at-ms])
         #(non-blank-string? (:id %))
         #(string? (:name %))
         #(contains? scenario-record-statuses (:status %))
         #(coercion/finite-number? (:updated-at-ms %))
         #(s/valid? ::draft (:config %))))

(s/def ::tracking-snapshot
  (s/and map?
         #(contains-keys? % [:scenario-id :as-of-ms :status])
         #(non-blank-string? (:scenario-id %))
         #(coercion/finite-number? (:as-of-ms %))
         #(contains? tracking-snapshot-statuses (:status %))
         #(if (= :tracked (:status %))
            (vector? (:rows %))
            true)
         #(if (= :not-trackable (:status %))
            (vector? (:warnings %))
            true)))

(s/def ::tracking-record
  (s/and map?
         #(= tracking-record-schema-version (:schema-version %))
         #(contains-keys? % [:scenario-id :updated-at-ms :snapshots])
         #(non-blank-string? (:scenario-id %))
         #(coercion/finite-number? (:updated-at-ms %))
         #(vector? (:snapshots %))
         #(every? (fn [snapshot]
                    (and (s/valid? ::tracking-snapshot snapshot)
                         (= (:scenario-id %) (:scenario-id snapshot))))
                  (:snapshots %))))

(s/def ::result-payload
  (s/and map?
         #(contains? % :status)
         #(contains? result-payload-statuses (:status %))
         #(if (= :solved (:status %))
            (solved-result-payload? %)
            true)))

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

(def enum-value-keys instrument-keyed-codec/enum-value-keys)
(def instrument-keyed-map-keys instrument-keyed-codec/instrument-keyed-map-keys)
(def instrument-keyed-map-paths instrument-keyed-codec/instrument-keyed-map-paths)
(def instrument-id-key instrument-keyed-codec/instrument-id-key)
(def stringify-instrument-keyed-map
  instrument-keyed-codec/stringify-instrument-keyed-map)
(def normalize-wire-values instrument-keyed-codec/normalize-wire-values)
(def normalize-instrument-keyed-maps
  instrument-keyed-codec/normalize-instrument-keyed-maps)
(def normalize-worker-boundary instrument-keyed-codec/normalize-worker-boundary)
