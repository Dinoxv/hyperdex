(ns hyperopen.api.trading
  (:require [clojure.string :as str]
            [hyperopen.wallet.agent-session :as agent-session]
            [hyperopen.utils.hl-signing :as signing]))

(def exchange-url "https://api.hyperliquid.xyz/exchange")

(defn- json-post! [url body]
  (js/fetch url
            (clj->js {:method "POST"
                      :headers {"Content-Type" "application/json"}
                      :body (js/JSON.stringify (clj->js body))})))

(defn- parse-json! [resp]
  (-> (.json resp)
      (.then (fn [payload]
               (js->clj payload :keywordize-keys true)))))

(defn- nonce-error-response? [resp]
  (let [text (-> (or (:error resp)
                     (:response resp)
                     (:message resp)
                     "")
                 str
                 str/lower-case)]
    (and (or (= "err" (:status resp))
             (seq text))
         (str/includes? text "nonce"))))

(defn- next-nonce [cursor]
  (let [now (.now js/Date)
        cursor* (when (number? cursor)
                  (js/Math.floor cursor))
        monotonic-candidate (if (number? cursor*)
                              (inc cursor*)
                              now)]
    (max now monotonic-candidate)))

(defn- post-signed-action!
  [action nonce signature & {:keys [vault-address expires-after]}]
  (let [payload (cond-> {:action action
                         :nonce nonce
                         :signature signature}
                  vault-address (assoc :vaultAddress vault-address)
                  expires-after (assoc :expiresAfter expires-after))]
    (json-post! exchange-url payload)))

(defn- resolve-agent-session
  [store owner-address]
  (let [agent-state (get-in @store [:wallet :agent] {})
        storage-mode (agent-session/normalize-storage-mode (:storage-mode agent-state))
        session (agent-session/load-agent-session-by-mode owner-address storage-mode)]
    (when (map? session)
      (assoc session :storage-mode storage-mode))))

(defn- persist-agent-nonce-cursor!
  [store owner-address session nonce]
  (let [storage-mode (:storage-mode session)
        updated-session (assoc session :nonce-cursor nonce)]
    (agent-session/persist-agent-session-by-mode! owner-address storage-mode updated-session)
    (swap! store update-in [:wallet :agent] merge {:status :ready
                                                   :agent-address (:agent-address session)
                                                   :storage-mode storage-mode
                                                   :nonce-cursor nonce})))

(defn- sign-and-post-agent-action!
  [store owner-address action & {:keys [vault-address expires-after is-mainnet max-nonce-retries]
                                 :or {vault-address nil
                                      expires-after nil
                                      is-mainnet true
                                      max-nonce-retries 1}}]
  (let [session (resolve-agent-session store owner-address)]
    (if-not (and (map? session)
                 (seq (:private-key session)))
      (js/Promise.reject (js/Error. "Agent session unavailable. Enable trading first."))
      (letfn [(attempt! [cursor retries-left]
                (let [nonce (next-nonce cursor)]
                  (-> (signing/sign-l1-action-with-private-key!
                       (:private-key session)
                       action
                       nonce
                       :vault-address vault-address
                       :expires-after expires-after
                       :is-mainnet is-mainnet)
                      (.then
                       (fn [sig]
                         (let [{:keys [r s v]} (js->clj sig :keywordize-keys true)]
                           (-> (post-signed-action! action nonce {:r r :s s :v v}
                                                    :vault-address vault-address
                                                    :expires-after expires-after)
                               (.then parse-json!)
                               (.then
                                (fn [resp]
                                  (if (and (pos? retries-left)
                                           (nonce-error-response? resp))
                                    (attempt! nonce (dec retries-left))
                                    (do
                                      (persist-agent-nonce-cursor! store owner-address session nonce)
                                      resp)))))))))))]
        (attempt! (or (:nonce-cursor session)
                      (get-in @store [:wallet :agent :nonce-cursor]))
                  max-nonce-retries)))))

(defn submit-order!
  [store address action]
  (sign-and-post-agent-action! store address action))

(defn cancel-order!
  [store address action]
  (sign-and-post-agent-action! store address action))

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
