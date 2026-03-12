(ns hyperopen.views.staking-view
  (:require [hyperopen.utils.formatting :as fmt]
            [hyperopen.views.staking.vm :as staking-vm]))

(defn- format-hype
  [value]
  (if (number? value)
    (str (fmt/format-fixed-number value 4) " HYPE")
    "--"))

(defn- format-compact-hype
  [value]
  (if (number? value)
    (fmt/format-fixed-number value 4)
    "--"))

(defn- format-percent
  [value]
  (if (number? value)
    (str (fmt/format-fixed-number (* value 100) 2) "%")
    "--"))

(defn- status-chip
  [status]
  (let [[label classes]
        (case status
          :active ["Active" ["border-[#1f5f52]" "bg-[#123a36]" "text-[#97fce4]"]]
          :jailed ["Jailed" ["border-[#6b2638]" "bg-[#2d1118]" "text-[#ff99ac]"]]
          ["Inactive" ["border-base-300" "bg-base-100" "text-trading-text-secondary"]])]
    [:span {:class (into ["inline-flex"
                          "items-center"
                          "rounded-md"
                          "border"
                          "px-2"
                          "py-0.5"
                          "text-xs"
                          "font-medium"
                          "uppercase"
                          "tracking-[0.06em]"]
                         classes)}
     label]))

(defn- summary-card
  [label value data-role]
  [:div {:class ["rounded-xl"
                 "border"
                 "border-base-300"
                 "bg-base-100"
                 "px-4"
                 "py-3"
                 "space-y-1"]
         :data-role data-role}
   [:div {:class ["text-xs" "uppercase" "tracking-[0.08em]" "text-trading-text-secondary"]}
    label]
   [:div {:class ["text-lg" "font-semibold" "text-white" "num"]}
    value]])

(defn- key-value-row
  [label value]
  [:div {:class ["flex" "items-center" "justify-between" "gap-3" "text-sm"]}
   [:span {:class ["text-trading-text-secondary"]}
    label]
   [:span {:class ["num" "text-trading-text"]}
    value]])

(defn- action-card
  [{:keys [title
           description
           input-id
           amount
           submitting?
           connected?
           on-change
           on-max
           on-submit
           button-label]}]
  [:div {:class ["rounded-xl"
                 "border"
                 "border-base-300"
                 "bg-base-100"
                 "p-4"
                 "space-y-3"]}
   [:div {:class ["space-y-1"]}
    [:h3 {:class ["text-sm" "font-semibold" "text-white"]}
     title]
    [:p {:class ["text-xs" "text-trading-text-secondary"]}
     description]]
   [:div {:class ["flex" "items-center" "gap-2"]}
    [:input {:id input-id
             :type "text"
             :inputmode "decimal"
             :placeholder "0.0"
             :value amount
             :class ["h-9"
                     "w-full"
                     "rounded-xl"
                     "border"
                     "border-base-300"
                     "bg-base-100"
                     "px-3"
                     "text-sm"
                     "text-trading-text"
                     "focus:outline-none"
                     "focus:ring-0"
                     "focus:ring-offset-0"]
             :on {:input [on-change]}}]
    [:button {:type "button"
              :class ["h-9"
                      "rounded-lg"
                      "border"
                      "border-base-300"
                      "px-2.5"
                      "text-xs"
                      "font-semibold"
                      "text-trading-text-secondary"
                      "transition-colors"
                      "hover:bg-base-200"
                      "focus:outline-none"
                      "focus:ring-0"
                      "focus:ring-offset-0"]
              :on {:click [on-max]}}
     "Max"]]
   [:button {:type "button"
             :class ["h-9"
                     "w-full"
                     "rounded-xl"
                     "border"
                     "border-[#2f7f73]"
                     "bg-[#123a36]"
                     "text-sm"
                     "font-semibold"
                     "text-[#97fce4]"
                     "transition-colors"
                     "hover:bg-[#185047]"
                     "disabled:cursor-not-allowed"
                     "disabled:opacity-60"]
             :disabled (or submitting?
                           (not connected?))
             :on {:click [on-submit]}}
    (if submitting?
      "Submitting..."
      button-label)]])

(defn- tab-button
  [active? label action]
  [:button {:type "button"
            :class (into ["rounded-lg"
                          "border"
                          "px-2.5"
                          "py-1.5"
                          "text-xs"
                          "font-medium"
                          "transition-colors"
                          "focus:outline-none"
                          "focus:ring-0"
                          "focus:ring-offset-0"]
                         (if active?
                           ["border-[#2f7f73]" "bg-[#123a36]" "text-[#97fce4]"]
                           ["border-base-300" "text-trading-text-secondary" "hover:bg-base-200"]))
            :on {:click [action]}}
   label])

(defn- validator-row
  [{:keys [validator
           name
           description
           stake
           your-stake
           uptime-fraction
           predicted-apr
           status
           commission]}]
  [:tr {:class ["border-b" "border-base-300/50" "text-sm" "hover:bg-base-200/40"]
        :data-role "staking-validator-row"}
   [:td {:class ["px-3" "py-2.5"]}
    [:div {:class ["space-y-0.5"]}
     [:div {:class ["font-medium" "text-white"]}
      name]
     [:div {:class ["text-xs" "text-trading-text-secondary" "truncate" "max-w-[280px]"]}
      (or description validator)]]]
   [:td {:class ["px-3" "py-2.5" "num"]} (format-compact-hype stake)]
   [:td {:class ["px-3" "py-2.5" "num"]} (format-compact-hype your-stake)]
   [:td {:class ["px-3" "py-2.5" "num"]} (format-percent uptime-fraction)]
   [:td {:class ["px-3" "py-2.5" "num"]} (format-percent predicted-apr)]
   [:td {:class ["px-3" "py-2.5"]} (status-chip status)]
   [:td {:class ["px-3" "py-2.5" "num"]} (format-percent commission)]
   [:td {:class ["px-3" "py-2.5"]}
    [:button {:type "button"
              :class ["rounded-md"
                      "border"
                      "border-base-300"
                      "px-2"
                      "py-1"
                      "text-xs"
                      "text-trading-text-secondary"
                      "transition-colors"
                      "hover:bg-base-200"
                      "focus:outline-none"
                      "focus:ring-0"
                      "focus:ring-offset-0"]
              :on {:click [[:actions/select-staking-validator validator]]}}
     "Select"]]])

(defn- history-table
  [rows columns empty-text row-render]
  [:div {:class ["overflow-x-auto" "rounded-xl" "border" "border-base-300"]}
   [:table {:class ["min-w-full" "bg-base-100"]}
    [:thead
     [:tr {:class ["text-xs" "text-trading-text-secondary"]}
      (for [column columns]
        ^{:key column}
        [:th {:class ["px-3" "py-2" "text-left"]}
         column])]]
    [:tbody
     (if (seq rows)
       (map row-render rows)
       [:tr
        [:td {:col-span (count columns)
              :class ["px-3" "py-6" "text-center" "text-sm" "text-trading-text-secondary"]}
         empty-text]])]]])

(defn staking-view
  [state]
  (let [{:keys [connected?
                effective-address
                active-tab
                tabs
                validator-timeframe
                timeframe-options
                loading?
                error
                summary
                balances
                validators
                rewards
                history
                selected-validator
                form
                submitting
                delegations]} (staking-vm/staking-vm state)]
    [:div {:class ["flex"
                   "h-full"
                   "w-full"
                   "flex-col"
                   "gap-3"
                   "app-shell-gutter"
                   "pt-4"
                   "pb-16"]
           :data-parity-id "staking-root"}
     [:div {:class ["rounded-xl" "border" "border-base-300" "bg-base-100" "p-4" "space-y-3"]}
      [:div {:class ["flex" "flex-wrap" "items-start" "justify-between" "gap-3"]}
       [:div {:class ["space-y-1"]}
        [:h1 {:class ["text-xl" "font-semibold" "text-white"]}
         "Staking"]
        [:p {:class ["text-sm" "text-trading-text-secondary"]}
         "Delegate HYPE to validators, monitor rewards, and manage staking balance."]
        (when (seq effective-address)
          [:p {:class ["text-xs" "text-trading-text-secondary" "num"]}
           (str "Account: " effective-address)])]
       (when-not connected?
         [:button {:type "button"
                   :class ["h-9"
                           "rounded-xl"
                           "border"
                           "border-[#2f7f73]"
                           "bg-[#123a36]"
                           "px-3.5"
                           "text-sm"
                           "font-semibold"
                           "text-[#97fce4]"
                           "transition-colors"
                           "hover:bg-[#185047]"]
                   :data-role "staking-establish-connection"
                   :on {:click [[:actions/connect-wallet]]}}
          "Establish Connection"])]

      [:div {:class ["grid" "gap-2" "sm:grid-cols-3"]}
       (summary-card "Total Staked" (format-hype (:total-staked summary)) "staking-summary-total")
       (summary-card "Your Stake" (format-hype (:your-stake summary)) "staking-summary-user")
       (summary-card "Staking Balance" (format-hype (:staking-balance summary)) "staking-summary-balance")]

      [:div {:class ["rounded-xl" "border" "border-base-300" "bg-base-100" "p-3" "space-y-2"]
             :data-role "staking-balance-panel"}
       (key-value-row "Available to Transfer to Staking Balance"
                      (format-hype (:available-transfer balances)))
       (key-value-row "Available to Stake" (format-hype (:available-stake balances)))
       (key-value-row "Total Staked" (format-hype (:total-staked balances)))
       (key-value-row "Pending Transfers to Spot Balance"
                      (format-hype (:pending-withdrawals balances)))]

      [:div {:class ["grid" "gap-3" "lg:grid-cols-2"]}
       (action-card {:title "Transfer to Staking Balance"
                     :description "Move HYPE from spot to staking balance."
                     :input-id "staking-deposit-amount"
                     :amount (:deposit-amount form)
                     :submitting? (true? (:deposit? submitting))
                     :connected? connected?
                     :on-change [:actions/set-staking-form-field :deposit-amount [:event.target/value]]
                     :on-max :actions/set-staking-deposit-amount-to-max
                     :on-submit :actions/submit-staking-deposit
                     :button-label "Transfer In"})
       (action-card {:title "Transfer to Spot Balance"
                     :description "Move HYPE from staking balance back to spot."
                     :input-id "staking-withdraw-amount"
                     :amount (:withdraw-amount form)
                     :submitting? (true? (:withdraw? submitting))
                     :connected? connected?
                     :on-change [:actions/set-staking-form-field :withdraw-amount [:event.target/value]]
                     :on-max :actions/set-staking-withdraw-amount-to-max
                     :on-submit :actions/submit-staking-withdraw
                     :button-label "Transfer Out"})
       (action-card {:title "Stake"
                     :description "Delegate staking balance to a validator."
                     :input-id "staking-delegate-amount"
                     :amount (:delegate-amount form)
                     :submitting? (true? (:delegate? submitting))
                     :connected? connected?
                     :on-change [:actions/set-staking-form-field :delegate-amount [:event.target/value]]
                     :on-max :actions/set-staking-delegate-amount-to-max
                     :on-submit :actions/submit-staking-delegate
                     :button-label "Stake"})
       (action-card {:title "Unstake"
                     :description "Undelegate a validator position back to staking balance."
                     :input-id "staking-undelegate-amount"
                     :amount (:undelegate-amount form)
                     :submitting? (true? (:undelegate? submitting))
                     :connected? connected?
                     :on-change [:actions/set-staking-form-field :undelegate-amount [:event.target/value]]
                     :on-max :actions/set-staking-undelegate-amount-to-max
                     :on-submit :actions/submit-staking-undelegate
                     :button-label "Unstake"})]

      [:div {:class ["flex" "flex-wrap" "items-center" "gap-2"]}
       [:label {:for "staking-selected-validator"
                :class ["text-xs" "uppercase" "tracking-[0.08em]" "text-trading-text-secondary"]}
        "Selected Validator"]
       [:input {:id "staking-selected-validator"
                :type "text"
                :value selected-validator
                :placeholder "0x..."
                :class ["h-9"
                        "w-full"
                        "max-w-xl"
                        "rounded-xl"
                        "border"
                        "border-base-300"
                        "bg-base-100"
                        "px-3"
                        "text-sm"
                        "text-trading-text"
                        "focus:outline-none"
                        "focus:ring-0"
                        "focus:ring-offset-0"]
                :on {:input [[:actions/set-staking-form-field :selected-validator [:event.target/value]]]}}]

      (when (and (empty? selected-validator)
                 (seq delegations))
        [:p {:class ["text-xs" "text-trading-text-secondary"]}
         "Select a validator from the table to prefill stake/unstake actions."])]

     [:div {:class ["flex" "flex-wrap" "items-center" "justify-between" "gap-2"]}
      [:div {:class ["flex" "flex-wrap" "items-center" "gap-1"]}
       (for [{:keys [value label]} tabs]
         ^{:key value}
         (tab-button (= value active-tab)
                     label
                     [:actions/set-staking-active-tab value]))]
      (when (= :validator-performance active-tab)
        [:div {:class ["flex" "items-center" "gap-1"]
               :data-role "staking-timeframe-toggle"}
         (for [{:keys [value label]} timeframe-options]
           ^{:key value}
           (tab-button (= value validator-timeframe)
                       label
                       [:actions/set-staking-validator-timeframe value]))])]

     (case active-tab
       :staking-reward-history
       (history-table
        rewards
        ["Time" "Source" "Amount"]
        (if loading?
          "Loading staking rewards..."
          "No staking rewards found.")
        (fn [{:keys [time-ms source total-amount]}]
          ^{:key (str "reward-" time-ms "-" source)}
          [:tr {:class ["border-b" "border-base-300/50" "text-sm"]}
           [:td {:class ["px-3" "py-2.5" "num"]}
            (or (fmt/format-local-date-time time-ms) "--")]
           [:td {:class ["px-3" "py-2.5"]}
            (or source "--")]
           [:td {:class ["px-3" "py-2.5" "num"]}
            (format-hype total-amount)]]))

       :staking-action-history
       (history-table
        history
        ["Time" "Action" "Amount" "Status" "Tx"]
        (if loading?
          "Loading staking action history..."
          "No staking actions found.")
        (fn [{:keys [time-ms kind amount status hash]}]
          ^{:key (str "history-" time-ms "-" hash)}
          [:tr {:class ["border-b" "border-base-300/50" "text-sm"]}
           [:td {:class ["px-3" "py-2.5" "num"]}
            (or (fmt/format-local-date-time time-ms) "--")]
           [:td {:class ["px-3" "py-2.5"]}
            kind]
           [:td {:class ["px-3" "py-2.5" "num"]}
            (format-hype amount)]
           [:td {:class ["px-3" "py-2.5"]}
            (or status "--")]
           [:td {:class ["px-3" "py-2.5" "num"]}
            (or (some-> hash (subs 0 (min 10 (count hash)))) "--")]]))

       ;; Default: validator performance table
       [:div {:class ["overflow-x-auto" "rounded-xl" "border" "border-base-300"]}
        [:table {:class ["min-w-full" "bg-base-100"]
                 :data-role "staking-validator-table"}
         [:thead
          [:tr {:class ["text-xs" "text-trading-text-secondary"]}
           [:th {:class ["px-3" "py-2" "text-left"]} "Name"]
           [:th {:class ["px-3" "py-2" "text-left"]} "Stake"]
           [:th {:class ["px-3" "py-2" "text-left"]} "Your Stake"]
           [:th {:class ["px-3" "py-2" "text-left"]} "Uptime"]
           [:th {:class ["px-3" "py-2" "text-left"]} "Est. APR"]
           [:th {:class ["px-3" "py-2" "text-left"]} "Status"]
           [:th {:class ["px-3" "py-2" "text-left"]} "Commission"]
           [:th {:class ["px-3" "py-2" "text-left"]} "Action"]]]
         [:tbody
          (if (seq validators)
            (for [row validators]
              ^{:key (:validator row)}
              (validator-row row))
            [:tr
             [:td {:col-span 8
                   :class ["px-3" "py-6" "text-center" "text-sm" "text-trading-text-secondary"]}
              (if loading?
                "Loading validators..."
                "No validator data available.")]])]]])

     (when (seq error)
       [:div {:class ["rounded-xl"
                      "border"
                      "border-[#7a2836]"
                      "bg-[#2b1118]"
                      "px-3"
                      "py-2"
                      "text-sm"
                      "text-[#ff9db2]"]
              :data-role "staking-error"}
        error])]]))
