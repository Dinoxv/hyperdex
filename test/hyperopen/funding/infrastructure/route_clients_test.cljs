(ns hyperopen.funding.infrastructure.route-clients-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.infrastructure.route-clients :as route-clients]))

(def ^:private from-address
  "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")

(def ^:private to-address
  "0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")

(def ^:private token-a
  "0xToken/Source")

(def ^:private token-b
  "0xToken+Target")

(deftest across-approval->swap-config-normalizes-transactions-and-filters-invalid-steps-test
  (is (= {:swap-tx {:to "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                    :data "0xdeadbeef"}
          :approval-txs [{:to "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                          :data "0xcafe"
                          :value "0xf"}
                         {:to "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                          :data "0xbeef"}]}
         (route-clients/across-approval->swap-config
          {:swapTx {:to from-address
                    :data " 0xDEADBEEF "
                    :value "0"}
           :approvalTxns [{:to to-address
                           :data "0xCAFE"
                           :value "15"}
                          {:to to-address
                           :data "0xBEEF"
                           :value "0x0"}
                          {:to "invalid"
                           :data "0x1"
                           :value "1"}
                          {:to to-address
                           :data "not-hex"
                           :value "3"}]}))))

(deftest lifi-quote->swap-config-requires-complete-valid-shapes-test
  (let [config (route-clients/lifi-quote->swap-config
                {:action {:fromToken {:address from-address}}
                 :estimate {:approvalAddress to-address
                            :fromAmount "42"}
                 :transactionRequest {:to from-address
                                      :data " 0xabc123 "
                                      :value "0x12"}})]
    (is (= "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
           (:approval-address config)))
    (is (= "42"
           (.toString (:from-amount-units config) 10)))
    (is (= "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
           (:swap-token-address config)))
    (is (= "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
           (:swap-to-address config)))
    (is (= "0xabc123" (:swap-data config)))
    (is (= "0x12" (:swap-value config))))
  (is (nil? (route-clients/lifi-quote->swap-config
             {:action {:fromToken {:address "invalid"}}
              :estimate {:approvalAddress to-address
                         :fromAmount "abc"}
              :transactionRequest {:to from-address
                                   :data " "
                                   :value "0"}}))))

(deftest lifi-quote-url-builds-encoded-query-and-defaults-integrator-test
  (is (= (str "https://li.quest/v1/quote?"
              "fromChain=1"
              "&toChain=42161"
              "&fromToken=0xToken%2FSource"
              "&toToken=0xToken%2BTarget"
              "&fromAddress=0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
              "&fromAmount=42"
              "&slippage=0.005"
              "&integrator=hyperopen")
         (@#'hyperopen.funding.infrastructure.route-clients/lifi-quote-url
          {:from-address from-address
           :amount-units (js/BigInt "42")
           :to-token-address token-b
           :from-chain-id 1
           :to-chain-id 42161
           :from-token-address token-a
           :integrator nil}))))

(deftest across-approval-url-builds-encoded-query-test
  (is (= (str "https://across.example/approval"
              "?tradeType=minOutput"
              "&amount=9000"
              "&inputToken=0xToken%2FSource"
              "&originChainId=1"
              "&outputToken=0xToken%2BTarget"
              "&destinationChainId=42161"
              "&depositor=0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
         (@#'hyperopen.funding.infrastructure.route-clients/across-approval-url
          {:base-url "https://across.example/approval"
           :from-address from-address
           :amount-units (js/BigInt "9000")
           :input-token-address token-a
           :origin-chain-id 1
           :output-token-address token-b
           :destination-chain-id 42161}))))
