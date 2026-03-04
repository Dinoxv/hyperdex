(ns hyperopen.account.ghost-mode-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.ghost-mode-actions :as ghost-mode-actions]
            [hyperopen.platform :as platform]))

(def ^:private owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def ^:private spectated-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def ^:private secondary-address
  "0x1111111111111111111111111111111111111111")

(deftest open-ghost-mode-modal-prefills-active-address-test
  (let [state {:wallet {:address owner-address}
               :account-context {:ghost-mode {:active? true
                                              :address spectated-address}
                                 :ghost-ui {:search "0xdeadbeef"}}}]
    (is (= [[:effects/save-many [[[:account-context :ghost-ui :modal-open?] true]
                                 [[:account-context :ghost-ui :search] spectated-address]
                                 [[:account-context :ghost-ui :search-error] nil]]]]
           (ghost-mode-actions/open-ghost-mode-modal state)))))

(deftest start-ghost-mode-persists-search-watchlist-and-active-state-test
  (with-redefs [platform/now-ms (fn [] 1710000000000)]
    (let [state {:wallet {:address owner-address}
                 :account-context {:ghost-ui {:search "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD"}
                                   :watchlist [secondary-address]}}
          effects (ghost-mode-actions/start-ghost-mode state)]
      (is (= [[:effects/save-many [[[:account-context :ghost-mode :active?] true]
                                   [[:account-context :ghost-mode :address] spectated-address]
                                   [[:account-context :ghost-mode :started-at-ms] 1710000000000]
                                   [[:account-context :ghost-ui :modal-open?] false]
                                   [[:account-context :ghost-ui :search] spectated-address]
                                   [[:account-context :ghost-ui :last-search] spectated-address]
                                   [[:account-context :ghost-ui :search-error] nil]
                                   [[:account-context :watchlist] [secondary-address spectated-address]]]]
              [:effects/local-storage-set "ghost-mode-last-search:v1" spectated-address]
              [:effects/local-storage-set-json "ghost-mode-watchlist:v1" [secondary-address spectated-address]]]
             effects)))))

(deftest start-ghost-mode-rejects-invalid-address-test
  (let [state {:account-context {:ghost-ui {:search "not-an-address"}}}]
    (is (= [[:effects/save
             [:account-context :ghost-ui :search-error]
             "Enter a valid 0x-prefixed EVM address."]]
           (ghost-mode-actions/start-ghost-mode state)))))

(deftest watchlist-actions-persist-normalized-addresses-test
  (let [state {:account-context {:ghost-ui {:search ""}
                                 :watchlist [secondary-address spectated-address]}}
        add-effects (ghost-mode-actions/add-ghost-mode-watchlist-address state "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD")
        remove-effects (ghost-mode-actions/remove-ghost-mode-watchlist-address
                        {:account-context {:watchlist [secondary-address spectated-address]}}
                        "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD")]
    (is (= [[:effects/save-many [[[:account-context :watchlist] [secondary-address spectated-address]]
                                 [[:account-context :ghost-ui :search] spectated-address]
                                 [[:account-context :ghost-ui :last-search] spectated-address]
                                 [[:account-context :ghost-ui :search-error] nil]]]
            [:effects/local-storage-set "ghost-mode-last-search:v1" spectated-address]
            [:effects/local-storage-set-json "ghost-mode-watchlist:v1" [secondary-address spectated-address]]]
           add-effects))
    (is (= [[:effects/save [:account-context :watchlist] [secondary-address]]
            [:effects/local-storage-set-json "ghost-mode-watchlist:v1" [secondary-address]]]
           remove-effects))))

(deftest stop-and-spectate-actions-clear-or-ignore-as-expected-test
  (is (= [[:effects/save-many [[[:account-context :ghost-mode :active?] false]
                               [[:account-context :ghost-mode :address] nil]
                               [[:account-context :ghost-mode :started-at-ms] nil]
                               [[:account-context :ghost-ui :modal-open?] false]
                               [[:account-context :ghost-ui :search-error] nil]]]]
         (ghost-mode-actions/stop-ghost-mode {})))
  (is (= []
         (ghost-mode-actions/spectate-ghost-mode-watchlist-address {} " "))))
