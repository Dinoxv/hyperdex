(ns hyperopen.views.referrals-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.referrals-view :as referrals-view]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def referred-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(deftest referrals-view-renders-ready-share-state-and-kpis-test
  (let [view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :referrals {:raw {:cumVlm "12000"
                                  :tokenToState {:USDC {:unclaimedRewards "3.5"
                                                        :claimedRewards "1.5"}}
                                  :referrerState {:stage "ready"
                                                  :data {:code "MYCODE"
                                                         :nReferrals 1
                                                         :referralStates [{:user referred-address
                                                                           :cumVlm "1000"
                                                                           :cumReward "5"}]}}}}
               :referrals-ui {:active-tab :referrals
                              :form {:code ""
                                     :new-code ""}}})
        root (hiccup/find-by-parity-id view "referrals-root")
        code-node (hiccup/find-by-data-role view "referrals-own-code")
        join-link (hiccup/find-by-data-role view "referrals-join-link")
        row (hiccup/find-by-data-role view "referrals-row")
        rewards-stat (hiccup/find-by-data-role view "referrals-stat-rewards")
        claimable-stat (hiccup/find-by-data-role view "referrals-stat-claimable")]
    (is (some? root))
    (is (= "MYCODE" (hiccup/node-text code-node)))
    (is (= "/join/MYCODE" (hiccup/node-text join-link)))
    (is (some? row))
    (is (re-find #"\$5" (hiccup/node-text rewards-stat)))
    (is (re-find #"\$3.50" (hiccup/node-text claimable-stat)))))

(deftest referrals-view-renders-empty-and-join-code-prefill-state-test
  (let [view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :referrals {:raw {:referrerState {:stage "needToCreateCode"
                                                  :data {}}}}
               :referrals-ui {:pending-code "ABC123"
                              :active-tab :referrals
                              :form {:code "ABC123"
                                     :new-code ""}}})
        code-input (hiccup/find-by-data-role view "referrals-code")
        empty-node (hiccup/find-by-data-role view "referrals-empty")
        create-node (hiccup/find-by-data-role view "referrals-create-code")]
    (is (= "ABC123" (get-in code-input [1 :value])))
    (is (= "No referrals yet" (hiccup/node-text empty-node)))
    (is (some? create-node))))

(deftest referrals-view-renders-legacy-tab-empty-state-test
  (let [view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :referrals {:raw {:referrerState {:stage "ready"
                                                  :data {:code "MYCODE"}}}}
               :referrals-ui {:active-tab :legacy-reward-history
                              :form {:code ""
                                     :new-code ""}}})
        empty-node (hiccup/find-by-data-role view "referrals-legacy-empty")]
    (is (= "No rewards earned" (hiccup/node-text empty-node)))))
