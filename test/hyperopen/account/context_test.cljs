(ns hyperopen.account.context-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.context :as account-context]))

(deftest normalize-address-handles-trim-case-and-invalid-values-test
  (is (= "0x1111111111111111111111111111111111111111"
         (account-context/normalize-address " 0x1111111111111111111111111111111111111111 ")))
  (is (= "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
         (account-context/normalize-address "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")))
  (is (nil? (account-context/normalize-address "")))
  (is (nil? (account-context/normalize-address "0xabc")))
  (is (nil? (account-context/normalize-address "not-an-address"))))

(deftest normalize-watchlist-filters-invalid-and-deduplicates-test
  (is (= ["0x1111111111111111111111111111111111111111"
          "0x2222222222222222222222222222222222222222"]
         (account-context/normalize-watchlist
           ["0x1111111111111111111111111111111111111111"
            "0x1111111111111111111111111111111111111111"
            "0x2222222222222222222222222222222222222222"
            "bad"]))))

(deftest effective-account-address-prefers-ghost-when-active-test
  (let [owner "0x1111111111111111111111111111111111111111"
        ghost "0x2222222222222222222222222222222222222222"]
    (is (= ghost
           (account-context/effective-account-address
            {:wallet {:address owner}
             :account-context {:ghost-mode {:active? true
                                            :address ghost}}})))
    (is (= owner
           (account-context/effective-account-address
            {:wallet {:address owner}
             :account-context {:ghost-mode {:active? false
                                            :address ghost}}})))
    (is (= owner
           (account-context/effective-account-address
            {:wallet {:address owner}
             :account-context {:ghost-mode {:active? true
                                            :address "bad"}}})))))

(deftest mutations-allowed-is-disabled-during-ghost-mode-test
  (is (false? (account-context/mutations-allowed?
               {:account-context {:ghost-mode {:active? true
                                               :address "0x1111111111111111111111111111111111111111"}}})))
  (is (true? (account-context/mutations-allowed?
              {:account-context {:ghost-mode {:active? false
                                              :address "0x1111111111111111111111111111111111111111"}}}))))
