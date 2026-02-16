(ns hyperopen.websocket.trades
  (:require [hyperopen.platform :as platform]
            [hyperopen.telemetry :as telemetry]
            [hyperopen.websocket.client :as ws-client]
            [hyperopen.utils.interval :as interval]
            [hyperopen.websocket.trades-policy :as policy]))

(def ^:private max-recent-trades 100)

;; Trades state
(defonce trades-state (atom {:subscriptions #{}
                             :trades []
                             :trades-by-coin {}}))

(defonce trades-buffer (atom {:pending [] :timer nil}))

(defn- trade-time-ms [trade]
  (or (:time-ms trade) 0))

(defn- normalize-trade-for-view [trade]
  (let [price-raw (or (:px trade) (:price trade) (:p trade))
        size-raw (or (:sz trade) (:size trade) (:s trade))
        time-raw (or (:time trade) (:t trade) (:ts trade) (:timestamp trade))]
    {:coin (or (:coin trade) (:symbol trade) (:asset trade))
     :price (policy/parse-number price-raw)
     :price-raw price-raw
     :size (or (policy/parse-number size-raw) 0)
     :size-raw size-raw
     :side (or (:side trade) (:dir trade))
     :time-ms (policy/time->ms time-raw)
     :tid (or (:tid trade) (:id trade))}))

(defn- upsert-trades-by-coin [trades-by-coin normalized-trades]
  (let [incoming-by-coin (->> normalized-trades
                              (filter (fn [trade]
                                        (let [coin (:coin trade)]
                                          (and (string? coin)
                                               (not= "" coin)))))
                              (group-by :coin))]
    (reduce-kv (fn [acc coin incoming]
                 (assoc acc
                        coin
                        (->> (concat incoming (get acc coin []))
                             (sort-by trade-time-ms >)
                             (take max-recent-trades)
                             vec)))
               (or trades-by-coin {})
               incoming-by-coin)))

(defn- ingest-trades! [incoming-trades]
  (let [incoming (vec incoming-trades)
        normalized (mapv normalize-trade-for-view incoming)]
    (swap! trades-state
           (fn [state]
             (-> state
                 (update :trades #(take max-recent-trades (concat incoming (or % []))))
                 (update :trades-by-coin upsert-trades-by-coin normalized))))))

;; Subscribe to trades for a symbol
(defn subscribe-trades! [symbol]
  (when symbol
    (let [subscription-msg {:method "subscribe"
                            :subscription {:type "trades"
                                           :coin symbol}}]
      (swap! trades-state update :subscriptions conj symbol)
      (ws-client/send-message! subscription-msg)
      (telemetry/log! "Subscribed to trades for:" symbol))))

;; Unsubscribe from trades for a symbol
(defn unsubscribe-trades! [symbol]
  (when symbol
    (let [unsubscription-msg {:method "unsubscribe"
                              :subscription {:type "trades"
                                             :coin symbol}}]
      (swap! trades-state update :subscriptions disj symbol)
      (ws-client/send-message! unsubscription-msg)
      (telemetry/log! "Unsubscribed from trades for:" symbol))))

(defn- update-candles-from-trades! [store trades]
  (let [state @store
        active-asset (:active-asset state)
        selected-timeframe (get-in state [:chart-options :selected-timeframe] :1d)
        interval-ms (interval/interval-to-milliseconds selected-timeframe)]
    (when (and active-asset (seq trades))
      (let [normalize-trades-xf (comp
                                  (map policy/normalize-trade)
                                  (filter #(and (:time-ms %) (:price %)))
                                  (filter (fn [trade]
                                            (or (nil? (:coin trade))
                                                (= active-asset (:coin trade))))))
            normalized (->> trades
                            (into [] normalize-trades-xf)
                            (sort-by :time-ms))
            update-fn (fn [entry]
                        (let [raw (cond
                                    (vector? entry) entry
                                    (and (map? entry) (vector? (:data entry))) (:data entry)
                                    :else [])
                              max-count (when (seq raw) (count raw))
                              updated (reduce (fn [acc trade]
                                                (policy/upsert-candle acc interval-ms trade max-count))
                                              (vec raw)
                                              normalized)]
                          (cond
                            (vector? entry) updated
                            (map? entry) (assoc (or entry {}) :data updated)
                            :else updated)))]
        (when (seq normalized)
          (swap! store update-in [:candles active-asset selected-timeframe] update-fn))))))

(defn- schedule-candle-update! [store trades]
  (swap! trades-buffer update :pending into trades)
  (when-not (:timer @trades-buffer)
    (let [timeout-id (platform/set-timeout!
                       (fn []
                         (let [pending (:pending @trades-buffer)]
                           (swap! trades-buffer assoc :pending [] :timer nil)
                           (when (seq pending)
                             (update-candles-from-trades! store pending))))
                       500)]
      (swap! trades-buffer assoc :timer timeout-id))))

;; Handle incoming trade data
(defn handle-trade-data! [data]
  (telemetry/log! "Processing trade data:" data)
  (when (and (map? data) (= (:channel data) "trades"))
    (let [trades (:data data)]
      (when (seq trades)
        (ingest-trades! trades)
        (telemetry/log! "Received" (count trades) "new trades")
        (telemetry/log! "Latest trade:" (first trades))))))

(defn create-trades-handler [store]
  (fn [data]
    (when (and (map? data) (= (:channel data) "trades"))
      (let [trades (:data data)]
        (when (seq trades)
          (ingest-trades! trades)
          (schedule-candle-update! store trades))))))

;; Get current subscriptions
(defn get-subscriptions []
  (:subscriptions @trades-state))

;; Get recent trades
(defn get-recent-trades []
  (:trades @trades-state))

(defn get-recent-trades-for-coin [coin]
  (if (seq coin)
    (get-in @trades-state [:trades-by-coin coin] [])
    []))

;; Clear all trades data
(defn clear-trades! []
  (swap! trades-state assoc :trades [] :trades-by-coin {}))

;; Initialize trades module
(defn init! [store]
  (telemetry/log! "Trades subscription module initialized")
  ;; Register handler for trades channel
  (ws-client/register-handler! "trades" (create-trades-handler store)))
