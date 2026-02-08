(ns hyperopen.websocket.user
  (:require [hyperopen.api :as api]
            [hyperopen.websocket.client :as ws-client]
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

(defn- extract-channel-rows
  [msg collection-key]
  (let [payload (:data msg)]
    (cond
      (and (map? payload)
           (sequential? (get payload collection-key)))
      {:rows (vec (get payload collection-key))
       :snapshot? (boolean (:isSnapshot payload))}

      (sequential? payload)
      {:rows (vec payload)
       :snapshot? (boolean (:isSnapshot msg))}

      :else
      {:rows []
       :snapshot? false})))

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
      (let [{:keys [rows snapshot?]} (extract-channel-rows msg :fills)]
        (when (seq rows)
          (if snapshot?
            (swap! store assoc-in [:orders :fills] rows)
            (swap! store update-in [:orders :fills] #(upsert-seq (or % []) rows))))))))

(defn- user-fundings-handler [store]
  (fn [msg]
    (when (= "userFundings" (:channel msg))
      (let [{:keys [rows]} (extract-channel-rows msg :fundings)
            normalized (api/normalize-ws-funding-rows rows)]
        (when (seq normalized)
          (swap! store
                 (fn [state]
                   (let [existing (get-in state [:orders :fundings-raw] [])
                         filters (get-in state [:account-info :funding-history :filters])
                         merged (api/merge-funding-history-rows existing normalized)
                         filtered (api/filter-funding-history-rows merged filters)]
                     (-> state
                         (assoc-in [:orders :fundings-raw] merged)
                         (assoc-in [:orders :fundings] filtered))))))))))

(defn- user-ledger-handler [store]
  (fn [msg]
    (when (= "userNonFundingLedgerUpdates" (:channel msg))
      (let [{:keys [rows snapshot?]} (extract-channel-rows msg :nonFundingLedgerUpdates)]
        (when (seq rows)
          (if snapshot?
            (swap! store assoc-in [:orders :ledger] rows)
            (swap! store update-in [:orders :ledger] #(upsert-seq (or % []) rows))))))))

(defn init! [store]
  (ws-client/register-handler! "openOrders" (open-orders-handler store))
  (ws-client/register-handler! "userFills" (user-fills-handler store))
  (ws-client/register-handler! "userFundings" (user-fundings-handler store))
  (ws-client/register-handler! "userNonFundingLedgerUpdates" (user-ledger-handler store))
  (println "User websocket handlers initialized"))
