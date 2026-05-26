(ns hyperopen.portfolio.optimizer.application.history-loader.api-v2.discovery
  (:require [hyperopen.portfolio.optimizer.application.history-loader.api-v2.codec :as codec]))

(defn- normalize-history-coverage
  [history]
  (when (map? history)
    (let [history* (codec/normalize-api-map history)]
      (cond-> history*
        (:status history*) (update :status codec/keyword-like)
        (:quality-status history*) (update :quality-status codec/keyword-like)
        (map? (:native-only history*))
        (update-in [:native-only :status] codec/keyword-like)
        (map? (:approved-proxy-allowed history*))
        (update-in [:approved-proxy-allowed :status] codec/keyword-like)
        (get-in history* [:approved-proxy-allowed :lineage-kind])
        (update-in [:approved-proxy-allowed :lineage-kind] codec/keyword-like)
        (get-in history* [:approved-proxy-allowed :series-kind])
        (update-in [:approved-proxy-allowed :series-kind] codec/keyword-like)))))

(defn- normalize-discovery-instrument
  [instrument]
  (let [instrument* (codec/normalize-api-map instrument)]
    (cond-> instrument*
      (:instrument-kind instrument*) (update :instrument-kind codec/keyword-like)
      (:proxy instrument*) (update :proxy codec/normalize-proxy)
      (:history instrument*) (update :history normalize-history-coverage))))

(defn normalize-discovery
  [api-body]
  (let [body (codec/normalize-api-map api-body)
        instruments (mapv normalize-discovery-instrument
                          (:instruments body))
        instruments-by-backend-id (into {}
                                        (keep (fn [{:keys [instrument-id] :as instrument}]
                                                (when (seq instrument-id)
                                                  [instrument-id instrument])))
                                        instruments)
        backend-id-by-local-id (into {}
                                     (keep (fn [{:keys [instrument-id aliases]}]
                                             (when-let [local-id (codec/non-blank-text
                                                                  (:hyperopen-market-key aliases))]
                                               [local-id instrument-id])))
                                     instruments)]
    {:status (or (codec/keyword-like (:status body)) :idle)
     :contract-version (:contract-version body)
     :request-id (:request-id body)
     :dataset-version (:dataset-version body)
     :loaded-at-ms (:loaded-at-ms body)
     :instruments-by-backend-id instruments-by-backend-id
     :backend-id-by-local-id backend-id-by-local-id
     :warnings (codec/normalize-warnings (:warnings body))
     :error (:error body)}))

(defn with-discovery-metadata
  [local-market discovery]
  (let [local-id (codec/non-blank-text (or (:key local-market)
                                           (:instrument-id local-market)))
        backend-id (get-in discovery [:backend-id-by-local-id local-id])
        instrument (get-in discovery [:instruments-by-backend-id backend-id])
        history (:history instrument)]
    (cond-> local-market
      backend-id
      (assoc :optimizer-history/instrument-id backend-id)

      (:display-symbol instrument)
      (assoc :optimizer-history/display-symbol (:display-symbol instrument))

      (:instrument-kind instrument)
      (assoc :optimizer-history/instrument-kind (:instrument-kind instrument))

      (:status history)
      (assoc :optimizer-history/history-status (:status history))

      (:quality-status history)
      (assoc :optimizer-history/quality-status (:quality-status history))

      (:proxy instrument)
      (assoc :optimizer-history/proxy (:proxy instrument)))))
