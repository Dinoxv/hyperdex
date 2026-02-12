(ns hyperopen.wallet.agent-runtime-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [clojure.string :as str]
            [hyperopen.wallet.agent-runtime :as agent-runtime]))

(deftest set-agent-storage-mode-clears-sessions-and-resets-agent-state-test
  (let [cleared (atom [])
        persisted-modes (atom [])
        store (atom {:wallet {:address "0xabc"
                              :agent {:status :ready
                                      :storage-mode :session
                                      :agent-address "0xagent"}}})]
    (agent-runtime/set-agent-storage-mode!
     {:store store
      :storage-mode :local
      :normalize-storage-mode identity
      :clear-agent-session-by-mode! (fn [address mode]
                                      (swap! cleared conj [address mode]))
      :persist-storage-mode-preference! (fn [mode]
                                          (swap! persisted-modes conj mode))
      :default-agent-state (fn [& {:keys [storage-mode]}]
                             {:status :not-ready
                              :storage-mode storage-mode
                              :agent-address nil})
      :agent-storage-mode-reset-message "Trading persistence updated. Enable Trading again."})
    (is (= [["0xabc" :session]
            ["0xabc" :local]]
           @cleared))
    (is (= [:local] @persisted-modes))
    (is (= :not-ready (get-in @store [:wallet :agent :status])))
    (is (= :local (get-in @store [:wallet :agent :storage-mode])))
    (is (str/includes? (str (get-in @store [:wallet :agent :error]))
                       "Trading persistence updated"))))

(deftest enable-agent-trading-sets-error-when-wallet-missing-test
  (let [store (atom {:wallet {:address nil
                              :agent {:status :approving}}})]
    (agent-runtime/enable-agent-trading!
     {:store store
      :options {:storage-mode :session}
      :create-agent-credentials! (fn [] nil)
      :now-ms-fn (fn [] 1)
      :normalize-storage-mode identity
      :default-signature-chain-id-for-environment (fn [_] 1)
      :build-approve-agent-action (fn [& _] nil)
      :approve-agent! (fn [& _] (js/Promise.resolve nil))
      :persist-agent-session-by-mode! (fn [& _] true)
      :runtime-error-message (fn [err] (str err))
      :exchange-response-error (fn [resp] (pr-str resp))})
    (is (= :error (get-in @store [:wallet :agent :status])))
    (is (= "Connect your wallet before enabling trading."
           (get-in @store [:wallet :agent :error])))))

(deftest enable-agent-trading-sets-ready-state-on-success-test
  (async done
    (let [store (atom {:wallet {:address "0x111"
                                :chain-id 42161
                                :agent {:status :approving
                                        :storage-mode :session}}})]
      (agent-runtime/enable-agent-trading!
       {:store store
        :options {:storage-mode :session}
        :create-agent-credentials! (fn []
                                     {:private-key "0xpriv"
                                      :agent-address "0x999"})
        :now-ms-fn (fn [] 1700000000000)
        :normalize-storage-mode identity
        :default-signature-chain-id-for-environment (fn [_] 1)
        :build-approve-agent-action (fn [agent-address nonce & _]
                                      {:agentAddress agent-address
                                       :nonce nonce})
        :approve-agent! (fn [& _]
                          (js/Promise.resolve #js {:json (fn []
                                                          (js/Promise.resolve #js {:status "ok"}))}))
        :persist-agent-session-by-mode! (fn [& _] true)
        :runtime-error-message (fn [err] (str err))
        :exchange-response-error (fn [resp] (pr-str resp))})
      (js/setTimeout
       (fn []
         (try
           (is (= :ready (get-in @store [:wallet :agent :status])))
           (is (= "0x999" (get-in @store [:wallet :agent :agent-address])))
           (is (= 1700000000000 (get-in @store [:wallet :agent :last-approved-at])))
           (is (= nil (get-in @store [:wallet :agent :error])))
           (finally
             (done))))
       0))))
