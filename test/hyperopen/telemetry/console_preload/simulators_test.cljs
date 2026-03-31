(ns hyperopen.telemetry.console-preload.simulators-test
  (:require [cljs.test :refer-macros [async deftest is testing]]
            [hyperopen.api.default :as api]
            [hyperopen.api.service :as api-service]
            [hyperopen.api.trading :as trading-api]
            [hyperopen.runtime.validation :as runtime-validation]
            [hyperopen.telemetry :as telemetry]
            [hyperopen.telemetry.console-preload :as console-preload]
            [hyperopen.telemetry.console-preload.simulators :as simulators]
            [hyperopen.test-support.async :as async-support]
            [hyperopen.wallet.core :as wallet-core]
            [hyperopen.websocket.client :as ws-client]))

(defn- capture-browser-state
  []
  (let [window* (aget js/globalThis "window")]
    {:window window*
     :window-provider (some-> window* (aget "ethereum"))
     :global-provider (aget js/globalThis "ethereum")}))

(defn- restore-browser-state!
  [{:keys [window window-provider global-provider]}]
  (if (some? global-provider)
    (aset js/globalThis "ethereum" global-provider)
    (js-delete js/globalThis "ethereum"))
  (if (some? window)
    (do
      (aset window "ethereum" window-provider)
      (aset js/globalThis "window" window))
    (js-delete js/globalThis "window")))

(defn- cleanup-simulator-state!
  [browser-state]
  (try
    (simulators/set-wallet-connected-handler-mode! "passthrough")
    (catch :default _ nil))
  (simulators/clear-wallet-simulator!)
  (simulators/clear-account-request-simulator!)
  (simulators/clear-exchange-simulator!)
  (api/reset-api-service!)
  (wallet-core/clear-provider-override!)
  (wallet-core/reset-provider-listener-state!)
  (wallet-core/clear-on-connected-handler!)
  (restore-browser-state! browser-state))

(defn- install-stub-api-service!
  [request-info!]
  (api/install-api-service!
   (api-service/make-service
    {:info-client-instance
     {:request-info! request-info!
      :get-request-stats (fn [] {})
      :reset! (fn [] nil)}
     :log-fn (fn [& _] nil)})))

(defn- install-provider!
  ([config]
   (install-provider! config (fn [_] nil)))
  ([config attach-fn]
   (with-redefs [wallet-core/attach-listeners! attach-fn]
     (simulators/install-wallet-simulator! config)
     (aget js/globalThis "ethereum"))))

(deftest normalize-wallet-simulator-config-defaults-and-aliases-test
  (let [normalize-config @#'simulators/normalize-wallet-simulator-config
        defaults (normalize-config nil)
        aliases (normalize-config #js {:accounts #js ["0xabc" 42]
                                       :requestAccounts #js ["0xdef"]
                                       :chainId "0x1"
                                       :accountsError "accounts denied"
                                       :requestAccountsError "request denied"
                                       :typedDataSignature "0xsig"
                                       :typedDataError "typed denied"
                                       :switchChainError "switch denied"})]
    (is (= [] (:accounts defaults)))
    (is (= [] (:request-accounts defaults)))
    (is (= "0xa4b1" (:chain-id defaults)))
    (is (= 132 (count (:typed-data-signature defaults))))
    (is (= ["0xabc" "42"] (:accounts aliases)))
    (is (= ["0xdef"] (:request-accounts aliases)))
    (is (= "0x1" (:chain-id aliases)))
    (is (= "accounts denied" (:accounts-error aliases)))
    (is (= "request denied" (:request-accounts-error aliases)))
    (is (= "0xsig" (:typed-data-signature aliases)))
    (is (= "typed denied" (:typed-data-error aliases)))
    (is (= "switch denied" (:switch-chain-error aliases)))))

(deftest normalize-wallet-simulator-config-falls-back-request-accounts-to-accounts-test
  (let [normalize-config @#'simulators/normalize-wallet-simulator-config
        normalized (normalize-config {:accounts ["0xabc" "0xdef"]})]
    (is (= ["0xabc" "0xdef"] (:accounts normalized)))
    (is (= ["0xabc" "0xdef"] (:request-accounts normalized)))))

(deftest install-and-clear-wallet-simulator-restores-browser-and-wallet-core-provider-test
  (let [browser-state (capture-browser-state)
        existing-window #js {}
        existing-provider #js {:request (fn [_] "existing-provider")}
        attach-calls (atom [])]
    (try
      (aset js/globalThis "window" existing-window)
      (aset existing-window "ethereum" existing-provider)
      (aset js/globalThis "ethereum" existing-provider)
      (let [provider (install-provider!
                      #js {:accounts #js ["0xabc"]}
                      (fn [store]
                        (swap! attach-calls conj store)))
            snapshot (@#'simulators/wallet-simulator-state-snapshot)]
        (is (identical? provider (aget js/globalThis "ethereum")))
        (is (identical? provider (aget existing-window "ethereum")))
        (is (identical? provider (wallet-core/provider)))
        (is (true? (:installed snapshot)))
        (is (= ["0xabc"] (get-in snapshot [:config :accounts])))
        (is (= ["0xabc"] (get-in snapshot [:config :request-accounts])))
        (is (= "0xa4b1" (get-in snapshot [:config :chain-id])))
        (is (= {} (:listenerCounts snapshot)))
        (is (= 1 (count @attach-calls))))
      (simulators/clear-wallet-simulator!)
      (is (identical? existing-provider (aget js/globalThis "ethereum")))
      (is (identical? existing-window (aget js/globalThis "window")))
      (is (identical? existing-provider (aget existing-window "ethereum")))
      (is (identical? existing-provider (wallet-core/provider)))
      (finally
        (cleanup-simulator-state! browser-state)))))

(deftest wallet-simulator-accounts-request-success-test
  (async done
    (let [browser-state (capture-browser-state)]
      (try
        (let [provider (install-provider! #js {:accounts #js ["0xabc" "0xdef"]})]
          (-> (.request provider #js {:method "eth_accounts"})
              (.then (fn [accounts]
                       (try
                         (is (= ["0xabc" "0xdef"] (js->clj accounts)))
                         (finally
                           (cleanup-simulator-state! browser-state)
                           (done)))))
              (.catch (fn [err]
                        (cleanup-simulator-state! browser-state)
                        ((async-support/unexpected-error done) err)))))
        (catch :default err
          (cleanup-simulator-state! browser-state)
          ((async-support/unexpected-error done) err))))))

(deftest wallet-simulator-accounts-request-error-test
  (async done
    (let [browser-state (capture-browser-state)]
      (try
        (let [provider (install-provider! #js {:accountsError "accounts denied"})]
          (-> (.request provider #js {:method "eth_accounts"})
              (.then (fn [_]
                       (cleanup-simulator-state! browser-state)
                       (is false "Expected eth_accounts to reject")
                       (done)))
              (.catch (fn [err]
                        (try
                          (is (= "accounts denied" (.-message err)))
                          (finally
                            (cleanup-simulator-state! browser-state)
                            (done)))))))
        (catch :default err
          (cleanup-simulator-state! browser-state)
          ((async-support/unexpected-error done) err))))))

(deftest wallet-simulator-request-accounts-success-test
  (async done
    (let [browser-state (capture-browser-state)]
      (try
        (let [provider (install-provider! #js {:accounts #js ["0xabc"]})]
          (-> (.request provider #js {:method "eth_requestAccounts"})
              (.then (fn [accounts]
                       (try
                         (is (= ["0xabc"] (js->clj accounts)))
                         (finally
                           (cleanup-simulator-state! browser-state)
                           (done)))))
              (.catch (fn [err]
                        (cleanup-simulator-state! browser-state)
                        ((async-support/unexpected-error done) err)))))
        (catch :default err
          (cleanup-simulator-state! browser-state)
          ((async-support/unexpected-error done) err))))))

(deftest wallet-simulator-request-accounts-error-test
  (async done
    (let [browser-state (capture-browser-state)]
      (try
        (let [provider (install-provider! #js {:requestAccountsError "request denied"})]
          (-> (.request provider #js {:method "eth_requestAccounts"})
              (.then (fn [_]
                       (cleanup-simulator-state! browser-state)
                       (is false "Expected eth_requestAccounts to reject")
                       (done)))
              (.catch (fn [err]
                        (try
                          (is (= "request denied" (.-message err)))
                          (finally
                            (cleanup-simulator-state! browser-state)
                            (done)))))))
        (catch :default err
          (cleanup-simulator-state! browser-state)
          ((async-support/unexpected-error done) err))))))

(deftest wallet-simulator-typed-data-request-success-test
  (async done
    (let [browser-state (capture-browser-state)]
      (try
        (let [provider (install-provider! #js {:typedDataSignature "0xsigned"})]
          (-> (.request provider #js {:method "eth_signTypedData_v4"})
              (.then (fn [signature]
                       (try
                         (is (= "0xsigned" signature))
                         (finally
                           (cleanup-simulator-state! browser-state)
                           (done)))))
              (.catch (fn [err]
                        (cleanup-simulator-state! browser-state)
                        ((async-support/unexpected-error done) err)))))
        (catch :default err
          (cleanup-simulator-state! browser-state)
          ((async-support/unexpected-error done) err))))))

(deftest wallet-simulator-typed-data-request-error-test
  (async done
    (let [browser-state (capture-browser-state)]
      (try
        (let [provider (install-provider! #js {:typedDataError "typed denied"})]
          (-> (.request provider #js {:method "eth_signTypedData"})
              (.then (fn [_]
                       (cleanup-simulator-state! browser-state)
                       (is false "Expected eth_signTypedData to reject")
                       (done)))
              (.catch (fn [err]
                        (try
                          (is (= "typed denied" (.-message err)))
                          (finally
                            (cleanup-simulator-state! browser-state)
                            (done)))))))
        (catch :default err
          (cleanup-simulator-state! browser-state)
          ((async-support/unexpected-error done) err))))))

(deftest wallet-simulator-switch-chain-updates-config-and-emits-event-test
  (async done
    (let [browser-state (capture-browser-state)]
      (try
        (let [provider (install-provider! #js {:chainId "0xa4b1"})
              on-fn (aget provider "on")
              chain-events (atom [])]
          (on-fn "chainChanged"
                 (fn [chain-id]
                   (swap! chain-events conj chain-id)))
          (-> (.request provider #js {:method "wallet_switchEthereumChain"
                                      :params #js [#js {:chainId "0x2105"}]})
              (.then (fn [result]
                       (let [snapshot (@#'simulators/wallet-simulator-state-snapshot)]
                         (try
                           (is (nil? result))
                           (is (= ["0x2105"] @chain-events))
                           (is (= "0x2105" (get-in snapshot [:config :chain-id])))
                           (finally
                             (cleanup-simulator-state! browser-state)
                             (done))))))
              (.catch (fn [err]
                        (cleanup-simulator-state! browser-state)
                        ((async-support/unexpected-error done) err)))))
        (catch :default err
          (cleanup-simulator-state! browser-state)
          ((async-support/unexpected-error done) err))))))

(deftest wallet-simulator-switch-chain-error-test
  (async done
    (let [browser-state (capture-browser-state)]
      (try
        (let [provider (install-provider! #js {:switchChainError "switch denied"})]
          (-> (.request provider #js {:method "wallet_switchEthereumChain"
                                      :params #js [#js {:chainId "0x2105"}]})
              (.then (fn [_]
                       (cleanup-simulator-state! browser-state)
                       (is false "Expected wallet_switchEthereumChain to reject")
                       (done)))
              (.catch (fn [err]
                        (try
                          (is (= "switch denied" (.-message err)))
                          (finally
                            (cleanup-simulator-state! browser-state)
                            (done)))))))
        (catch :default err
          (cleanup-simulator-state! browser-state)
          ((async-support/unexpected-error done) err))))))

(deftest wallet-simulator-listener-registration-removal-and-emit-test
  (let [browser-state (capture-browser-state)
        events (atom [])]
    (try
      (let [provider (install-provider! #js {:accounts #js ["0xabc"]})
            on-fn (aget provider "on")
            remove-fn (aget provider "removeListener")
            handler-a (fn [payload]
                        (swap! events conj [:a (js->clj payload)]))
            handler-b (fn [payload]
                        (swap! events conj [:b (js->clj payload)]))]
        (is (true? (on-fn "accountsChanged" handler-a)))
        (is (true? (on-fn "accountsChanged" handler-b)))
        (is (= {"accountsChanged" 2}
               (:listenerCounts (@#'simulators/wallet-simulator-state-snapshot))))
        (simulators/emit-wallet-simulator! "accountsChanged" ["0xabc"])
        (is (= [[:a ["0xabc"]]
                [:b ["0xabc"]]]
               @events))
        (is (true? (remove-fn "accountsChanged" handler-a)))
        (simulators/emit-wallet-simulator! "accountsChanged" ["0xdef"])
        (is (= [[:a ["0xabc"]]
                [:b ["0xabc"]]
                [:b ["0xdef"]]]
               @events))
        (is (= {"accountsChanged" 1}
               (:listenerCounts (@#'simulators/wallet-simulator-state-snapshot)))))
      (finally
        (cleanup-simulator-state! browser-state)))))

(deftest install-exchange-simulator-normalizes-js-object-config-test
  (let [set-calls (atom [])
        snapshot {:installed true
                  :fills ["ok"]}]
    (with-redefs [trading-api/set-debug-exchange-simulator! (fn [config]
                                                              (swap! set-calls conj config))
                  trading-api/debug-exchange-simulator-snapshot (fn []
                                                                  snapshot)]
      (let [result (js->clj (simulators/install-exchange-simulator! #js {:fills #js ["ok"]})
                            :keywordize-keys true)]
        (is (= [{:fills ["ok"]}] @set-calls))
        (is (= snapshot result))))))

(deftest qa-reset-clears-runtime-traces-simulators-events-flight-recording-and-handler-suppression-test
  (let [browser-state (capture-browser-state)
        calls (atom [])
        handler (fn [_store _address] :handled)
        clear-exchange-calls (atom 0)]
    (try
      (wallet-core/set-on-connected-handler! handler)
      (simulators/set-wallet-connected-handler-mode! "suppress")
      (install-provider! #js {:accounts #js ["0xabc"]})
      (with-redefs [runtime-validation/clear-debug-action-effect-traces! (fn []
                                                                           (swap! calls conj :traces))
                    telemetry/clear-events! (fn []
                                              (swap! calls conj :events))
                    ws-client/clear-flight-recording! (fn []
                                                        (swap! calls conj :flight))
                    trading-api/clear-debug-exchange-simulator! (fn []
                                                                  (swap! clear-exchange-calls inc))]
        (let [result (js->clj (simulators/qa-reset!) :keywordize-keys true)]
          (is (= {:ok true} result))
          (is (= [:traces :events :flight] @calls))
          (is (= 1 @clear-exchange-calls))
          (is (identical? handler (wallet-core/current-on-connected-handler)))
          (is (identical? (:global-provider browser-state)
                          (aget js/globalThis "ethereum")))
          (is (false? (:installed (@#'simulators/wallet-simulator-state-snapshot))))))
      (finally
        (cleanup-simulator-state! browser-state)))))

(deftest account-request-simulator-drives-the-six-staking-loaded-request-kinds-test
  (async done
    (let [address "0x1111111111111111111111111111111111111111"
          service (api-service/make-service
                   {:info-client-instance
                    {:request-info! (fn [& _]
                                      (js/Promise.resolve nil))
                     :get-request-stats (fn [] {})
                     :reset! (fn [] nil)}
                    :log-fn (fn [& _] nil)})
          api* (@#'console-preload/debug-api)
          install! (aget api* "installAccountRequestSimulator")
          clear! (aget api* "clearAccountRequestSimulator")]
      (api/install-api-service! service)
      (-> (if (fn? install!)
            (js/Promise.resolve
             (install! #js {:defaultUser address
                            :validatorSummaries #js [#js {:validator "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                                          :name "Alpha"
                                                          :description "Alpha validator"
                                                          :stake 100
                                                          :isActive true
                                                          :isJailed false
                                                          :commission 0.01
                                                          :stats #js {:week #js {:uptimeFraction 0.99
                                                                                 :predictedApr 0.14
                                                                                 :sampleCount 7}}}]
                            :delegatorSummary #js {:delegated 12
                                                   :undelegated 3
                                                   :totalPendingWithdrawal 1
                                                   :nPendingWithdrawals 2}
                            :delegations #js [#js {:validator "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                                   :amount 12
                                                   :lockedUntilTimestamp 1700000000000}]
                            :delegatorRewards #js [#js {:time 1700000000000
                                                        :source "alpha"
                                                        :totalAmount 1.5}]
                            :delegatorHistory #js [#js {:time 1700000001000
                                                        :hash "0xdeadbeef"
                                                        :delta #js {:cDeposit #js {:amount 2.5}}}]
                            :spotClearinghouseState #js {:balances #js [#js {:coin "HYPE"
                                                                             :total "3.25"}]}}))
            (js/Promise.resolve nil))
          (.then (fn [_]
                   (js/Promise.all
                    #js [(api/request-staking-validator-summaries!)
                         (api/request-staking-delegator-summary! address)
                         (api/request-staking-delegations! address)
                         (api/request-staking-delegator-rewards! address)
                         (api/request-staking-delegator-history! address)
                         (api/request-spot-clearinghouse-state! address)])))
          (.then (fn [results]
                   (let [[validators summary delegations rewards history spot-state]
                         (js->clj results :keywordize-keys true)]
                     (is (= 1 (count validators)))
                     (is (= "Alpha" (get-in validators [0 :name])))
                     (is (= {:delegated 12
                             :undelegated 3
                             :total-pending-withdrawal 1
                             :pending-withdrawals 2}
                            summary))
                     (is (= [{:validator "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                              :amount 12
                              :locked-until-timestamp 1700000000000}]
                            delegations))
                     (is (= [{:time-ms 1700000000000
                              :source :alpha
                              :total-amount 1.5}]
                            rewards))
                     (is (= [{:time-ms 1700000001000
                              :hash "0xdeadbeef"
                              :delta {:kind :deposit
                                      :amount 2.5}}]
                            history))
                     (is (= {:balances [{:coin "HYPE"
                                         :total "3.25"}]}
                            spot-state))
                     (when (fn? clear!)
                       (clear!))
                     (api/reset-api-service!)
                     (done))))
          (.catch (fn [err]
                    (when (fn? clear!)
                      (clear!))
                    (api/reset-api-service!)
                    ((async-support/unexpected-error done) err)))))))

(deftest account-request-simulator-queue-rejections-and-qa-reset-clear-the-loaded-state-seam-test
  (async done
    (let [address "0x1111111111111111111111111111111111111111"
          live-history [{:time 1700000099999
                         :hash "0xlive"
                         :delta {:withdrawal {:amount 9
                                              :phase "pending"}}}]
          service (api-service/make-service
                   {:info-client-instance
                    {:request-info! (fn [body _opts]
                                      (case (get body "type")
                                        "delegatorHistory" (js/Promise.resolve live-history)
                                        "delegatorRewards" (js/Promise.resolve [])
                                        (js/Promise.resolve nil)))
                     :get-request-stats (fn [] {})
                     :reset! (fn [] nil)}
                    :log-fn (fn [& _] nil)})
          api* (@#'console-preload/debug-api)
          install! (aget api* "installAccountRequestSimulator")]
      (api/install-api-service! service)
      (-> (if (fn? install!)
            (js/Promise.resolve
             (install! #js {:defaultUser address
                            :delegatorHistory #js {:responses #js [#js [#js {:time 1700000000000
                                                                             :hash "0xfirst"
                                                                             :delta #js {:cDeposit #js {:amount 1}}}]
                                                                    #js [#js {:time 1700000005000
                                                                             :hash "0xsecond"
                                                                             :delta #js {:cWithdraw #js {:amount 2}}}]]}
                            :delegatorRewards #js {:rejectMessage "simulated rewards failure"}}))
            (js/Promise.resolve nil))
          (.then (fn [_]
                   (api/request-staking-delegator-history! address)))
          (.then (fn [first-history]
                   (is (= [{:time-ms 1700000000000
                            :hash "0xfirst"
                            :delta {:kind :deposit
                                    :amount 1}}]
                          first-history))
                   (api/request-staking-delegator-history! address)))
          (.then (fn [second-history]
                   (is (= [{:time-ms 1700000005000
                            :hash "0xsecond"
                            :delta {:kind :withdraw
                                    :amount 2}}]
                          second-history))
                   (-> (api/request-staking-delegator-rewards! address)
                       (.then (fn [_]
                                (is false "Expected delegator rewards to reject from simulated failure")
                                nil))
                       (.catch (fn [err]
                                 (is (= "simulated rewards failure" (.-message err))))))))
          (.then (fn [_]
                   (simulators/qa-reset!)
                   (api/request-staking-delegator-history! address)))
          (.then (fn [history-after-reset]
                   (is (= [{:time-ms 1700000099999
                            :hash "0xlive"
                            :delta {:kind :withdrawal
                                    :amount 9
                                    :phase :pending}}]
                          history-after-reset))
                   (api/reset-api-service!)
                   (done)))
          (.catch (fn [err]
                    (api/reset-api-service!)
                    ((async-support/unexpected-error done) err)))))))
