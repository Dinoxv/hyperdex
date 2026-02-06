(ns hyperopen.websocket.webdata2
  (:require [hyperopen.websocket.client :as ws-client]
            [hyperopen.utils.data-normalization :refer [preprocess-webdata2 normalize-asset-contexts]]))

;; WebData2 state
(defonce webdata2-state (atom {:subscriptions #{}
                               :data {}})) ; Map of address -> webdata2 data

;; Subscribe to WebData2 for an address
(defn subscribe-webdata2! [address]
  (when address
    (let [subscription-msg {:method "subscribe"
                            :subscription {:type "webData2"
                                           :user address}}]
      (swap! webdata2-state update :subscriptions conj address)
      (ws-client/send-message! subscription-msg)
      (println "Subscribed to WebData2 for address:" address))))

;; Unsubscribe from WebData2 for an address
(defn unsubscribe-webdata2! [address]
  (when address
    (let [unsubscription-msg {:method "unsubscribe"
                              :subscription {:type "webData2"
                                             :user address}}]
      (swap! webdata2-state update :subscriptions disj address)
      (swap! webdata2-state update :data dissoc address)
      (ws-client/send-message! unsubscription-msg)
      (println "Unsubscribed from WebData2 for address:" address))))

;; Create a handler function that has access to the store
(defn create-webdata2-handler [store]
  (fn [data]
    (when (and (map? data)
               (= "webData2" (:channel data)))
      (let [webdata2-data  (:data data)
            base-update    {:webdata2 webdata2-data}
            full-update    (if (and (map? webdata2-data)
                                    (:meta        webdata2-data)
                                    (:assetCtxs   webdata2-data))
                             {:asset-contexts
                              (-> webdata2-data
                                  preprocess-webdata2
                                  normalize-asset-contexts)}
                             {})]
        (swap! store merge base-update full-update)))))

;; Get current subscriptions
(defn get-subscriptions []
  (:subscriptions @webdata2-state))

;; Get WebData2 data
(defn get-webdata2 []
  (:data @webdata2-state))

;; Get all WebData2 (alias for get-webdata2)
(defn get-all-webdata2 []
  (get-webdata2))

;; Clear WebData2 data
(defn clear-webdata2! []
  (swap! webdata2-state assoc :data nil))

;; Clear all WebData2 data (alias for clear-webdata2!)
(defn clear-all-webdata2! []
  (clear-webdata2!))

;; Initialize WebData2 module
(defn init! [store]
  (println "WebData2 subscription module initialized")
  (println "Registering WebData2 handler with store:" store)
  ;; Register handler for webData2 channel with store access
  (ws-client/register-handler! "webData2" (create-webdata2-handler store))
  (println "WebData2 handler registered successfully")) 
