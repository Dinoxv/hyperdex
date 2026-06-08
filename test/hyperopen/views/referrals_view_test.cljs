(ns hyperopen.views.referrals-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.referrals-view :as referrals-view]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def referred-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def spectate-address
  "0x7777777777777777777777777777777777777777")

(defn- connected-state
  [overrides]
  (merge {:wallet {:address owner-address}
          :referrals-ui {:active-tab :referrals
                         :form {:code ""
                                :new-code ""}}}
         overrides))

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
                              :active-modal :enter-code
                              :active-tab :referrals
                              :form {:code "ABC123"
                                     :new-code ""}}})
        code-input (hiccup/find-by-data-role view "referrals-modal-code-input")
        empty-node (hiccup/find-by-data-role view "referrals-empty")
        create-node (hiccup/find-by-data-role view "referrals-open-create-code")]
    (is (= "ABC123" (get-in code-input [1 :value])))
    (is (= "No referrals yet" (hiccup/node-text empty-node)))
    (is (some? create-node))))

(deftest referrals-view-labels-spectated-referral-state-and-blocks-mutations-test
  (let [view (referrals-view/referrals-view
              {:wallet {:address owner-address}
               :account-context {:spectate-mode {:active? true
                                                 :address spectate-address}}
               :referrals {:raw {:referrerState {:stage "ready"
                                                  :data {:code "WATCHED"
                                                         :nReferrals 2}}}}
               :referrals-ui {:active-tab :referrals
                              :form {:code ""
                                     :new-code ""}}})
        owner-node (hiccup/find-by-data-role view "referrals-owner")
        read-only-node (hiccup/find-by-data-role view "referrals-read-only")
        share-button (hiccup/find-by-data-role view "referrals-open-share-code")]
    (is (nil? owner-node))
    (is (re-find #"Spectate Mode is read-only" (hiccup/node-text read-only-node)))
    (is (re-find #"0x7777…7777" (hiccup/node-text read-only-node)))
    (is (some? (get-in share-button [1 :disabled])))))

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

(deftest referrals-view-opens-actions-through-modal-buttons-test
  (let [view (referrals-view/referrals-view
              (connected-state
               {:referrals {:raw {:referrerState {:stage "needToCreateCode"
                                                  :data {}}}}}))
        enter-button (hiccup/find-by-data-role view "referrals-open-enter-code")
        create-button (hiccup/find-by-data-role view "referrals-open-create-code")
        claim-button (hiccup/find-by-data-role view "referrals-open-claim-rewards")]
    (is (= [[:actions/open-referrals-modal :enter-code]]
           (get-in enter-button [1 :on :click])))
    (is (= [[:actions/open-referrals-modal :create-code]]
           (get-in create-button [1 :on :click])))
    (is (= [[:actions/open-referrals-modal :claim-rewards]]
           (get-in claim-button [1 :on :click])))))

(deftest referrals-view-renders-join-confirmation-modal-test
  (let [view (referrals-view/referrals-view
              (connected-state
               {:referrals {:raw {:referrerState {:stage "needToCreateCode"
                                                  :data {}}}}
                :referrals-ui {:pending-code "ABC123"
                               :active-modal :enter-code
                               :active-tab :referrals
                               :form {:code "ABC123"
                                      :new-code ""}}}))
        modal (hiccup/find-by-data-role view "referrals-modal")
        title (hiccup/find-by-data-role view "referrals-modal-title")
        code-node (hiccup/find-by-data-role view "referrals-modal-normalized-code")
        input (hiccup/find-by-data-role view "referrals-modal-code-input")
        submit (hiccup/find-by-data-role view "referrals-modal-submit")]
    (is (= "Confirm Referral Code" (hiccup/node-text title)))
    (is (re-find #"ABC123" (hiccup/node-text modal)))
    (is (= "ABC123" (hiccup/node-text code-node)))
    (is (= "ABC123" (get-in input [1 :value])))
    (is (= [[:actions/submit-set-referrer]]
           (get-in submit [1 :on :click])))))

(deftest referrals-view-renders-create-share-and-claim-modals-test
  (let [create-view (referrals-view/referrals-view
                     (connected-state
                      {:referrals {:raw {:referrerState {:stage "needToCreateCode"
                                                         :data {}}}}
                       :referrals-ui {:active-modal :create-code
                                      :active-tab :referrals
                                      :form {:code ""
                                             :new-code "MYCODE"}}}))
        create-title (hiccup/find-by-data-role create-view "referrals-modal-title")
        create-input (hiccup/find-by-data-role create-view "referrals-modal-new-code-input")
        share-view (referrals-view/referrals-view
                    (connected-state
                     {:referrals {:raw {:referrerState {:stage "ready"
                                                        :data {:code "MYCODE"}}}}
                      :referrals-ui {:active-modal :share-code
                                     :active-tab :referrals
                                     :form {:code ""
                                            :new-code ""}}}))
        share-link (hiccup/find-by-data-role share-view "referrals-modal-join-link")
        claim-view (referrals-view/referrals-view
                    (connected-state
                     {:referrals {:raw {:tokenToState {:USDC {:unclaimedRewards "3.5"
                                                              :claimedRewards "1.5"}}
                                        :referrerState {:stage "ready"
                                                        :data {:code "MYCODE"}}}}
                      :referrals-ui {:active-modal :claim-rewards
                                     :active-tab :referrals
                                     :form {:code ""
                                            :new-code ""}}}))
        claim-title (hiccup/find-by-data-role claim-view "referrals-modal-title")
        claim-total (hiccup/find-by-data-role claim-view "referrals-modal-claim-total")]
    (is (= "Create Referral Code" (hiccup/node-text create-title)))
    (is (= "MYCODE" (get-in create-input [1 :value])))
    (is (= "/join/MYCODE" (hiccup/node-text share-link)))
    (is (= "Claim Rewards" (hiccup/node-text claim-title)))
    (is (re-find #"\$3.50" (hiccup/node-text claim-total)))))
