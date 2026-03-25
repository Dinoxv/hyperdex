(ns hyperopen.runtime.action-adapters.wallet-test
  (:require [cljs.test :refer-macros [deftest is]]
            [nexus.registry :as nxr]
            [hyperopen.platform :as platform]
            [hyperopen.runtime.action-adapters.wallet :as wallet-adapters]
            [hyperopen.wallet.agent-runtime :as agent-runtime]
            [hyperopen.wallet.connection-runtime :as wallet-connection-runtime]))

(deftest enable-agent-trading-injects-platform-now-ms-fn-test
  (let [captured-now-ms (atom nil)]
    (with-redefs [platform/now-ms (fn [] 4242)
                  agent-runtime/enable-agent-trading!
                  (fn [{:keys [now-ms-fn]}]
                    (reset! captured-now-ms (now-ms-fn))
                    nil)]
      (wallet-adapters/enable-agent-trading nil (atom {}) {}))
    (is (= 4242 @captured-now-ms))))

(deftest handle-wallet-connected-refreshes-vault-route-when-active-test
  (let [dispatch-calls (atom [])]
    (with-redefs [wallet-connection-runtime/handle-wallet-connected!
                  (fn [_]
                    :handled)
                  nxr/dispatch (fn [store _ctx effects]
                                 (swap! dispatch-calls conj [store effects]))]
      (let [store (atom {:router {:path "/vaults/0x1234567890abcdef1234567890abcdef12345678"}})
            result (wallet-adapters/handle-wallet-connected store "0xabc")]
        (is (= :handled result))
        (is (= [[store [[:actions/load-vault-route "/vaults/0x1234567890abcdef1234567890abcdef12345678"]]]]
               @dispatch-calls))))))

(deftest handle-wallet-connected-refreshes-staking-route-when-active-test
  (let [dispatch-calls (atom [])]
    (with-redefs [wallet-connection-runtime/handle-wallet-connected!
                  (fn [_]
                    :handled)
                  nxr/dispatch (fn [store _ctx effects]
                                 (swap! dispatch-calls conj [store effects]))]
      (let [store (atom {:router {:path "/staking"}})
            result (wallet-adapters/handle-wallet-connected store "0xabc")]
        (is (= :handled result))
        (is (= [[store [[:actions/load-staking-route "/staking"]]]]
               @dispatch-calls))))))
