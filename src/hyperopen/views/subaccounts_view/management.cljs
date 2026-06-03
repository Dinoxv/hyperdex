(ns hyperopen.views.subaccounts-view.management)

(defn- action-button
  [{:keys [data-role label on-click disabled? variant]}]
  [:button {:type "button"
            :data-role data-role
            :disabled (boolean disabled?)
            :class (into ["rounded-lg"
                          "border"
                          "px-3"
                          "py-2"
                          "text-sm"
                          "font-medium"
                          "transition-colors"]
                         (cond
                           disabled?
                           ["cursor-not-allowed" "border-base-300" "bg-base-200/30" "text-trading-text-secondary"]

                           (= :primary variant)
                           ["border-[#2dceb3]" "bg-[#123a36]" "text-[#97fce4]" "hover:bg-[#174640]"]

                           :else
                           ["border-base-300" "bg-base-200/40" "text-white" "hover:bg-base-200"]))
            :on {:click on-click}}
   label])

(defn- text-input
  [{:keys [data-role value on-input placeholder disabled?]}]
  [:input {:type "text"
           :data-role data-role
           :value (or value "")
           :placeholder placeholder
           :disabled (boolean disabled?)
           :class ["min-w-0"
                   "rounded-lg"
                   "border"
                   "border-base-300"
                   "bg-base-200/30"
                   "px-3"
                   "py-2"
                   "text-sm"
                   "text-white"
                   "outline-none"
                   "placeholder:text-trading-text-secondary"
                   "focus:border-[#2dceb3]"]
           :on {:input on-input}}])

(defn create-panel
  [{:keys [subaccounts connected?]}]
  (let [creating? (true? (:creating? subaccounts))]
    [:section {:class ["rounded-lg"
                       "border"
                       "border-base-300"
                       "bg-base-100"
                       "p-4"
                       "space-y-3"]}
     [:div
      [:h2 {:class ["text-base" "font-semibold" "text-white"]} "Create Subaccount"]
      [:p {:class ["text-sm" "text-trading-text-secondary"]}
       "Names must be 1-16 characters. Hyperliquid enforces eligibility and count limits when submitted."]]
     [:div {:class ["flex" "flex-col" "gap-2" "sm:flex-row"]}
      (text-input {:data-role "subaccounts-create-name"
                   :value (:create-name subaccounts)
                   :placeholder "Subaccount name"
                   :disabled? (or creating? (not connected?))
                   :on-input [[:actions/set-subaccount-form-field
                               :create-name
                               [:event.target/value]]]})
      (action-button {:data-role "subaccounts-create-submit"
                      :label (if creating? "Creating..." "Create")
                      :variant :primary
                      :disabled? (or creating? (not connected?))
                      :on-click [[:actions/submit-create-subaccount]]})]]))

(defn- rename-controls
  [{:keys [address subaccounts]}]
  (let [active? (= address (:renaming-address subaccounts))]
    [:div {:class ["flex" "flex-col" "gap-2"]}
     (action-button {:data-role (str "subaccounts-rename-" address)
                     :label "Rename"
                     :disabled? active?
                     :on-click [[:actions/start-rename-subaccount address]]})
     (when active?
       [:div {:class ["flex" "flex-col" "gap-2" "sm:flex-row"]}
        (text-input {:data-role (str "subaccounts-rename-name-" address)
                     :value (:rename-name subaccounts)
                     :placeholder "New name"
                     :on-input [[:actions/set-subaccount-form-field
                                 :rename-name
                                 [:event.target/value]]]})
        (action-button {:data-role (str "subaccounts-rename-submit-" address)
                        :label "Save"
                        :variant :primary
                        :on-click [[:actions/submit-rename-subaccount address]]})
        (action-button {:data-role (str "subaccounts-rename-cancel-" address)
                        :label "Cancel"
                        :on-click [[:actions/cancel-rename-subaccount]]})])]))

(defn- transfer-direction-select
  [{:keys [address subaccounts]}]
  [:select {:data-role (str "subaccounts-transfer-direction-" address)
            :value (name (or (:transfer-direction subaccounts) :deposit))
            :class ["rounded-lg"
                    "border"
                    "border-base-300"
                    "bg-base-200/30"
                    "px-3"
                    "py-2"
                    "text-sm"
                    "text-white"
                    "outline-none"
                    "focus:border-[#2dceb3]"]
            :on {:change [[:actions/set-subaccount-form-field
                           :transfer-direction
                           [:event.target/value]]]}}
   [:option {:value "deposit"} "Master to subaccount"]
   [:option {:value "withdraw"} "Subaccount to master"]])

(defn- transfer-controls
  [{:keys [address subaccounts]}]
  (let [active? (= address (:transferring-address subaccounts))]
    [:div {:class ["flex" "flex-col" "gap-2"]}
     (action-button {:data-role (str "subaccounts-transfer-" address)
                     :label "Transfer"
                     :disabled? active?
                     :on-click [[:actions/start-transfer-subaccount address]]})
     (when active?
       [:div {:class ["flex" "flex-col" "gap-2" "xl:flex-row"]}
        (transfer-direction-select {:address address
                                    :subaccounts subaccounts})
        (text-input {:data-role (str "subaccounts-transfer-amount-" address)
                     :value (:transfer-amount subaccounts)
                     :placeholder "USDC"
                     :on-input [[:actions/set-subaccount-form-field
                                 :transfer-amount
                                 [:event.target/value]]]})
        (action-button {:data-role (str "subaccounts-transfer-submit-" address)
                        :label "Submit"
                        :variant :primary
                        :on-click [[:actions/submit-transfer-subaccount address]]})
        (action-button {:data-role (str "subaccounts-transfer-cancel-" address)
                        :label "Cancel"
                        :on-click [[:actions/cancel-transfer-subaccount]]})])]))

(defn row-controls
  [{:keys [address subaccounts]}]
  [:div {:class ["flex" "min-w-[220px]" "flex-col" "gap-2"]}
   (rename-controls {:address address
                     :subaccounts subaccounts})
   (transfer-controls {:address address
                       :subaccounts subaccounts})])
