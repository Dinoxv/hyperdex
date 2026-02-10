(ns hyperopen.wallet.core-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.wallet.agent-session :as agent-session]
            [hyperopen.wallet.core :as wallet]))

(deftest set-disconnected-clears-agent-session-and-resets-state-test
  (let [store (atom {:wallet {:connected? true
                              :address "0xabc"
                              :chain-id "0x1"
                              :agent {:status :ready
                                      :storage-mode :session
                                      :agent-address "0xagent"}}})
        cleared (atom [])]
    (with-redefs [agent-session/default-agent-state
                  (fn [& {:keys [storage-mode]}]
                    {:status :not-ready
                     :storage-mode (or storage-mode :session)})
                  agent-session/clear-agent-session-by-mode!
                  (fn [wallet-address storage-mode]
                    (swap! cleared conj [wallet-address storage-mode]))]
      (wallet/set-disconnected! store)
      (is (= [["0xabc" :session]] @cleared))
      (is (= false (get-in @store [:wallet :connected?])))
      (is (nil? (get-in @store [:wallet :address])))
      (is (= "0x1" (get-in @store [:wallet :chain-id])))
      (is (= :not-ready (get-in @store [:wallet :agent :status]))))))

(deftest set-connected-account-switch-clears-prior-session-and-loads-new-session-test
  (let [store (atom {:wallet {:connected? true
                              :address "0xold"
                              :agent {:status :ready
                                      :storage-mode :session
                                      :agent-address "0xoldagent"}}})
        cleared (atom [])
        loaded (atom [])]
    (with-redefs [agent-session/default-agent-state
                  (fn [& {:keys [storage-mode]}]
                    {:status :not-ready
                     :storage-mode (or storage-mode :session)})
                  agent-session/clear-agent-session-by-mode!
                  (fn [wallet-address storage-mode]
                    (swap! cleared conj [wallet-address storage-mode]))
                  agent-session/load-agent-session-by-mode
                  (fn [wallet-address storage-mode]
                    (swap! loaded conj [wallet-address storage-mode])
                    {:agent-address "0xnewagent"
                     :last-approved-at 1700000003333
                     :nonce-cursor 1700000003333})]
      (wallet/set-connected! store "0xnew")
      (is (= [["0xold" :session]] @cleared))
      (is (= [["0xnew" :session]] @loaded))
      (is (= true (get-in @store [:wallet :connected?])))
      (is (= "0xnew" (get-in @store [:wallet :address])))
      (is (= :ready (get-in @store [:wallet :agent :status])))
      (is (= "0xnewagent" (get-in @store [:wallet :agent :agent-address]))))))
