(ns hyperopen.api.trading
  (:require [hyperopen.utils.hl-signing :as signing]))

(def exchange-url "https://api.hyperliquid.xyz/exchange")

(defn- json-post! [url body]
  (js/fetch url
            (clj->js {:method "POST"
                      :headers {"Content-Type" "application/json"}
                      :body (js/JSON.stringify (clj->js body))})))

(defn sign-and-post-action!
  [store address action & {:keys [vault-address]}]
  (let [nonce (js/Date.now)]
    (-> (signing/sign-l1-action! address action nonce :vault-address vault-address)
        (.then (fn [sig]
                 (let [{:keys [r s v]} (js->clj sig :keywordize-keys true)
                       payload (cond-> {:action action
                                        :nonce nonce
                                        :signature {:r r
                                                    :s s
                                                    :v v}}
                                 vault-address (assoc :vaultAddress vault-address))]
                   (json-post! exchange-url payload)))))))

(defn submit-order!
  [store address action]
  (sign-and-post-action! store address action))

(defn cancel-order!
  [store address action]
  (sign-and-post-action! store address action))

(defn approve-agent!
  [store address action]
  (-> (signing/sign-approve-agent-action! address action)
      (.then (fn [sig]
               (let [{:keys [r s v]} (js->clj sig :keywordize-keys true)
                     payload {:action action
                              :nonce (:nonce action)
                              :signature {:r r
                                          :s s
                                          :v v}}]
                 (json-post! exchange-url payload))))))
