(ns hyperopen.portfolio.optimizer.contracts.migrations)

(def draft-schema-version 1)
(def scenario-record-schema-version 1)
(def tracking-record-schema-version 1)

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
