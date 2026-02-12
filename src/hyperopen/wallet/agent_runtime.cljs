(ns hyperopen.wallet.agent-runtime)

(defn exchange-response-error
  [resp]
  (or (:error resp)
      (:response resp)
      (:message resp)
      (pr-str resp)))

(defn runtime-error-message
  [err]
  (or (some-> err .-message str)
      (some-> err (aget "message") str)
      (some-> err (aget "data") (aget "message") str)
      (some-> err (aget "error") (aget "message") str)
      (when (map? err)
        (or (some-> (:message err) str)
            (some-> err :data :message str)
            (some-> err :error :message str)))
      (try
        (let [clj-value (js->clj err :keywordize-keys true)]
          (when (map? clj-value)
            (or (some-> (:message clj-value) str)
                (some-> clj-value :data :message str)
                (some-> clj-value :error :message str)
                (pr-str clj-value))))
        (catch :default _
          nil))
      (str err)))

(defn set-agent-storage-mode!
  [{:keys [store
           storage-mode
           normalize-storage-mode
           clear-agent-session-by-mode!
           persist-storage-mode-preference!
           default-agent-state
           agent-storage-mode-reset-message]}]
  (let [next-mode (normalize-storage-mode storage-mode)
        current-mode (normalize-storage-mode
                      (get-in @store [:wallet :agent :storage-mode]))
        wallet-address (get-in @store [:wallet :address])
        switching? (not= current-mode next-mode)]
    (when switching?
      (when (seq wallet-address)
        (clear-agent-session-by-mode! wallet-address current-mode)
        (clear-agent-session-by-mode! wallet-address next-mode))
      (persist-storage-mode-preference! next-mode)
      (swap! store assoc-in [:wallet :agent]
             (assoc (default-agent-state :storage-mode next-mode)
                    :error agent-storage-mode-reset-message)))))

(defn- set-agent-error!
  [store error]
  (swap! store update-in [:wallet :agent] merge
         {:status :error
          :error error
          :agent-address nil
          :last-approved-at nil
          :nonce-cursor nil}))

(defn enable-agent-trading!
  [{:keys [store
           options
           create-agent-credentials!
           now-ms-fn
           normalize-storage-mode
           default-signature-chain-id-for-environment
           build-approve-agent-action
           approve-agent!
           persist-agent-session-by-mode!
           runtime-error-message
           exchange-response-error]}]
  (let [{:keys [storage-mode is-mainnet agent-name signature-chain-id]
         :or {storage-mode :local
              is-mainnet true
              agent-name nil
              signature-chain-id nil}} options
        owner-address (get-in @store [:wallet :address])]
    (if-not (seq owner-address)
      (set-agent-error! store "Connect your wallet before enabling trading.")
      (try
        (let [{:keys [private-key agent-address]} (create-agent-credentials!)
              nonce (now-ms-fn)
              normalized-storage-mode (normalize-storage-mode storage-mode)
              wallet-chain-id (get-in @store [:wallet :chain-id])
              resolved-signature-chain-id (or signature-chain-id
                                              wallet-chain-id
                                              (default-signature-chain-id-for-environment is-mainnet))
              action (build-approve-agent-action
                      agent-address
                      nonce
                      :agent-name agent-name
                      :is-mainnet is-mainnet
                      :signature-chain-id resolved-signature-chain-id)]
          (-> (approve-agent! store owner-address action)
              (.then #(.json %))
              (.then (fn [resp]
                       (let [data (js->clj resp :keywordize-keys true)]
                         (if (= "ok" (:status data))
                           (let [persisted? (persist-agent-session-by-mode!
                                             owner-address
                                             normalized-storage-mode
                                             {:agent-address agent-address
                                              :private-key private-key
                                              :last-approved-at nonce
                                              :nonce-cursor nonce})]
                             (if persisted?
                               (swap! store update-in [:wallet :agent] merge
                                      {:status :ready
                                       :agent-address agent-address
                                       :storage-mode normalized-storage-mode
                                       :last-approved-at nonce
                                       :error nil
                                       :nonce-cursor nonce})
                               (set-agent-error! store "Unable to persist agent credentials.")))
                           (set-agent-error! store (exchange-response-error data))))))
              (.catch (fn [err]
                        (set-agent-error! store (runtime-error-message err))))))
        (catch :default err
          (set-agent-error! store (runtime-error-message err)))))))
