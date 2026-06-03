(ns hyperopen.subaccounts.actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.subaccounts.actions :as actions]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def subaccount-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def other-subaccount-address
  "0x9999999999999999999999999999999999999999")

(defn- path-value
  [effects path]
  (let [[_ path-values] (first effects)
        missing (js-obj)]
    (let [value (reduce (fn [result [candidate-path candidate-value]]
                          (if (= path candidate-path)
                            (reduced candidate-value)
                            result))
                        missing
                        path-values)]
      (when-not (identical? missing value)
        value))))

(defn- base-management-state
  []
  {:wallet {:address owner-address}
   :account-context
   {:subaccounts {:rows [{:name "Desk"
                          :master owner-address
                          :sub-account-user subaccount-address}
                         {:name "Ops"
                          :master owner-address
                          :sub-account-user other-subaccount-address}]
                  :create-name ""
                  :rename-name ""
                  :transfer-amount ""
                  :transfer-direction :deposit}}})

(deftest parse-subaccounts-route-supports-hyperliquid-casing-test
  (is (= "/subAccounts" actions/canonical-route))
  (is (= {:kind :page
          :path "/subAccounts"}
         (actions/parse-subaccounts-route "/subAccounts")))
  (is (= {:kind :page
          :path "/subAccounts"}
         (actions/parse-subaccounts-route "/SUBACCOUNTS?tab=manage")))
  (is (= :other (:kind (actions/parse-subaccounts-route "/trade")))))

(deftest parse-subaccounts-route-normalizes-trailing-slashes-and-non-string-input-test
  (is (= {:kind :page
          :path "/subAccounts"}
         (actions/parse-subaccounts-route " /subaccounts///?tab=keys#section ")))
  (is (= {:kind :other
          :path "42"}
         (actions/parse-subaccounts-route 42)))
  (is (true? (actions/subaccounts-route? "/subaccounts////")))
  (is (= {:kind :other
          :path ""}
         (actions/parse-subaccounts-route nil))))

(deftest load-subaccounts-route-emits-projection-before-heavy-load-test
  (let [effects (actions/load-subaccounts-route {:wallet {:address owner-address}}
                                                "/subaccounts")]
    (is (= :effects/save-many (ffirst effects)))
    (is (= :loading (path-value effects [:account-context :subaccounts :status])))
    (is (= [] (path-value effects [:account-context :subaccounts :rows])))
    (is (= owner-address
           (path-value effects [:account-context :subaccounts :loaded-for-owner])))
    (is (nil? (path-value effects [:account-context :subaccounts :error])))
    (is (= [:effects/api-load-subaccounts]
           (second effects)))))

(deftest load-subaccounts-route-skips-heavy-load-when-route-is-inactive-or-owner-missing-test
  (is (= []
         (actions/load-subaccounts-route {:wallet {:address owner-address}}
                                         "/trade")))
  (let [effects (actions/load-subaccounts-route {:wallet {:address nil}
                                                :account-context
                                                {:subaccounts {:rows [{:name "Desk"}]
                                                               :selected-address subaccount-address}}}
                                               "/subAccounts")]
    (is (= [[:effects/save-many [[[:account-context :subaccounts :status] :idle]
                                 [[:account-context :subaccounts :loaded-for-owner] nil]
                                 [[:account-context :subaccounts :rows] []]
                                 [[:account-context :subaccounts :error] nil]
                                 [[:account-context :subaccounts :selected-address] nil]
                                 [[:account-context :subaccounts :create-name] ""]
                                 [[:account-context :subaccounts :create-popover-open?] false]
                                 [[:account-context :subaccounts :rename-name] ""]
                                 [[:account-context :subaccounts :transfer-amount] ""]
                                 [[:account-context :subaccounts :transfer-direction] :deposit]
                                 [[:account-context :subaccounts :selection-loaded?] false]]]]
           effects))
    (is (= :idle (path-value effects [:account-context :subaccounts :status])))))

(deftest select-subaccount-validates-owner-and-persists-selected-address-test
  (let [state {:wallet {:address owner-address}
               :account-context {:subaccounts
                                 {:rows [{:name "Desk"
                                          :master owner-address
                                          :sub-account-user subaccount-address}]}}}]
    (is (= [[:effects/save-many [[[:account-context :subaccounts :selected-address]
                                  subaccount-address]
                                 [[:account-context :subaccounts :error] nil]]]
            [:effects/local-storage-set
             (actions/selected-subaccount-storage-key owner-address)
             subaccount-address]
            [:effects/api-load-user-data subaccount-address]]
           (actions/select-subaccount state (str "  " subaccount-address "  "))))
    (is (= [[:effects/save [:account-context :subaccounts :error]
             "Select a subaccount owned by the connected master wallet."]]
           (actions/select-subaccount state other-subaccount-address)))))

(deftest select-master-account-clears-selection-and-refreshes-user-data-test
  (is (= [[:effects/save-many [[[:account-context :subaccounts :selected-address] nil]
                               [[:account-context :subaccounts :error] nil]]]
          [:effects/local-storage-set
           (actions/selected-subaccount-storage-key owner-address)
           ""]
          [:effects/api-load-user-data owner-address]]
         (actions/select-master-account
          {:wallet {:address owner-address}
           :account-context {:subaccounts {:selected-address subaccount-address}}}))))

(deftest selection-actions-require-connected-owner-test
  (is (= [[:effects/save [:account-context :subaccounts :error]
           "Connect your wallet before selecting a subaccount."]]
         (actions/select-subaccount {:wallet {:address nil}} subaccount-address)))
  (is (= [[:effects/save [:account-context :subaccounts :error]
           "Connect your wallet before selecting a subaccount."]]
         (actions/select-master-account {:wallet {:address nil}}))))

(deftest subaccount-management-form-fields-normalize-supported-inputs-test
  (is (= [[:effects/save-many [[[:account-context :subaccounts :create-name] " Desk "]
                                [[:account-context :subaccounts :error] nil]]]]
         (actions/set-subaccount-form-field {} :create-name " Desk ")))
  (is (= [[:effects/save-many [[[:account-context :subaccounts :transfer-direction] :withdraw]
                                [[:account-context :subaccounts :error] nil]]]]
         (actions/set-subaccount-form-field {} "transferDirection" "withdraw")))
  (is (= []
         (actions/set-subaccount-form-field {} :unknown "value"))))

(deftest subaccount-create-popover-actions-toggle-state-test
  (is (= [[:effects/save-many [[[:account-context :subaccounts :create-popover-open?] true]
                                [[:account-context :subaccounts :create-name] ""]
                                [[:account-context :subaccounts :error] nil]]]]
         (actions/open-create-popover {})))
  (is (= [[:effects/save-many [[[:account-context :subaccounts :create-popover-open?] false]
                                [[:account-context :subaccounts :create-name] ""]
                                [[:account-context :subaccounts :error] nil]]]]
         (actions/close-create-popover {}))))

(deftest copy-subaccount-address-emits-wallet-copy-effect-test
  (is (= [[:effects/copy-wallet-address subaccount-address]]
         (actions/copy-subaccount-address {} (str " " subaccount-address " ")))))

(deftest subaccount-usdc-amount-parser-uses-raw-micro-usdc-units-test
  (is (= 1230000 (actions/parse-usdc-amount->micros "1.23")))
  (is (= 1 (actions/parse-usdc-amount->micros "0.000001")))
  (is (= 1200000 (actions/parse-usdc-amount->micros "001.2")))
  (is (nil? (actions/parse-usdc-amount->micros "")))
  (is (nil? (actions/parse-usdc-amount->micros "0")))
  (is (nil? (actions/parse-usdc-amount->micros "1.0000001"))))

(deftest submit-create-subaccount-validates-name-and-emits-management-effect-test
  (is (= [[:effects/save-many [[[:account-context :subaccounts :creating?] true]
                                [[:account-context :subaccounts :create-popover-open?] true]
                                [[:account-context :subaccounts :error] nil]]]
          [:effects/api-create-subaccount {:name "Desk"}]]
         (actions/submit-create-subaccount
          (assoc-in (base-management-state)
                    [:account-context :subaccounts :create-name]
                    " Desk "))))
  (is (= [[:effects/save-many [[[:account-context :subaccounts :creating?] false]
                                [[:account-context :subaccounts :error]
                                 "Subaccount name must be 1-16 characters."]]]]
         (actions/submit-create-subaccount
          (assoc-in (base-management-state)
                    [:account-context :subaccounts :create-name]
                    "this-name-is-too-long"))))
  (is (= [[:effects/save [:account-context :subaccounts :error]
           "Connect your wallet before selecting a subaccount."]]
         (actions/submit-create-subaccount {:wallet {:address nil}}))))

(deftest rename-subaccount-actions-validate-ownership-and-emit-management-effect-test
  (is (= [[:effects/save-many [[[:account-context :subaccounts :renaming-address]
                                 subaccount-address]
                                [[:account-context :subaccounts :rename-name] "Desk"]
                                [[:account-context :subaccounts :error] nil]]]]
         (actions/start-rename-subaccount (base-management-state)
                                          subaccount-address)))
  (is (= [[:effects/save-many [[[:account-context :subaccounts :renaming-address] nil]
                                [[:account-context :subaccounts :rename-name] ""]
                                [[:account-context :subaccounts :error] nil]]]]
         (actions/cancel-rename-subaccount {})))
  (is (= [[:effects/save-many [[[:account-context :subaccounts :renaming-address]
                                 subaccount-address]
                                [[:account-context :subaccounts :error] nil]]]
          [:effects/api-rename-subaccount {:sub-account-user subaccount-address
                                           :name "Ops"}]]
         (actions/submit-rename-subaccount
          (assoc-in (base-management-state)
                    [:account-context :subaccounts :rename-name]
                    " Ops ")
          subaccount-address)))
  (is (= [[:effects/save [:account-context :subaccounts :error]
           "Select a subaccount owned by the connected master wallet."]]
         (actions/start-rename-subaccount (base-management-state)
                                          "0x0000000000000000000000000000000000000000"))))

(deftest transfer-subaccount-actions-validate-amount-and-emit-management-effect-test
  (is (= [[:effects/save-many [[[:account-context :subaccounts :transferring-address]
                                 subaccount-address]
                                [[:account-context :subaccounts :transfer-amount] ""]
                                [[:account-context :subaccounts :transfer-direction] :deposit]
                                [[:account-context :subaccounts :error] nil]]]]
         (actions/start-transfer-subaccount (base-management-state)
                                            subaccount-address)))
  (is (= [[:effects/save-many [[[:account-context :subaccounts :transferring-address] nil]
                                [[:account-context :subaccounts :transfer-amount] ""]
                                [[:account-context :subaccounts :transfer-direction] :deposit]
                                [[:account-context :subaccounts :error] nil]]]]
         (actions/cancel-transfer-subaccount {})))
  (is (= [[:effects/save-many [[[:account-context :subaccounts :transferring-address]
                                 subaccount-address]
                                [[:account-context :subaccounts :error] nil]]]
          [:effects/api-transfer-subaccount {:sub-account-user subaccount-address
                                             :is-deposit true
                                             :usd 1230000
                                             :amount "1.23"}]]
         (actions/submit-transfer-subaccount
          (-> (base-management-state)
              (assoc-in [:account-context :subaccounts :transfer-amount] "1.23")
              (assoc-in [:account-context :subaccounts :transfer-direction] :deposit))
          subaccount-address)))
  (is (= [[:effects/save-many [[[:account-context :subaccounts :transferring-address]
                                 subaccount-address]
                                [[:account-context :subaccounts :error] nil]]]
          [:effects/api-transfer-subaccount {:sub-account-user subaccount-address
                                             :is-deposit false
                                             :usd 1
                                             :amount "0.000001"}]]
         (actions/submit-transfer-subaccount
          (-> (base-management-state)
              (assoc-in [:account-context :subaccounts :transfer-amount] "0.000001")
              (assoc-in [:account-context :subaccounts :transfer-direction] :withdraw))
          subaccount-address)))
  (is (= [[:effects/save-many [[[:account-context :subaccounts :transferring-address] nil]
                                [[:account-context :subaccounts :error]
                                 "Enter a positive USDC amount with at most 6 decimal places."]]]]
         (actions/submit-transfer-subaccount
          (-> (base-management-state)
              (assoc-in [:account-context :subaccounts :transfer-amount] "0.0000001"))
          subaccount-address))))
