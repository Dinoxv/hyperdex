(ns hyperopen.funding.domain.policy-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.domain.assets :as assets-domain]
            [hyperopen.funding.domain.policy :as policy]))

(defn- base-state
  []
  {:spot {:clearinghouse-state {:balances [{:coin "USDC" :available "12.5" :total "12.5" :hold "0"}
                                           {:coin "BTC" :available "1.25" :total "1.25" :hold "0"}]}}
   :webdata2 {:clearinghouseState {:availableToWithdraw "8.5"
                                   :marginSummary {:accountValue "20"
                                                   :totalMarginUsed "11.5"}}}})

(deftest direct-balance-row-available-prefers-supported-direct-fields-test
  (let [direct-balance-row-available @#'hyperopen.funding.domain.policy/direct-balance-row-available]
    (is (= 10.5
           (direct-balance-row-available {:available "10.5"
                                          :availableBalance "9"
                                          :free "8"})))
    (is (= 8
           (direct-balance-row-available {:availableBalance "8"
                                          :free "7"})))
    (is (= 7
           (direct-balance-row-available {:free "7"})))
    (is (nil? (direct-balance-row-available {:available "NaN"
                                             :availableBalance nil
                                             :free ""})))))

(deftest derived-balance-row-available-uses-total-minus-hold-when-needed-test
  (let [derived-balance-row-available @#'hyperopen.funding.domain.policy/derived-balance-row-available]
    (is (= 6
           (derived-balance-row-available {:total "10"
                                           :hold "4"})))
    (is (= 12
           (derived-balance-row-available {:totalBalance "12"})))
    (is (= -2
           (derived-balance-row-available {:total "5"
                                           :hold "7"})))
    (is (nil? (derived-balance-row-available {:hold "2"})))))

(deftest balance-row-available-wraps-direct-and-derived-values-test
  (let [balance-row-available @#'hyperopen.funding.domain.policy/balance-row-available]
    (is (= 10.5
           (balance-row-available {:available "10.5"
                                   :availableBalance "9"
                                   :free "8"
                                   :total "100"
                                   :hold "50"})))
    (is (= 0
           (balance-row-available {:total "5"
                                   :hold "7"})))
    (is (nil? (balance-row-available {:available "NaN"})))
    (is (nil? (balance-row-available nil)))))

(deftest withdraw-preview-validates-standard-destination-and-balance-test
  (is (= {:ok? false
          :display-message "Enter a valid destination address."}
         (policy/withdraw-preview (base-state)
                                  {:withdraw-selected-asset-key :usdc
                                   :destination-input "abc"
                                   :amount-input "6.5"})))
  (is (= {:ok? false
          :display-message "Amount exceeds withdrawable balance."}
         (policy/withdraw-preview (base-state)
                                  {:withdraw-selected-asset-key :usdc
                                   :destination-input "0x1234567890abcdef1234567890abcdef12345678"
                                   :amount-input "9"}))))

(deftest withdraw-preview-builds-standard-withdraw-request-test
  (is (= {:ok? true
          :request {:action {:type "withdraw3"
                             :amount "6.5"
                             :destination "0x1234567890abcdef1234567890abcdef12345678"}}}
         (policy/withdraw-preview (base-state)
                                  {:withdraw-selected-asset-key :usdc
                                   :destination-input "0x1234567890abcdef1234567890abcdef12345678"
                                   :amount-input "6.5"}))))

(deftest withdraw-preview-requires-hyperunit-source-chain-and-preserves-request-shape-test
  (with-redefs [assets-domain/withdraw-assets (fn [_state]
                                                [{:key :btc
                                                  :symbol "BTC"
                                                  :network "Bitcoin"
                                                  :flow-kind :hyperunit-address}])]
    (is (= {:ok? false
            :display-message "Withdrawal source chain is unavailable for BTC."}
           (policy/withdraw-preview (base-state)
                                    {:withdraw-selected-asset-key :btc
                                     :destination-input "bc1qmissingchain"
                                     :amount-input "0.25"}))))
  (is (= {:ok? true
          :request {:action {:type "hyperunitSendAssetWithdraw"
                             :asset "btc"
                             :token "BTC"
                             :amount "0.25"
                             :destination "bc1qexamplexyz0p4y0p4y0p4y0p4y0p4y0p4y0p"
                             :destinationChain "bitcoin"
                             :network "Bitcoin"}}}
         (policy/withdraw-preview (base-state)
                                  {:withdraw-selected-asset-key :btc
                                   :destination-input "bc1qexamplexyz0p4y0p4y0p4y0p4y0p4y0p4y0p"
                                   :amount-input "0.25"}))))

(deftest withdraw-preview-reports-no-withdrawable-balance-test
  (is (= {:ok? false
          :display-message "No withdrawable balance available."}
         (policy/withdraw-preview {:spot {:clearinghouse-state {:balances []}}
                                   :webdata2 {:clearinghouseState {:availableToWithdraw "0"}}}
                                  {:withdraw-selected-asset-key :usdc
                                   :destination-input "0x1234567890abcdef1234567890abcdef12345678"
                                   :amount-input "1"}))))
