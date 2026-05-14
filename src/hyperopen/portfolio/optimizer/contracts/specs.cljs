(ns hyperopen.portfolio.optimizer.contracts.specs
  (:require [clojure.spec.alpha :as s]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.contracts.migrations :as migrations]
            [hyperopen.portfolio.optimizer.contracts.signatures :as signatures]))

(def result-payload-schema-version 1)

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
         #(= migrations/draft-schema-version (:schema-version %))
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
         #(= signatures/request-signature-schema-version (:schema-version %))
         #(contains-keys? % [:request :input-signature])
         #(map? (:request %))
         #(= (signatures/optimizer-input-signature (:request %))
             (:input-signature %))))

(s/def ::scenario-record
  (s/and map?
         #(= migrations/scenario-record-schema-version (:schema-version %))
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
         #(= migrations/tracking-record-schema-version (:schema-version %))
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
