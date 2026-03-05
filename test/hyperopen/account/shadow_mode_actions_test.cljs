(ns hyperopen.account.shadow-mode-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.shadow-mode-actions :as shadow-mode-actions]
            [hyperopen.platform :as platform]))

(def ^:private owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def ^:private spectated-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def ^:private secondary-address
  "0x1111111111111111111111111111111111111111")

(deftest open-shadow-mode-modal-prefills-active-address-and-stores-anchor-test
  (let [state {:wallet {:address owner-address}
               :account-context {:shadow-mode {:active? true
                                              :address spectated-address}
                                 :watchlist [{:address spectated-address
                                              :label "Assistance"}]
                                 :shadow-ui {:search "0xdeadbeef"
                                            :label "Old"}}}]
    (is (= [[:effects/save-many [[[:account-context :shadow-ui :modal-open?] true]
                                 [[:account-context :shadow-ui :anchor] {:left 100
                                                                        :right 180
                                                                        :top 18
                                                                        :bottom 58
                                                                        :viewport-width 1440
                                                                        :viewport-height 900}]
                                 [[:account-context :shadow-ui :search] spectated-address]
                                 [[:account-context :shadow-ui :label] "Assistance"]
                                 [[:account-context :shadow-ui :editing-watchlist-address] nil]
                                 [[:account-context :shadow-ui :search-error] nil]]]]
           (shadow-mode-actions/open-shadow-mode-modal state
                                                     {:left 100
                                                      :right 180
                                                      :top 18
                                                      :bottom 58
                                                      :viewport-width 1440
                                                      :viewport-height 900
                                                      :ignored "noop"})))))

(deftest start-shadow-mode-persists-search-watchlist-and-active-state-test
  (with-redefs [platform/now-ms (fn [] 1710000000000)]
    (let [state {:wallet {:address owner-address}
                 :account-context {:shadow-ui {:search "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD"
                                              :label "Assistance"}
                                   :watchlist [{:address secondary-address
                                                :label nil}]}}
          effects (shadow-mode-actions/start-shadow-mode state)]
      (is (= [[:effects/save-many [[[:account-context :shadow-mode :active?] true]
                                   [[:account-context :shadow-mode :address] spectated-address]
                                   [[:account-context :shadow-mode :started-at-ms] 1710000000000]
                                   [[:account-context :shadow-ui :modal-open?] false]
                                   [[:account-context :shadow-ui :anchor] nil]
                                   [[:account-context :shadow-ui :search] spectated-address]
                                   [[:account-context :shadow-ui :last-search] spectated-address]
                                   [[:account-context :shadow-ui :label] ""]
                                   [[:account-context :shadow-ui :editing-watchlist-address] nil]
                                   [[:account-context :shadow-ui :search-error] nil]
                                   [[:account-context :watchlist] [{:address secondary-address
                                                                    :label nil}
                                                                   {:address spectated-address
                                                                    :label "Assistance"}]]]]
              [:effects/local-storage-set "shadow-mode-last-search:v1" spectated-address]
              [:effects/local-storage-set-json "shadow-mode-watchlist:v1" [{:address secondary-address
                                                                            :label nil}
                                                                           {:address spectated-address
                                                                            :label "Assistance"}]]]
             effects)))))

(deftest start-shadow-mode-rejects-invalid-address-test
  (let [state {:account-context {:shadow-ui {:search "not-an-address"}}}]
    (is (= [[:effects/save
             [:account-context :shadow-ui :search-error]
             "Enter a valid 0x-prefixed EVM address."]]
           (shadow-mode-actions/start-shadow-mode state)))))

(deftest watchlist-actions-persist-normalized-addresses-test
  (let [watchlist [{:address secondary-address
                    :label nil}
                   {:address spectated-address
                    :label "Old Label"}]
        add-effects (shadow-mode-actions/add-shadow-mode-watchlist-address
                     {:account-context {:shadow-ui {:search ""
                                                   :label ""}
                                        :watchlist watchlist}}
                     "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD")
        edit-effects (shadow-mode-actions/add-shadow-mode-watchlist-address
                      {:account-context {:shadow-ui {:search spectated-address
                                                    :label "Updated Label"
                                                    :editing-watchlist-address spectated-address}
                                         :watchlist watchlist}})
        remove-effects (shadow-mode-actions/remove-shadow-mode-watchlist-address
                        {:account-context {:watchlist watchlist}}
                        "0xABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD")]
    (is (= [[:effects/save-many [[[:account-context :watchlist] watchlist]
                                 [[:account-context :shadow-ui :search] spectated-address]
                                 [[:account-context :shadow-ui :last-search] spectated-address]
                                 [[:account-context :shadow-ui :label] ""]
                                 [[:account-context :shadow-ui :editing-watchlist-address] nil]
                                 [[:account-context :shadow-ui :search-error] nil]]]
            [:effects/local-storage-set "shadow-mode-last-search:v1" spectated-address]
            [:effects/local-storage-set-json "shadow-mode-watchlist:v1" watchlist]]
           add-effects))
    (is (= [[:effects/save-many [[[:account-context :watchlist] [{:address secondary-address
                                                                  :label nil}
                                                                 {:address spectated-address
                                                                  :label "Updated Label"}]]
                                 [[:account-context :shadow-ui :search] spectated-address]
                                 [[:account-context :shadow-ui :last-search] spectated-address]
                                 [[:account-context :shadow-ui :label] ""]
                                 [[:account-context :shadow-ui :editing-watchlist-address] nil]
                                 [[:account-context :shadow-ui :search-error] nil]]]
            [:effects/local-storage-set "shadow-mode-last-search:v1" spectated-address]
            [:effects/local-storage-set-json "shadow-mode-watchlist:v1" [{:address secondary-address
                                                                          :label nil}
                                                                         {:address spectated-address
                                                                          :label "Updated Label"}]]]
           edit-effects))
    (is (= [[:effects/save-many [[[:account-context :watchlist] [{:address secondary-address
                                                                  :label nil}]]]]
            [:effects/local-storage-set-json "shadow-mode-watchlist:v1" [{:address secondary-address
                                                                          :label nil}]]]
           remove-effects))))

(deftest label-edit-and-copy-actions-emit-expected-effects-test
  (let [watchlist [{:address spectated-address
                    :label "Assistance"}]]
    (is (= [[:effects/save-many [[[:account-context :shadow-ui :search] spectated-address]
                                 [[:account-context :shadow-ui :label] "Assistance"]
                                 [[:account-context :shadow-ui :editing-watchlist-address] spectated-address]
                                 [[:account-context :shadow-ui :search-error] nil]]]]
           (shadow-mode-actions/edit-shadow-mode-watchlist-address
            {:account-context {:watchlist watchlist}}
            spectated-address)))
    (is (= [[:effects/save-many [[[:account-context :shadow-ui :label] ""]
                                 [[:account-context :shadow-ui :editing-watchlist-address] nil]
                                 [[:account-context :shadow-ui :search-error] nil]]]]
           (shadow-mode-actions/clear-shadow-mode-watchlist-edit {})))
    (is (= [[:effects/copy-wallet-address spectated-address]]
           (shadow-mode-actions/copy-shadow-mode-watchlist-address
            {}
            spectated-address)))))

(deftest stop-and-spectate-actions-clear-or-ignore-as-expected-test
  (is (= [[:effects/save-many [[[:account-context :shadow-mode :active?] false]
                               [[:account-context :shadow-mode :address] nil]
                               [[:account-context :shadow-mode :started-at-ms] nil]
                               [[:account-context :shadow-ui :modal-open?] false]
                               [[:account-context :shadow-ui :anchor] nil]
                               [[:account-context :shadow-ui :label] ""]
                               [[:account-context :shadow-ui :editing-watchlist-address] nil]
                               [[:account-context :shadow-ui :search-error] nil]]]]
         (shadow-mode-actions/stop-shadow-mode {})))
  (is (= []
         (shadow-mode-actions/spectate-shadow-mode-watchlist-address {} " "))))
