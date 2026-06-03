(ns hyperopen.views.subaccounts-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup :as shared-hiccup]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]
            [hyperopen.views.subaccounts-view :as view]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def subaccount-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def other-subaccount-address
  "0x9999999999999999999999999999999999999999")

(defn- base-state []
  {:wallet {:address owner-address}
   :webdata2 {:clearinghouseState {:marginSummary {:accountValue "999.99"}}}
   :spot {:clearinghouse-state {:balances [{:coin "USDC"
                                             :total "50.50"}]}}
   :account-context
   {:subaccounts
    {:status :loaded
     :loaded-for-owner owner-address
     :rows [{:name "Desk"
             :master owner-address
             :sub-account-user subaccount-address
             :clearinghouse-state {:marginSummary {:accountValue "123.45"}}
             :spot-state {:balances [{:coin "USDC"
                                       :total "250.25"}]}}
            {:name "Ops"
             :master owner-address
             :sub-account-user other-subaccount-address
             :clearinghouse-state {:marginSummary {:accountValue "0"}}}]
     :error nil
     :selected-address subaccount-address
     :selection-loaded? true}}})

(deftest subaccounts-view-renders-master-and-subaccount-selection-actions-test
  (let [view-node (view/subaccounts-view (base-state))
        root (shared-hiccup/find-by-parity-id view-node "subaccounts-root")
        console (hiccup/find-by-data-role view-node "subaccounts-console")
        create-panel (hiccup/find-by-data-role view-node "subaccounts-create-panel")
        master-row (hiccup/find-by-data-role view-node "subaccounts-master-row")
        refresh-button (hiccup/find-by-data-role view-node "subaccounts-refresh")
        selected-row (hiccup/find-by-data-role view-node (str "subaccounts-row-" subaccount-address))
        other-row (hiccup/find-by-data-role view-node (str "subaccounts-row-" other-subaccount-address))
        select-master (hiccup/find-by-data-role view-node "subaccounts-select-master")
        select-other (hiccup/find-by-data-role view-node (str "subaccounts-select-" other-subaccount-address))
        strings (set (hiccup/collect-strings view-node))]
    (is (some? root))
    (is (contains? strings "Sub-Accounts"))
    (is (contains? strings "Master Account"))
    (is (contains? strings "Perps Account Equity"))
    (is (contains? strings "Spot Account Equity"))
    (is (contains? strings "Desk"))
    (is (contains? strings "Ops"))
    (is (contains? strings "$999.99"))
    (is (contains? strings "$50.50"))
    (is (contains? strings "$123.45"))
    (is (contains? strings "$250.25"))
    (is (contains? strings "Selected"))
    (is (some? master-row))
    (is (some? console))
    (is (some? create-panel))
    (is (some? selected-row))
    (is (some? other-row))
    (is (= [[:actions/load-subaccounts-route "/subAccounts"]]
           (get-in refresh-button [1 :on :click])))
    (is (= [[:actions/select-master-account]]
           (get-in select-master [1 :on :click])))
    (is (= [[:actions/select-subaccount other-subaccount-address]]
           (get-in select-other [1 :on :click])))))

(deftest subaccounts-view-renders-empty-and-error-states-test
  (let [empty-node (view/subaccounts-view
                    {:wallet {:address owner-address}
                     :account-context {:subaccounts {:status :loaded
                                                     :rows []
                                                     :selected-address nil}}})
        error-node (view/subaccounts-view
                    {:wallet {:address owner-address}
                     :account-context {:subaccounts {:status :error
                                                     :rows []
                                                     :error "boom"}}})
        disconnected-node (view/subaccounts-view
                           {:wallet {:address nil}
                            :account-context {:subaccounts {:status :idle
                                                            :rows []}}})]
    (is (contains? (set (hiccup/collect-strings empty-node))
                   "No subaccounts found for this master account."))
    (is (contains? (set (hiccup/collect-strings error-node))
                   "boom"))
    (is (contains? (set (hiccup/collect-strings disconnected-node))
                   "Connect a wallet to load and select subaccounts."))))

(deftest subaccounts-view-renders-create-rename-and-transfer-controls-test
  (let [view-node (view/subaccounts-view
                   (-> (base-state)
                       (assoc-in [:account-context :subaccounts :create-name] "New Desk")
                       (assoc-in [:account-context :subaccounts :rename-name] "Desk B")
                       (assoc-in [:account-context :subaccounts :transfer-amount] "1.23")
                       (assoc-in [:account-context :subaccounts :transfer-direction] :withdraw)
                       (assoc-in [:account-context :subaccounts :renaming-address] subaccount-address)
                       (assoc-in [:account-context :subaccounts :transferring-address] other-subaccount-address)))
        create-input (hiccup/find-by-data-role view-node "subaccounts-create-name")
        create-submit (hiccup/find-by-data-role view-node "subaccounts-create-submit")
        rename-button (hiccup/find-by-data-role view-node
                                                (str "subaccounts-rename-" subaccount-address))
        rename-input (hiccup/find-by-data-role view-node
                                               (str "subaccounts-rename-name-" subaccount-address))
        rename-submit (hiccup/find-by-data-role view-node
                                                (str "subaccounts-rename-submit-" subaccount-address))
        transfer-button (hiccup/find-by-data-role view-node
                                                  (str "subaccounts-transfer-" other-subaccount-address))
        transfer-amount (hiccup/find-by-data-role view-node
                                                  (str "subaccounts-transfer-amount-" other-subaccount-address))
        transfer-direction (hiccup/find-by-data-role view-node
                                                     (str "subaccounts-transfer-direction-" other-subaccount-address))
        transfer-submit (hiccup/find-by-data-role view-node
                                                  (str "subaccounts-transfer-submit-" other-subaccount-address))]
    (is (= "New Desk" (get-in create-input [1 :value])))
    (is (= [[:actions/set-subaccount-form-field :create-name [:event.target/value]]]
           (get-in create-input [1 :on :input])))
    (is (= [[:actions/submit-create-subaccount]]
           (get-in create-submit [1 :on :click])))
    (is (= [[:actions/start-rename-subaccount subaccount-address]]
           (get-in rename-button [1 :on :click])))
    (is (= "Desk B" (get-in rename-input [1 :value])))
    (is (= [[:actions/set-subaccount-form-field :rename-name [:event.target/value]]]
           (get-in rename-input [1 :on :input])))
    (is (= [[:actions/submit-rename-subaccount subaccount-address]]
           (get-in rename-submit [1 :on :click])))
    (is (= [[:actions/start-transfer-subaccount other-subaccount-address]]
           (get-in transfer-button [1 :on :click])))
    (is (= "1.23" (get-in transfer-amount [1 :value])))
    (is (= [[:actions/set-subaccount-form-field :transfer-amount [:event.target/value]]]
           (get-in transfer-amount [1 :on :input])))
    (is (= "withdraw" (get-in transfer-direction [1 :value])))
    (is (= [[:actions/set-subaccount-form-field :transfer-direction [:event.target/value]]]
           (get-in transfer-direction [1 :on :change])))
    (is (= [[:actions/submit-transfer-subaccount other-subaccount-address]]
           (get-in transfer-submit [1 :on :click])))))
