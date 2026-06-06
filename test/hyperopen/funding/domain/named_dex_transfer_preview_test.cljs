(ns hyperopen.funding.domain.named-dex-transfer-preview-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.domain.availability :as availability]
            [hyperopen.funding.domain.policy :as policy]))

(defn- named-dex-state
  []
  {:wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}
   :spot {:meta {:tokens [{:index 0
                           :name "USDC"
                           :tokenId "0x6d1e7cde53ba9467b783cb7c530ce054"}]}
          :clearinghouse-state {:balances [{:coin "USDC"
                                            :available "78.22"
                                            :total "78.22"
                                            :hold "0"}]}}
   :webdata2 {:clearinghouseState {:withdrawable "0.0"
                                   :marginSummary {:accountValue "0.0"
                                                   :totalMarginUsed "0.0"}}}
   :perp-dex-clearinghouse {"xyz" {:withdrawable "1923.97"
                                   :marginSummary {:accountValue "1923.97"
                                                   :totalMarginUsed "0.0"}}}})

(deftest transfer-max-amount-reads-named-dex-withdrawable-for-perps-to-spot-test
  (let [state (named-dex-state)]
    ;; Default perps -> spot still reads the (zero) default clearinghouse state.
    (is (= 0 (availability/transfer-max-amount state {:to-perp? false})))
    ;; Named DEX perps -> spot reads the xyz withdrawable, not the default state.
    (is (= 1923.97 (availability/transfer-max-amount state {:to-perp? false
                                                            :transfer-dex "xyz"})))
    ;; Spot -> perps reads spot USDC regardless of dex.
    (is (= 78.22 (availability/transfer-max-amount state {:to-perp? true
                                                          :transfer-dex "xyz"})))))

(deftest transfer-dex-name-normalizes-default-and-spot-aliases-test
  (is (nil? (availability/transfer-dex-name nil)))
  (is (nil? (availability/transfer-dex-name "")))
  (is (nil? (availability/transfer-dex-name "  ")))
  (is (nil? (availability/transfer-dex-name "spot")))
  (is (nil? (availability/transfer-dex-name "SPOT")))
  (is (= "xyz" (availability/transfer-dex-name "xyz")))
  (is (= "xyz" (availability/transfer-dex-name "  xyz "))))

(deftest transfer-preview-builds-named-dex-send-asset-for-perps-to-spot-test
  (is (= {:ok? true
          :request {:action {:type "sendAsset"
                             :destination "0x1234567890abcdef1234567890abcdef12345678"
                             :sourceDex "xyz"
                             :destinationDex "spot"
                             :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                             :amount "100"
                             :fromSubAccount ""}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "100"
                                   :to-perp? false
                                   :transfer-dex "xyz"}))))

(deftest transfer-preview-builds-named-dex-send-asset-for-spot-to-perps-test
  (is (= {:ok? true
          :request {:action {:type "sendAsset"
                             :destination "0x1234567890abcdef1234567890abcdef12345678"
                             :sourceDex "spot"
                             :destinationDex "xyz"
                             :token "USDC:0x6d1e7cde53ba9467b783cb7c530ce054"
                             :amount "50"
                             :fromSubAccount ""}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "50"
                                   :to-perp? true
                                   :transfer-dex "xyz"}))))

(deftest transfer-preview-keeps-usd-class-transfer-for-default-perps-test
  (is (= {:ok? true
          :request {:action {:type "usdClassTransfer"
                             :amount "25"
                             :toPerp true}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "25"
                                   :to-perp? true})))
  (is (= {:ok? true
          :request {:action {:type "usdClassTransfer"
                             :amount "25"
                             :toPerp true}}}
         (policy/transfer-preview (named-dex-state)
                                  {:amount-input "25"
                                   :to-perp? true
                                   :transfer-dex ""}))))

(deftest transfer-preview-requires-wallet-address-for-named-dex-send-asset-test
  (is (= {:ok? false
          :display-message "Connect your wallet before transferring funds."}
         (policy/transfer-preview (assoc (named-dex-state) :wallet {})
                                  {:amount-input "100"
                                   :to-perp? false
                                   :transfer-dex "xyz"}))))
