(ns hyperopen.account.history.position-margin-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.history.position-margin :as position-margin]
            [hyperopen.views.account-info.test-support.fixtures :as fixtures]))

(deftest from-position-row-falls-back-to-default-clearinghouse-when-dex-state-lacks-summary-test
  (let [row (fixtures/sample-position-row "xyz:GOLD" 20 "0.0185" "xyz")
        state {:webdata2 {:clearinghouseState {:marginSummary {:accountValue "200"
                                                                :totalMarginUsed "4.5"}}}
               :perp-dex-clearinghouse {"xyz" {:assetPositions []}}}
        modal (position-margin/from-position-row state row)]
    (is (= 195.5 (:available-to-add modal)))))

(deftest from-position-row-uses-unified-spot-usdc-availability-when-present-test
  (let [row (fixtures/sample-position-row "GOLD" 20 "0.0185")
        state {:account {:mode :unified}
               :spot {:clearinghouse-state {:balances [{:coin "USDC"
                                                        :available "200.00"}]}}
               :webdata2 {:clearinghouseState {:marginSummary {:accountValue "4.62"
                                                                :totalMarginUsed "4.62"}}}}
        modal (position-margin/from-position-row state row)]
    (is (= 200 (:available-to-add modal)))))

(deftest from-position-row-reads-available-to-withdraw-compat-field-test
  (let [row (fixtures/sample-position-row "GOLD" 20 "0.0185")
        state {:webdata2 {:clearinghouseState {:availableToWithdraw "123.45"}}}
        modal (position-margin/from-position-row state row)]
    (is (= 123.45 (:available-to-add modal)))))
