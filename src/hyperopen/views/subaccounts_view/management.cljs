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
                           ["border-[#54d8c6]" "bg-[#54d8c6]" "text-[#021b18]" "hover:bg-[#69e7d6]"]

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
  (let [creating? (true? (:creating? subaccounts))
        open? (true? (:create-popover-open? subaccounts))]
    [:div {:class ["relative" "flex" "w-full" "justify-end" "sm:w-auto"]
           :data-role "subaccounts-create-panel"}
     (action-button {:data-role "subaccounts-open-create-popover"
                     :label "Create Sub-Account"
                     :variant :primary
                     :disabled? (not connected?)
                     :on-click [[:actions/open-subaccount-create-popover]]})
     (when open?
       [:div {:class ["absolute"
                      "right-0"
                      "top-12"
                      "z-20"
                      "w-[min(92vw,31rem)]"
                      "rounded-xl"
                      "border"
                      "border-[#294145]"
                      "bg-[#0b171b]"
                      "p-6"
                      "shadow-[0_24px_80px_rgba(0,0,0,0.45)]"]
              :data-role "subaccounts-create-popover"}
        [:div {:class ["mb-5" "flex" "items-center" "justify-between" "gap-4"]}
         [:h2 {:class ["text-xl" "font-semibold" "text-white"]} "Create Sub-Account"]
         [:button {:type "button"
                   :data-role "subaccounts-create-popover-close"
                   :class ["text-xl" "leading-none" "text-trading-text-secondary" "hover:text-white"]
                   :on {:click [[:actions/close-subaccount-create-popover]]}}
          "x"]]
        (text-input {:data-role "subaccounts-create-name"
                     :value (:create-name subaccounts)
                     :placeholder "Name"
                     :disabled? (or creating? (not connected?))
                     :on-input [[:actions/set-subaccount-form-field
                                 :create-name
                                 [:event.target/value]]]})
        [:div {:class ["mt-5" "grid" "grid-cols-2" "gap-4"]}
         (action-button {:data-role "subaccounts-create-cancel"
                         :label "Cancel"
                         :disabled? creating?
                         :on-click [[:actions/close-subaccount-create-popover]]})
         (action-button {:data-role "subaccounts-create-submit"
                         :label (if creating? "Creating..." "Confirm")
                         :variant :primary
                         :disabled? (or creating? (not connected?))
                         :on-click [[:actions/submit-create-subaccount]]})]])]))

(defn trade-button
  [{:keys [data-role label on-click active? disabled?]}]
  [:button {:type "button"
            :data-role data-role
            :disabled (boolean disabled?)
            :class (into ["text-sm" "font-medium" "text-[#48dbc8]" "transition-colors"]
                         (cond
                           disabled? ["cursor-not-allowed" "opacity-40"]
                           active? ["text-white"]
                           :else ["hover:text-white"]))
            :on {:click on-click}}
   label])

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
