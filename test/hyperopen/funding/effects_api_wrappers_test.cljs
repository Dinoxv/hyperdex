(ns hyperopen.funding.effects-api-wrappers-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.application.deposit-submit :as deposit-submit]
            [hyperopen.funding.application.hyperunit-query :as hyperunit-query]
            [hyperopen.funding.application.hyperunit-submit :as hyperunit-submit]
            [hyperopen.funding.application.submit-effects :as submit-effects]
            [hyperopen.funding.effects :as effects]
            [hyperopen.funding.infrastructure.hyperunit-address-client :as hyperunit-address-client]
            [hyperopen.funding.test-support.effects :as effects-support]
            [hyperopen.wallet.core :as wallet]))

(deftest funding-effect-submit-request-wrappers-compose_expected_dependency_maps_test
  (let [hyperunit-request-error-message @#'hyperopen.funding.effects/hyperunit-request-error-message
        submit-hyperunit-address-deposit-request!
        @#'hyperopen.funding.effects/submit-hyperunit-address-deposit-request!
        submit-hyperunit-send-asset-withdraw-request!
        @#'hyperopen.funding.effects/submit-hyperunit-send-asset-withdraw-request!
        submit-usdc-bridge2-deposit-tx! @#'hyperopen.funding.effects/submit-usdc-bridge2-deposit-tx!
        submit-usdh-across-deposit-tx! @#'hyperopen.funding.effects/submit-usdh-across-deposit-tx!
        submit-usdt-lifi-bridge2-deposit-tx!
        @#'hyperopen.funding.effects/submit-usdt-lifi-bridge2-deposit-tx!
        store (atom {:wallet {:chain-id "0xa4b1"}})
        action {:type "bridge2Deposit"
                :asset "usdc"
                :amount "5"}
        seen (atom {})]
    (with-redefs [hyperunit-address-client/hyperunit-request-error-message
                  (fn [err ctx]
                    (swap! seen assoc :error-message [err ctx])
                    "request error")
                  hyperunit-submit/submit-hyperunit-address-deposit-request!
                  (fn [deps store* owner-address action*]
                    (swap! seen assoc
                           :address-submit
                           {:deps deps
                            :store store*
                            :owner-address owner-address
                            :action action*})
                    :address-submit-result)
                  hyperunit-submit/submit-hyperunit-send-asset-withdraw-request!
                  (fn [deps store* owner-address action* submit-send-asset!]
                    (swap! seen assoc
                           :withdraw-submit
                           {:deps deps
                            :store store*
                            :owner-address owner-address
                            :action action*
                            :submit-send-asset! submit-send-asset!})
                    :withdraw-submit-result)
                  deposit-submit/submit-usdc-bridge2-deposit-tx!
                  (fn [deps store* owner-address action*]
                    (swap! seen assoc
                           :usdc-deposit
                           {:deps deps
                            :store store*
                            :owner-address owner-address
                            :action action*})
                    :usdc-deposit-result)
                  deposit-submit/submit-usdh-across-deposit-tx!
                  (fn [deps store* owner-address action*]
                    (swap! seen assoc
                           :usdh-deposit
                           {:deps deps
                            :store store*
                            :owner-address owner-address
                            :action action*})
                    :usdh-deposit-result)
                  deposit-submit/submit-usdt-lifi-bridge2-deposit-tx!
                  (fn [deps store* owner-address action*]
                    (swap! seen assoc
                           :usdt-deposit
                           {:deps deps
                            :store store*
                            :owner-address owner-address
                            :action action*})
                    :usdt-deposit-result)]
      (is (= "request error"
             (hyperunit-request-error-message :boom
                                              {:asset "btc"
                                               :source-chain "bitcoin"})))
      (is (= [:boom {:asset "btc"
                     :source-chain "bitcoin"}]
             (:error-message @seen)))

      (is (= :address-submit-result
             (submit-hyperunit-address-deposit-request! store "0xowner" action)))
      (is (identical? store
                      (get-in @seen [:address-submit :store])))
      (is (fn? (get-in @seen [:address-submit :deps :fetch-hyperunit-address-with-source-fallbacks!])))
      (is (fn? (get-in @seen [:address-submit :deps :request-existing-hyperunit-deposit-address!])))

      (is (= :withdraw-submit-result
             (submit-hyperunit-send-asset-withdraw-request! store
                                                            "0xowner"
                                                            {:type "sendAsset"}
                                                            :submit-send-asset)))
      (is (= :submit-send-asset
             (get-in @seen [:withdraw-submit :submit-send-asset!])))
      (is (fn? (get-in @seen [:withdraw-submit :deps :hyperunit-request-error-message])))
      (is (fn? (get-in @seen [:withdraw-submit :deps :fallback-exchange-response-error])))

      (is (= :usdc-deposit-result
             (submit-usdc-bridge2-deposit-tx! store "0xowner" action)))
      (is (identical? wallet/provider
                      (get-in @seen [:usdc-deposit :deps :wallet-provider-fn])))
      (is (fn? (get-in @seen [:usdc-deposit :deps :resolve-deposit-chain-config])))
      (is (fn? (get-in @seen [:usdc-deposit :deps :wallet-error-message])))

      (is (= :usdh-deposit-result
             (submit-usdh-across-deposit-tx! store "0xowner" {:type "acrossUsdcToUsdhDeposit"})))
      (is (= "Arbitrum"
             (get-in @seen [:usdh-deposit :deps :chain-config :network-label])))
      (is (= "100000000000000"
             (.toString (get-in @seen [:usdh-deposit :deps :usdh-route-max-units]))))
      (is (identical? wallet/provider
                      (get-in @seen [:usdh-deposit :deps :wallet-provider-fn])))

      (is (= :usdt-deposit-result
             (submit-usdt-lifi-bridge2-deposit-tx! store "0xowner" {:type "lifiUsdtToUsdcBridge2Deposit"})))
      (is (= "0xa4b1"
             (get-in @seen [:usdt-deposit :deps :bridge-chain-id])))
      (is (= "Arbitrum"
             (get-in @seen [:usdt-deposit :deps :chain-config :network-label])))
      (is (fn? (get-in @seen [:usdt-deposit :deps :submit-usdc-bridge2-deposit!])))
      (is (fn? (get-in @seen [:usdt-deposit :deps :usdc-units->amount-text]))))))

(deftest funding-effect-public-api-wrappers_forward_defaults_and_overrides_test
  (let [set-funding-submit-error! @#'hyperopen.funding.effects/set-funding-submit-error!
        close-funding-modal! @#'hyperopen.funding.effects/close-funding-modal!
        refresh-after-funding-submit! @#'hyperopen.funding.effects/refresh-after-funding-submit!
        submit-hyperunit-address-deposit-request!
        @#'hyperopen.funding.effects/submit-hyperunit-address-deposit-request!
        start-hyperunit-deposit-lifecycle-polling!
        @#'hyperopen.funding.effects/start-hyperunit-deposit-lifecycle-polling!
        start-hyperunit-withdraw-lifecycle-polling!
        @#'hyperopen.funding.effects/start-hyperunit-withdraw-lifecycle-polling!
        custom-estimate! (fn [& _] :estimate)
        custom-queue! (fn [& _] :queue)
        custom-transfer! (fn [& _] :transfer)
        custom-send! (fn [& _] :send)
        custom-withdraw! (fn [& _] :withdraw)
        custom-address-request! (fn [& _] :address-request)
        custom-usdc-deposit! (fn [& _] :usdc-deposit)
        custom-usdt-deposit! (fn [& _] :usdt-deposit)
        custom-usdh-deposit! (fn [& _] :usdh-deposit)
        custom-show-toast! (fn [& _] :toast)
        custom-dispatch! (fn [& _] :dispatch)
        custom-now-ms-fn (fn [] 4242)
        custom-runtime-error-message (fn [err] (str "runtime:" err))
        store (atom {:wallet {:chain-id "421614"}})
        seen (atom {})]
    (with-redefs [hyperunit-query/api-fetch-hyperunit-fee-estimate!
                  (fn [deps opts]
                    (swap! seen assoc :fee-estimate {:deps deps
                                                     :opts opts})
                    :fee-estimate-result)
                  hyperunit-query/fetch-hyperunit-withdrawal-queue!
                  (fn [deps opts]
                    (swap! seen assoc :withdrawal-queue {:deps deps
                                                         :opts opts})
                    :withdrawal-queue-result)
                  submit-effects/api-submit-funding-transfer!
                  (fn [deps]
                    (swap! seen assoc :transfer deps)
                    :transfer-result)
                  submit-effects/api-submit-funding-send!
                  (fn [deps]
                    (swap! seen assoc :send deps)
                    :send-result)
                  submit-effects/api-submit-funding-withdraw!
                  (fn [deps]
                    (swap! seen assoc :withdraw deps)
                    :withdraw-result)
                  submit-effects/api-submit-funding-deposit!
                  (fn [deps]
                    (swap! seen assoc :deposit deps)
                    :deposit-result)]
      (is (= :fee-estimate-result
             (effects/api-fetch-hyperunit-fee-estimate! {:store store
                                                         :request-hyperunit-estimate-fees! custom-estimate!
                                                         :now-ms-fn custom-now-ms-fn
                                                         :runtime-error-message custom-runtime-error-message})))
      (is (identical? custom-estimate!
                      (get-in @seen [:fee-estimate :opts :request-hyperunit-estimate-fees!])))
      (is (identical? custom-now-ms-fn
                      (get-in @seen [:fee-estimate :opts :now-ms-fn])))
      (is (identical? custom-runtime-error-message
                      (get-in @seen [:fee-estimate :opts :runtime-error-message])))
      (is (fn? (get-in @seen [:fee-estimate :deps :prefetch-selected-hyperunit-deposit-address!])))

      (is (= :withdrawal-queue-result
             (effects/api-fetch-hyperunit-withdrawal-queue! {:store store
                                                             :request-hyperunit-withdrawal-queue! custom-queue!
                                                             :now-ms-fn custom-now-ms-fn
                                                             :runtime-error-message custom-runtime-error-message})))
      (is (= "https://api.hyperunit-testnet.xyz"
             (get-in @seen [:withdrawal-queue :opts :base-url])))
      (is (= ["https://api.hyperunit-testnet.xyz"]
             (get-in @seen [:withdrawal-queue :opts :base-urls])))
      (is (identical? custom-queue!
                      (get-in @seen [:withdrawal-queue :opts :request-hyperunit-withdrawal-queue!])))

      (is (= :transfer-result
             (effects/api-submit-funding-transfer! {:store store
                                                    :request {:action {:type "usdClassTransfer"}}
                                                    :dispatch! custom-dispatch!
                                                    :submit-usd-class-transfer! custom-transfer!
                                                    :runtime-error-message custom-runtime-error-message
                                                    :show-toast! custom-show-toast!
                                                    :default-funding-modal-state effects-support/default-funding-modal-state})))
      (is (identical? custom-transfer!
                      (:submit-usd-class-transfer! (:transfer @seen))))
      (is (identical? set-funding-submit-error!
                      (:set-funding-submit-error! (:transfer @seen))))
      (is (identical? close-funding-modal!
                      (:close-funding-modal! (:transfer @seen))))

      (is (= :send-result
             (effects/api-submit-funding-send! {:store store
                                                :request {:action {:type "sendAsset"}}
                                                :dispatch! custom-dispatch!
                                                :submit-send-asset! custom-send!
                                                :show-toast! custom-show-toast!
                                                :default-funding-modal-state effects-support/default-funding-modal-state})))
      (is (identical? custom-send!
                      (:submit-send-asset! (:send @seen))))
      (is (identical? refresh-after-funding-submit!
                      (:refresh-after-funding-submit! (:send @seen))))

      (is (= :withdraw-result
             (effects/api-submit-funding-withdraw! {:store store
                                                    :request {:action {:type "withdraw3"}}
                                                    :dispatch! custom-dispatch!
                                                    :submit-withdraw3! custom-withdraw!
                                                    :submit-send-asset! custom-send!
                                                    :request-hyperunit-operations! :ops
                                                    :request-hyperunit-withdrawal-queue! :queue
                                                    :set-timeout-fn :timeout
                                                    :now-ms-fn custom-now-ms-fn
                                                    :runtime-error-message custom-runtime-error-message
                                                    :show-toast! custom-show-toast!
                                                    :default-funding-modal-state effects-support/default-funding-modal-state})))
      (is (= :ops
             (:request-hyperunit-operations! (:withdraw @seen))))
      (is (= :queue
             (:request-hyperunit-withdrawal-queue! (:withdraw @seen))))
      (is (identical? start-hyperunit-withdraw-lifecycle-polling!
                      (:start-hyperunit-withdraw-lifecycle-polling! (:withdraw @seen))))

      (is (= :deposit-result
             (effects/api-submit-funding-deposit! {:store store
                                                   :request {:action {:type "bridge2Deposit"}}
                                                   :dispatch! custom-dispatch!
                                                   :submit-usdc-bridge2-deposit! custom-usdc-deposit!
                                                   :submit-usdt-lifi-deposit! custom-usdt-deposit!
                                                   :submit-usdh-across-deposit! custom-usdh-deposit!
                                                   :submit-hyperunit-address-request! custom-address-request!
                                                   :request-hyperunit-operations! :ops
                                                   :set-timeout-fn :timeout
                                                   :now-ms-fn custom-now-ms-fn
                                                   :runtime-error-message custom-runtime-error-message
                                                   :show-toast! custom-show-toast!
                                                   :default-funding-modal-state effects-support/default-funding-modal-state})))
      (is (identical? custom-address-request!
                      (:submit-hyperunit-address-request! (:deposit @seen))))
      (is (identical? custom-usdc-deposit!
                      (:submit-usdc-bridge2-deposit! (:deposit @seen))))
      (is (identical? custom-usdt-deposit!
                      (:submit-usdt-lifi-deposit! (:deposit @seen))))
      (is (identical? custom-usdh-deposit!
                      (:submit-usdh-across-deposit! (:deposit @seen))))
      (is (identical? start-hyperunit-deposit-lifecycle-polling!
                      (:start-hyperunit-deposit-lifecycle-polling! (:deposit @seen))))
      (is (not (identical? submit-hyperunit-address-deposit-request!
                           (:submit-hyperunit-address-request! (:deposit @seen))))))))
