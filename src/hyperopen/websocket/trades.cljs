(ns hyperopen.websocket.trades
  (:require [hyperopen.websocket.client :as ws-client]))

;; Trades state
(defonce trades-state (atom {:subscriptions #{}
                             :trades []}))

;; Subscribe to trades for a symbol
(defn subscribe-trades! [symbol]
  (when (ws-client/connected?)
    (let [subscription-msg {:method "subscribe"
                            :subscription {:type "trades"
                                           :coin symbol}}]
      (ws-client/send-message! subscription-msg)
      (swap! trades-state update :subscriptions conj symbol)
      (println "Subscribed to trades for:" symbol))))

;; Unsubscribe from trades for a symbol
(defn unsubscribe-trades! [symbol]
  (when (ws-client/connected?)
    (let [unsubscription-msg {:method "unsubscribe"
                              :subscription {:type "trades"
                                             :coin symbol}}]
      (ws-client/send-message! unsubscription-msg)
      (swap! trades-state update :subscriptions disj symbol)
      (println "Unsubscribed from trades for:" symbol))))

;; Handle incoming trade data
(defn handle-trade-data! [data]
  (println "Processing trade data:" data)
  (when (and (map? data) (= (:channel data) "trades"))
    (let [trades (:data data)]
      (when (seq trades)
        (swap! trades-state update :trades 
               #(take 100 (concat trades %))) ; Keep last 100 trades
        (println "Received" (count trades) "new trades")
        (println "Latest trade:" (first trades))))))

;; Get current subscriptions
(defn get-subscriptions []
  (:subscriptions @trades-state))

;; Get recent trades
(defn get-recent-trades []
  (:trades @trades-state))

;; Clear all trades data
(defn clear-trades! []
  (swap! trades-state assoc :trades []))

;; Initialize trades module
(defn init! []
  (println "Trades subscription module initialized")
  ;; Register handler for trades channel
  (ws-client/register-handler! "trades" handle-trade-data!))