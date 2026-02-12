(ns hyperopen.wallet.connection-runtime-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.wallet.connection-runtime :as connection-runtime]))

(deftest should-auto-enable-agent-trading-predicate-guards-connect-state-test
  (is (true? (connection-runtime/should-auto-enable-agent-trading?
              {:wallet {:connected? true
                        :address "0xAbC"
                        :agent {:status :not-ready}}}
              "0xabc")))
  (is (false? (connection-runtime/should-auto-enable-agent-trading?
               {:wallet {:connected? false
                         :address "0xabc"
                         :agent {:status :not-ready}}}
               "0xabc")))
  (is (false? (connection-runtime/should-auto-enable-agent-trading?
               {:wallet {:connected? true
                         :address "0xabc"
                         :agent {:status :ready}}}
               "0xabc")))
  (is (false? (connection-runtime/should-auto-enable-agent-trading?
               {:wallet {:connected? true
                         :address "0xabc"
                         :agent {:status :not-ready}}}
               "0xdef"))))

(deftest connect-wallet-invokes-request-connection-hook-test
  (let [calls (atom [])
        store (atom {:wallet {:connected? false}})]
    (connection-runtime/connect-wallet!
     {:store store
      :log-fn (fn [& _] nil)
      :request-connection! (fn [store-arg]
                             (swap! calls conj (= store store-arg)))})
    (is (= [true] @calls))))

(deftest handle-wallet-connected-dispatches-enable-agent-trading-when-eligible-test
  (let [store (atom {:wallet {:connected? true
                              :address "0xabc"
                              :agent {:status :not-ready}}})
        dispatched (atom [])]
    (connection-runtime/handle-wallet-connected!
     {:store store
      :connected-address "0xabc"
      :should-auto-enable-agent-trading? connection-runtime/should-auto-enable-agent-trading?
      :dispatch! (fn [_ _ actions]
                   (swap! dispatched conj actions))})
    (is (= [[[:actions/enable-agent-trading]]]
           @dispatched))))

(deftest disconnect-wallet-runs-clear-and-disconnect-hooks-test
  (let [calls (atom [])
        store (atom {:wallet {:copy-feedback {:kind :success}
                              :connected? true}
                     :ui {:toast {:kind :success}}})]
    (connection-runtime/disconnect-wallet!
     {:store store
      :log-fn (fn [& _] nil)
      :clear-wallet-copy-feedback-timeout! (fn []
                                             (swap! calls conj :clear-wallet-copy-timeout))
      :clear-order-feedback-toast-timeout! (fn []
                                             (swap! calls conj :clear-order-toast-timeout))
      :clear-order-feedback-toast! (fn [_]
                                     (swap! calls conj :clear-order-toast))
      :set-disconnected! (fn [_]
                           (swap! calls conj :set-disconnected))})
    (is (= [:clear-wallet-copy-timeout
            :clear-order-toast-timeout
            :clear-order-toast
            :set-disconnected]
           @calls))))
