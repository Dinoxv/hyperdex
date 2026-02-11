(ns hyperopen.wallet.core-test
  (:require [cljs.test :refer-macros [async deftest is]]
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

(deftest set-connected-notifies-handler-when-notify-option-enabled-test
  (let [store (atom {:wallet {:connected? false
                              :address nil
                              :connecting? false
                              :agent {:status :not-ready
                                      :storage-mode :session}}})
        notified (atom [])]
    (with-redefs [agent-session/default-agent-state
                  (fn [& {:keys [storage-mode]}]
                    {:status :not-ready
                     :storage-mode (or storage-mode :session)})
                  agent-session/load-agent-session-by-mode
                  (fn [_ _] nil)]
      (wallet/set-on-connected-handler!
       (fn [_ address]
         (swap! notified conj address)))
      (try
        (wallet/set-connected! store "0xabc" :notify-connected? true)
        (is (= ["0xabc"] @notified))
        (finally
          (wallet/clear-on-connected-handler!))))))

(deftest set-connected-skips-handler-when-notify-option-not-set-test
  (let [store (atom {:wallet {:connected? false
                              :address nil
                              :connecting? true
                              :agent {:status :not-ready
                                      :storage-mode :session}}})
        notified (atom [])]
    (with-redefs [agent-session/default-agent-state
                  (fn [& {:keys [storage-mode]}]
                    {:status :not-ready
                     :storage-mode (or storage-mode :session)})
                  agent-session/load-agent-session-by-mode
                  (fn [_ _] nil)]
      (wallet/set-on-connected-handler!
       (fn [_ address]
         (swap! notified conj address)))
      (try
        (wallet/set-connected! store "0xabc")
        (is (= [] @notified))
        (finally
          (wallet/clear-on-connected-handler!))))))

(deftest request-connection-success-notifies-connected-handler-path-test
  (let [store (atom {:wallet {:connected? false
                              :address nil
                              :connecting? false
                              :error nil}})
        connected-calls (atom [])
        disconnected-calls (atom 0)
        original-queue-microtask js/queueMicrotask]
    (set! js/queueMicrotask (fn [f] (f)))
    (try
      (with-redefs [wallet/has-provider? (fn [] true)
                    wallet/provider (fn []
                                      (let [thenable #js {}]
                                        (set! (.-then thenable)
                                              (fn [on-fulfilled]
                                                (on-fulfilled #js ["0xabc"])
                                                thenable))
                                        (set! (.-catch thenable)
                                              (fn [_]
                                                thenable))
                                        #js {:request (fn [_] thenable)}))
                    wallet/set-connected! (fn [store' addr & {:keys [notify-connected?]}]
                                            (swap! connected-calls conj [store' addr notify-connected?]))
                    wallet/set-disconnected! (fn [_]
                                               (swap! disconnected-calls inc))]
        (wallet/request-connection! store)
        (is (= 1 (count @connected-calls)))
        (is (= "0xabc" (second (first @connected-calls))))
        (is (true? (nth (first @connected-calls) 2)))
        (is (= 0 @disconnected-calls)))
      (finally
        (set! js/queueMicrotask original-queue-microtask)))))
