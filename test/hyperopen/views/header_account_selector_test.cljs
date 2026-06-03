(ns hyperopen.views.header-account-selector-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.header-view :as header-view]
            [hyperopen.views.header.account-selector :as selector]
            [hyperopen.wallet.core :as wallet]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def subaccount-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def other-subaccount-address
  "0x9999999999999999999999999999999999999999")

(defn- state
  [& {:keys [connected? selected rows]}]
  {:wallet {:connected? (not= false connected?)
            :address owner-address}
   :account-context {:subaccounts
                     {:selected-address selected
                      :rows (or rows
                                [{:name "Desk"
                                  :master owner-address
                                  :sub-account-user subaccount-address}
                                 {:name "Other Owner"
                                  :master other-subaccount-address
                                  :sub-account-user other-subaccount-address}])}}})

(deftest header-account-selector-vm-projects-owned-master-and-subaccount-options-test
  (let [result (selector/vm (state :selected subaccount-address))
        master-option (first (:options result))
        subaccount-option (second (:options result))]
    (is (= "Desk" (:trigger-label result)))
    (is (= (wallet/short-addr subaccount-address)
           (:trigger-address-label result)))
    (is (= ["Master" "Desk"]
           (mapv :label (:options result))))
    (is (= [[:actions/select-master-account]]
           (:action master-option)))
    (is (= [[:actions/select-subaccount subaccount-address]]
           (:action subaccount-option)))
    (is (false? (:selected? master-option)))
    (is (true? (:selected? subaccount-option)))))

(deftest header-account-selector-vm-hides-without-connected-owned-subaccounts-test
  (is (nil? (selector/vm (state :connected? false))))
  (is (nil? (selector/vm (state :rows []))))
  (let [result (selector/vm (state :selected other-subaccount-address))]
    (is (= "Master" (:trigger-label result)))
    (is (true? (:selected? (first (:options result)))))))

(deftest header-renders-account-target-menu-with-existing-selection-actions-test
  (let [view (header-view/header-view (state :selected subaccount-address))
        details (hiccup/find-by-data-role view "header-account-target-details")
        trigger (hiccup/find-by-data-role view "header-account-target-trigger")
        menu (hiccup/find-by-data-role view "header-account-target-menu")
        master-option (hiccup/find-by-data-role
                       view
                       "header-account-target-option-master")
        subaccount-option (hiccup/find-by-data-role
                           view
                           (str "header-account-target-option-"
                                subaccount-address))
        strings (set (hiccup/collect-strings view))]
    (is (some? details))
    (is (some? trigger))
    (is (= "Account target" (get-in trigger [1 :aria-label])))
    (is (some? menu))
    (is (contains? strings "Master"))
    (is (contains? strings "Desk"))
    (is (= [[:actions/select-master-account]]
           (get-in master-option [1 :on :click])))
    (is (= [[:actions/select-subaccount subaccount-address]]
           (get-in subaccount-option [1 :on :click])))
    (is (= "true" (get-in subaccount-option [1 :aria-current])))))
