(ns hyperopen.api.endpoints.market
  (:require [hyperopen.asset-selector.markets :as markets]
            [hyperopen.api.market-metadata.perp-dexs :as perp-dexs]
            [hyperopen.api.request-policy :as request-policy]
            [hyperopen.utils.data-normalization :refer [normalize-asset-contexts]]
            [hyperopen.utils.interval :refer [interval-to-milliseconds]]))

(defn request-asset-contexts!
  [post-info! opts]
  (let [opts* (request-policy/apply-info-request-policy
               :asset-contexts
               (merge {:priority :high
                       :dedupe-key :asset-contexts}
                      opts))]
    (-> (post-info! {"type" "metaAndAssetCtxs"}
                    opts*)
      (.then normalize-asset-contexts))))

(defn request-meta-and-asset-ctxs!
  [post-info! dex opts]
  (let [body (cond-> {"type" "metaAndAssetCtxs"}
               (and dex (not= dex "")) (assoc "dex" dex))
        dedupe-key (or (:dedupe-key opts)
                       (if (seq dex)
                         [:meta-and-asset-ctxs dex]
                         :meta-and-asset-ctxs-default))
        opts* (request-policy/apply-info-request-policy
               :meta-and-asset-ctxs
               (merge {:priority :high
                       :dedupe-key dedupe-key}
                      opts))]
    (post-info! body
                opts*)))

(defn request-perp-dexs!
  [post-info! opts]
  (let [opts* (request-policy/apply-info-request-policy
               :perp-dexs
               (merge {:priority :high
                       :dedupe-key :perp-dexs}
                      opts))]
    (-> (post-info! {"type" "perpDexs"}
                    opts*)
      (.then perp-dexs/normalize-perp-dex-payload))))

(defn request-candle-snapshot!
  [post-info! now-ms-fn coin {:keys [interval bars priority]
                              :or {interval :1d bars 330 priority :high}}]
  (if (nil? coin)
    (js/Promise.resolve nil)
    (let [now (now-ms-fn)
          ms (interval-to-milliseconds interval)
          start (- now (* bars ms))
          interval-s (name interval)
          body {"type" "candleSnapshot"
                "req" {"coin" coin
                       "interval" interval-s
                       "startTime" start
                       "endTime" now}}]
      (post-info! body {:priority priority}))))

(defn request-spot-meta!
  [post-info! opts]
  (post-info! {"type" "spotMeta"}
              (request-policy/apply-info-request-policy
               :spot-meta
               (merge {:priority :high
                       :dedupe-key :spot-meta}
                      opts))))

(defn request-public-webdata2!
  [post-info! opts]
  (post-info! {"type" "webData2"
               "user" "0x0000000000000000000000000000000000000000"}
              (request-policy/apply-info-request-policy
               :public-webdata2
               (merge {:priority :high
                       :dedupe-key :public-webdata2}
                      opts))))

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn- parse-decimal
  [value]
  (cond
    (number? value)
    (when (finite-number? value)
      value)

    (string? value)
    (let [parsed (js/parseFloat value)]
      (when (finite-number? parsed)
        parsed))

    :else
    nil))

(defn- parse-ms
  [value]
  (when-let [parsed (parse-decimal value)]
    (js/Math.floor parsed)))

(defn- normalize-market-funding-history-row
  [row]
  (when (map? row)
    (let [time-ms (or (parse-ms (:time row))
                      (parse-ms (:time-ms row)))
          coin (when (string? (:coin row))
                 (:coin row))
          funding-rate (parse-decimal (or (:fundingRate row)
                                          (:funding-rate row)))
          premium (parse-decimal (:premium row))]
      (when (and (number? time-ms)
                 (seq coin)
                 (number? funding-rate))
        {:coin coin
         :time-ms time-ms
         :time time-ms
         :funding-rate-raw funding-rate
         :fundingRate funding-rate
         :premium premium}))))

(defn- normalize-market-funding-history-rows
  [rows]
  (->> rows
       (keep normalize-market-funding-history-row)
       (sort-by :time-ms)
       vec))

(defn- market-funding-history-seq
  [payload]
  (cond
    (sequential? payload)
    payload

    (map? payload)
    (let [data (:data payload)
          nested (or (:fundingHistory payload)
                     (:funding-history payload)
                     (when (map? data)
                       (or (:fundingHistory data)
                           (:funding-history data)))
                     data)]
      (if (sequential? nested)
        nested
        []))

    :else
    []))

(defn request-market-funding-history!
  [post-info! coin opts]
  (let [coin* (some-> coin str .trim)
        start-time-ms (or (:start-time-ms opts)
                          (:startTime opts))
        end-time-ms (or (:end-time-ms opts)
                        (:endTime opts))]
    (if-not (seq coin*)
      (js/Promise.resolve [])
      (let [body (cond-> {"type" "fundingHistory"
                          "coin" coin*}
                   (number? start-time-ms) (assoc "startTime" (js/Math.floor start-time-ms))
                   (number? end-time-ms) (assoc "endTime" (js/Math.floor end-time-ms)))
            request-opts (request-policy/apply-info-request-policy
                          :market-funding-history
                          (merge {:priority :high
                                  :dedupe-key [:market-funding-history coin* start-time-ms end-time-ms]}
                                 (dissoc (or opts {})
                                         :start-time-ms
                                         :end-time-ms
                                         :startTime
                                         :endTime)))]
        (-> (post-info! body request-opts)
            (.then market-funding-history-seq)
            (.then normalize-market-funding-history-rows))))))

(defn request-predicted-fundings!
  [post-info! opts]
  (post-info! {"type" "predictedFundings"}
              (request-policy/apply-info-request-policy
               :predicted-fundings
               (merge {:priority :high
                       :dedupe-key :predicted-fundings}
                      opts))))

(defn- build-market-index-by-key
  [markets]
  (reduce-kv (fn [acc idx market]
               (if-let [market-key (:key market)]
                 (assoc acc market-key idx)
                 acc))
             {}
             (vec (or markets []))))

(defn build-market-state
  [now-ms-fn active-asset phase dexs spot-meta spot-asset-ctxs perp-results]
  (let [dexs-with-default (if (= phase :bootstrap)
                            [nil]
                            (vec (cons nil (vec dexs))))
        token-by-index (into {}
                             (map (fn [{:keys [index name]}]
                                    [index name]))
                             (:tokens spot-meta))
        perp-markets (->> (map-indexed vector (map vector dexs-with-default perp-results))
                          (mapcat (fn [[perp-dex-index [dex [meta asset-ctxs]]]]
                                    (markets/build-perp-markets
                                     meta
                                     asset-ctxs
                                     token-by-index
                                     :dex dex
                                     :perp-dex-index perp-dex-index)))
                          vec)
        spot-markets (markets/build-spot-markets spot-meta spot-asset-ctxs)
        all-markets (vec (concat perp-markets spot-markets))
        market-by-key (into {}
                            (map (fn [m] [(:key m) m]))
                            all-markets)
        market-index-by-key (build-market-index-by-key all-markets)
        active-market (when active-asset
                        (markets/resolve-market-by-coin
                         market-by-key
                         active-asset))]
    {:markets all-markets
     :market-by-key market-by-key
     :market-index-by-key market-index-by-key
     :active-market active-market
     :loaded-at-ms (now-ms-fn)}))
