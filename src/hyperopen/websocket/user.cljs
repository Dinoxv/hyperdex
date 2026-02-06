(ns hyperopen.websocket.user
  (:require [hyperopen.websocket.client :as ws-client]
            [hyperopen.wallet.address-watcher :as address-watcher]))

(defonce user-state (atom {:subscriptions #{}}))

(defn- subscribe! [sub]
  (ws-client/send-message! {:method "subscribe"
                            :subscription sub}))

(defn- unsubscribe! [sub]
  (ws-client/send-message! {:method "unsubscribe"
                            :subscription sub}))

(defn subscribe-user! [address]
  (when address
    (let [subs [{:type "openOrders" :user address}
                {:type "userFills" :user address}
                {:type "userFundings" :user address}
                {:type "userNonFundingLedgerUpdates" :user address}]]
      (doseq [s subs] (subscribe! s))
      (swap! user-state update :subscriptions conj address)
      (println "Subscribed to user streams for:" address))))

(defn unsubscribe-user! [address]
  (when address
    (let [subs [{:type "openOrders" :user address}
                {:type "userFills" :user address}
                {:type "userFundings" :user address}
                {:type "userNonFundingLedgerUpdates" :user address}]]
      (doseq [s subs] (unsubscribe! s))
      (swap! user-state update :subscriptions disj address)
      (println "Unsubscribed from user streams for:" address))))

(defn- upsert-seq [current incoming]
  (let [combined (concat incoming current)]
    (vec (take 200 combined))))

(defn create-user-handler [subscribe-fn unsubscribe-fn]
  (reify address-watcher/IAddressChangeHandler
    (on-address-changed [_ old-address new-address]
      (when old-address (unsubscribe-fn old-address))
      (when new-address (subscribe-fn new-address)))
    (get-handler-name [_] "user-ws-subscription-handler")))

(defn- open-orders-handler [store]
  (fn [msg]
    (when (= "openOrders" (:channel msg))
      (swap! store assoc-in [:orders :open-orders] (:data msg)))))

(defn- user-fills-handler [store]
  (fn [msg]
    (when (= "userFills" (:channel msg))
      (let [data (:data msg)
            snapshot? (:isSnapshot msg)]
        (when (seq data)
          (if snapshot?
            (swap! store assoc-in [:orders :fills] data)
            (swap! store update-in [:orders :fills] #(upsert-seq (or % []) data))))))))

(defn- user-fundings-handler [store]
  (fn [msg]
    (when (= "userFundings" (:channel msg))
      (let [data (:data msg)
            snapshot? (:isSnapshot msg)]
        (when (seq data)
          (if snapshot?
            (swap! store assoc-in [:orders :fundings] data)
            (swap! store update-in [:orders :fundings] #(upsert-seq (or % []) data))))))))

(defn- user-ledger-handler [store]
  (fn [msg]
    (when (= "userNonFundingLedgerUpdates" (:channel msg))
      (let [data (:data msg)
            snapshot? (:isSnapshot msg)]
        (when (seq data)
          (if snapshot?
            (swap! store assoc-in [:orders :ledger] data)
            (swap! store update-in [:orders :ledger] #(upsert-seq (or % []) data))))))))

(defn init! [store]
  (ws-client/register-handler! "openOrders" (open-orders-handler store))
  (ws-client/register-handler! "userFills" (user-fills-handler store))
  (ws-client/register-handler! "userFundings" (user-fundings-handler store))
  (ws-client/register-handler! "userNonFundingLedgerUpdates" (user-ledger-handler store))
  (println "User websocket handlers initialized"))
