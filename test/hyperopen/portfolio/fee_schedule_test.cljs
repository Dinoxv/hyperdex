(ns hyperopen.portfolio.fee-schedule-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.fee-schedule :as fee-schedule]))

(deftest normalize-market-type-accepts-keywords-strings-and-labels-test
  (is (= :perps (fee-schedule/normalize-market-type nil)))
  (is (= :perps (fee-schedule/normalize-market-type :unknown)))
  (is (= :perps (fee-schedule/normalize-market-type "Perps")))
  (is (= :spot (fee-schedule/normalize-market-type "spot")))
  (is (= :spot-stable-pair
         (fee-schedule/normalize-market-type "Spot + Stable Pair")))
  (is (= :spot-aligned-quote
         (fee-schedule/normalize-market-type "spotAlignedQuote")))
  (is (= :spot-aligned-stable-pair
         (fee-schedule/normalize-market-type :spot-aligned-stable-pair)))
  (is (= :spot-aligned-stable-pair
         (fee-schedule/normalize-market-type "Spot + Aligned Quote + Stable Pair"))))

(deftest fee-schedule-rows-render-protocol-rate-variants-test
  (testing "perps base schedule"
    (let [rows (fee-schedule/fee-schedule-rows :perps)]
      (is (= {:tier "0"
              :volume "<= $5M"
              :taker "0.045%"
              :maker "0.015%"}
             (first rows)))
      (is (= {:tier "6"
              :volume "> $7B"
              :taker "0.024%"
              :maker "0%"}
             (last rows)))))
  (testing "spot base schedule"
    (let [rows (fee-schedule/fee-schedule-rows :spot)]
      (is (= {:tier "0"
              :volume "<= $5M"
              :taker "0.070%"
              :maker "0.040%"}
             (first rows)))))
  (testing "stable pair and aligned quote multipliers"
    (is (= {:tier "0"
            :volume "<= $5M"
            :taker "0.014%"
            :maker "0.008%"}
           (first (fee-schedule/fee-schedule-rows :spot-stable-pair))))
    (is (= {:tier "0"
            :volume "<= $5M"
            :taker "0.056%"
            :maker "0.040%"}
           (first (fee-schedule/fee-schedule-rows :spot-aligned-quote))))
    (is (= {:tier "0"
            :volume "<= $5M"
            :taker "0.0112%"
            :maker "0.008%"}
           (first (fee-schedule/fee-schedule-rows :spot-aligned-stable-pair))))
    (is (= {:tier "6"
            :volume "> $7B"
            :taker "0.004%"
            :maker "0%"}
           (last (fee-schedule/fee-schedule-rows :spot-aligned-stable-pair))))))

(deftest fee-schedule-rows-apply-selected-discount-scenarios-test
  (testing "referral and staking reduce positive perps fees while maker rebate overrides maker fee"
    (let [rows (fee-schedule/fee-schedule-rows
                :perps
                {:referral-discount :referral-4
                 :staking-tier :diamond
                 :maker-rebate-tier :tier-2})]
      (is (= {:tier "0"
              :volume "<= $5M"
              :taker "0.0259%"
              :maker "-0.002%"}
             (first rows)))
      (is (= "-0.002%" (:maker (last rows))))))
  (testing "spot stable-pair and aligned-quote scaling applies after selected discounts"
    (let [rows (fee-schedule/fee-schedule-rows
                :spot-aligned-stable-pair
                {:referral-discount :referral-4
                 :staking-tier :wood})]
      (is (= {:tier "0"
              :volume "<= $5M"
              :taker "0.0102%"
              :maker "0.0073%"}
             (first rows)))))
  (testing "aligned quote improves selected negative maker rebate"
    (let [rows (fee-schedule/fee-schedule-rows
                :spot-aligned-stable-pair
                {:maker-rebate-tier :tier-1})]
      (is (= "-0.0003%" (:maker (first rows)))))))

(deftest fee-schedule-model-describes-disconnected-and-connected-status-test
  (let [disconnected (fee-schedule/fee-schedule-model
                      {:portfolio-ui {:fee-schedule-open? true
                                      :fee-schedule-market-type "spotAlignedStablePair"
                                      :fee-schedule-market-dropdown-open? true}
                       :portfolio {:user-fees nil}})
        connected (fee-schedule/fee-schedule-model
                   {:wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}
                    :portfolio-ui {:fee-schedule-open? true
                                   :fee-schedule-market-type :perps
                                   :fee-schedule-referral-dropdown-open? true
                                   :fee-schedule-staking-dropdown-open? true
                                   :fee-schedule-maker-rebate-dropdown-open? true}
                    :portfolio {:user-fees {:activeReferralDiscount 0.04
                                            :activeStakingDiscount {:discount 0.1
                                                                    :tier "Bronze"}
                                            :userAddRate -0.00002}}})
        overridden (fee-schedule/fee-schedule-model
                    {:wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}
                     :portfolio-ui {:fee-schedule-open? true
                                    :fee-schedule-market-type :perps
                                    :fee-schedule-referral-discount :none
                                    :fee-schedule-staking-tier :diamond
                                    :fee-schedule-maker-rebate-tier :tier-3}
                     :portfolio {:user-fees {:activeReferralDiscount 0.04
                                             :activeStakingDiscount {:discount 0.05}
                                             :userAddRate -0.00001}}})]
    (testing "disconnected wallet"
      (is (true? (:open? disconnected)))
      (is (= :spot-aligned-stable-pair (:selected-market-type disconnected)))
      (is (= "Spot + Aligned Quote + Stable Pair"
             (:selected-market-label disconnected)))
      (is (true? (:market-dropdown-open? disconnected)))
      (is (= "No referral discount" (get-in disconnected [:referral :value])))
      (is (= "Wallet not connected" (get-in disconnected [:referral :helper])))
      (is (= :none (get-in disconnected [:referral :selected-value])))
      (is (= true (->> (get-in disconnected [:referral :options])
                       (some #(when (= :none (:value %))
                                (:current? %))))))
      (is (= "No stake" (get-in disconnected [:staking :value])))
      (is (= "Wallet not connected" (get-in disconnected [:staking :helper])))
      (is (= :none (get-in disconnected [:staking :selected-value])))
      (is (= "No rebate" (get-in disconnected [:maker-rebate :value])))
      (is (= :none (get-in disconnected [:maker-rebate :selected-value])))
      (is (= "* Rates reflect selected referral, staking, and maker rebate scenarios; HIP-3 deployer adjustments not included"
             (:rate-note disconnected))))
    (testing "connected wallet discounts"
      (is (= "4%" (get-in connected [:referral :value])))
      (is (= "Active referral discount" (get-in connected [:referral :helper])))
      (is (= :referral-4 (get-in connected [:referral :selected-value])))
      (is (true? (get-in connected [:referral :dropdown-open?])))
      (is (= "Bronze" (get-in connected [:staking :value])))
      (is (= "Active staking discount" (get-in connected [:staking :helper])))
      (is (= :bronze (get-in connected [:staking :selected-value])))
      (is (true? (get-in connected [:staking :dropdown-open?])))
      (is (= "Tier 2" (get-in connected [:maker-rebate :value])))
      (is (= "Current maker rate is a rebate"
             (get-in connected [:maker-rebate :helper])))
      (is (= :tier-2 (get-in connected [:maker-rebate :selected-value])))
      (is (true? (get-in connected [:maker-rebate :dropdown-open?])))
      (is (= "0.0389%" (get-in connected [:rows 0 :taker])))
      (is (= "-0.002%" (get-in connected [:rows 0 :maker]))))
    (testing "local what-if overrides beat current wallet defaults"
      (is (= :none (get-in overridden [:referral :selected-value])))
      (is (= :diamond (get-in overridden [:staking :selected-value])))
      (is (= :tier-3 (get-in overridden [:maker-rebate :selected-value])))
      (is (= "0.027%" (get-in overridden [:rows 0 :taker])))
      (is (= "-0.003%" (get-in overridden [:rows 0 :maker]))))))
