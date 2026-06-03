(ns hyperopen.views.subaccounts-view.management)

(defn- action-button
  [{:keys [data-role label on-click disabled? variant]}]
  [:button {:type "button"
            :data-role data-role
            :disabled (boolean disabled?)
            :class (into ["inline-flex"
                          "h-8"
                          "items-center"
                          "justify-center"
                          "rounded-md"
                          "border"
                          "px-2.5"
                          "text-xs"
                          "font-medium"
                          "leading-none"
                          "transition-colors"]
                         (cond
                           disabled?
                           ["cursor-not-allowed" "border-base-300" "bg-base-200/30" "text-trading-text-secondary"]

                           (= :primary variant)
                           ["border-[#2dceb3]" "bg-[#0f3a35]" "text-[#97fce4]" "hover:bg-[#174640]"]

                           :else
                           ["border-base-300" "bg-[#121d20]" "text-white" "hover:border-[#2dceb3]/60" "hover:bg-[#172528]"]))
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
                   "h-8"
                   "rounded-md"
                   "border"
                   "border-base-300"
                   "bg-[#0a1417]"
                   "px-2.5"
                   "text-xs"
                   "text-white"
                   "outline-none"
                   "placeholder:text-trading-text-secondary"
                   "focus:border-[#2dceb3]"]
           :on {:input on-input}}])

(defn create-panel
  [{:keys [subaccounts connected?]}]
  (let [creating? (true? (:creating? subaccounts))]
    [:div {:class ["flex" "w-full" "flex-col" "gap-2" "sm:w-auto" "sm:flex-row" "sm:items-center"]
           :data-role "subaccounts-create-panel"}
     [:span {:class ["text-xs" "font-medium" "text-trading-text-secondary"]}
      "Create Subaccount"]
     [:div {:class ["flex" "min-w-0" "flex-col" "gap-2" "sm:flex-row"]}
      (text-input {:data-role "subaccounts-create-name"
                   :value (:create-name subaccounts)
                   :placeholder "Name, 1-16 chars"
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
    [:div {:class ["flex" "min-w-0" "flex-col" "gap-2"]}
     (action-button {:data-role (str "subaccounts-rename-" address)
                     :label "Rename"
                     :disabled? active?
                     :on-click [[:actions/start-rename-subaccount address]]})
     (when active?
       [:div {:class ["flex" "min-w-[18rem]" "flex-col" "gap-2" "sm:flex-row"]}
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
            :class ["h-8"
                    "rounded-md"
                    "border"
                    "border-base-300"
                    "bg-[#0a1417]"
                    "px-2.5"
                    "text-xs"
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
    [:div {:class ["flex" "min-w-0" "flex-col" "gap-2"]}
     (action-button {:data-role (str "subaccounts-transfer-" address)
                     :label "Transfer"
                     :disabled? active?
                     :on-click [[:actions/start-transfer-subaccount address]]})
     (when active?
       [:div {:class ["flex" "min-w-[18rem]" "flex-col" "gap-2" "xl:flex-row"]}
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
  [:div {:class ["flex" "min-w-[16rem]" "flex-wrap" "items-start" "justify-end" "gap-2"]}
   (rename-controls {:address address
                     :subaccounts subaccounts})
   (transfer-controls {:address address
                       :subaccounts subaccounts})])
