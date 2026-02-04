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
                 (let [payload (cond-> {:action action
                                        :nonce nonce
                                        :signature {:r (.-r sig)
                                                    :s (.-s sig)
                                                    :v (.-v sig)}}
                                 vault-address (assoc :vaultAddress vault-address))]
                   (json-post! exchange-url payload)))))))

(defn submit-order!
  [store address action]
  (sign-and-post-action! store address action))

(defn cancel-order!
  [store address action]
  (sign-and-post-action! store address action))
