(ns hyperopen.api.request-policy)

(def default-info-request-ttl-ms
  {:asset-contexts 4000
   :meta-and-asset-ctxs 4000
   :perp-dexs 60000
   :spot-meta 60000
   :public-webdata2 30000
   :user-funding-history 5000
   :historical-orders 5000
   :clearinghouse-state 5000
   :spot-clearinghouse-state 15000
   :user-abstraction 60000
   :portfolio 8000
   :user-fees 15000
   :user-non-funding-ledger 5000
   :market-funding-history 15000
   :predicted-fundings 5000
   :vault-summaries 15000
   :user-vault-equities 5000
   :vault-details 8000
   :vault-webdata2 8000})

(defn default-ttl-ms
  [request-kind]
  (get default-info-request-ttl-ms request-kind))

(defn normalize-ttl-ms
  [value]
  (when (number? value)
    (let [value* (js/Math.floor value)]
      (when (pos? value*)
        value*))))

(defn apply-info-request-policy
  [request-kind opts]
  (let [opts* (or opts {})
        explicit-ttl? (contains? opts* :cache-ttl-ms)
        ttl-ms (normalize-ttl-ms
                (if explicit-ttl?
                  (:cache-ttl-ms opts*)
                  (default-ttl-ms request-kind)))]
    (cond-> opts*
      explicit-ttl? (dissoc :cache-ttl-ms)
      (number? ttl-ms) (assoc :cache-ttl-ms ttl-ms))))
