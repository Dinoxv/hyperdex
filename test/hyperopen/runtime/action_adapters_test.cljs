(ns hyperopen.runtime.action-adapters-test
  (:require [cljs.test :refer-macros [deftest is]]
            [nexus.registry :as nxr]
            [hyperopen.platform :as platform]
            [hyperopen.runtime.action-adapters :as action-adapters]
            [hyperopen.vaults.actions :as vault-actions]
            [hyperopen.wallet.agent-runtime :as agent-runtime]
            [hyperopen.wallet.connection-runtime :as wallet-connection-runtime]))

(deftest enable-agent-trading-injects-platform-now-ms-fn-test
  (let [captured-now-ms (atom nil)]
    (with-redefs [platform/now-ms (fn [] 4242)
                  agent-runtime/enable-agent-trading!
                  (fn [{:keys [now-ms-fn]}]
                    (reset! captured-now-ms (now-ms-fn))
                    nil)]
      (action-adapters/enable-agent-trading nil (atom {}) {}))
    (is (= 4242 @captured-now-ms))))

(deftest navigate-appends-vault-route-effects-after-route-projection-test
  (with-redefs [vault-actions/load-vault-route (fn [_state _path]
                                                 [[:effects/save [:vaults-ui :list-loading?] true]
                                                  [:effects/api-fetch-vault-index]])]
    (is (= [[:effects/save [:router :path] "/vaults"]
            [:effects/push-state "/vaults"]
            [:effects/save [:vaults-ui :list-loading?] true]
            [:effects/api-fetch-vault-index]]
           (action-adapters/navigate {} "/vaults")))
    (is (= [[:effects/save [:router :path] "/vaults"]
            [:effects/replace-state "/vaults"]
            [:effects/save [:vaults-ui :list-loading?] true]
            [:effects/api-fetch-vault-index]]
           (action-adapters/navigate {} "/vaults" {:replace? true})))))

(deftest handle-wallet-connected-refreshes-vault-route-when-active-test
  (let [dispatch-calls (atom [])]
    (with-redefs [wallet-connection-runtime/handle-wallet-connected!
                  (fn [_]
                    :handled)
                  nxr/dispatch (fn [store _ctx effects]
                                 (swap! dispatch-calls conj [store effects]))]
      (let [store (atom {:router {:path "/vaults/0x1234567890abcdef1234567890abcdef12345678"}})
            result (action-adapters/handle-wallet-connected store "0xabc")]
        (is (= :handled result))
        (is (= [[store [[:actions/load-vault-route "/vaults/0x1234567890abcdef1234567890abcdef12345678"]]]]
               @dispatch-calls))))))
