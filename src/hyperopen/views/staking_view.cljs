(ns hyperopen.views.staking-view
  (:require [hyperopen.utils.formatting :as fmt]
            [hyperopen.views.staking.vm :as staking-vm]))

(defn- format-summary-hype
  [value]
  (if (number? value)
    (or (fmt/format-integer value) "0")
    "--"))

(defn- format-balance-hype
  [value]
  (if (number? value)
    (str (fmt/format-fixed-number value 8) " HYPE")
    "--"))

(defn- format-table-hype
  [value]
  (if (number? value)
    (or (fmt/format-integer value) "0")
    "--"))

(defn- format-percent
  [value]
  (if (number? value)
    (str (fmt/format-fixed-number (* value 100) 2) "%")
    "--"))

(defn- status-pill
  [status]
  (let [[label classes]
        (case status
          :active ["Active" ["text-[#97fce4]"]]
          :jailed ["Jailed" ["text-[#ff99ac]"]]
          ["Inactive" ["text-[#9aa3a4]"]])]
    [:span {:class (into ["text-xs" "font-normal" "leading-6"]
                         classes)}
     label]))

(defn- summary-card
  [label value data-role]
  [:div {:class ["rounded-[10px]"
                 "border"
                 "border-[#1b2429]"
                 "bg-[#0f1a1f]"
                 "px-4"
                 "py-3"
                 "space-y-2"]
         :data-role data-role}
   [:div {:class ["text-sm" "leading-[15px]" "font-normal" "text-[#878c8f]"]}
    label]
   [:div {:class ["text-[30px]" "sm:text-[34px]" "leading-none" "font-normal" "text-[#f6fefd]" "num"]}
    value]])

(defn- key-value-row
  [label value]
  [:div {:class ["flex" "items-start" "justify-between" "gap-3" "text-xs"]}
   [:span {:class ["text-[#9aa3a4]" "leading-[15px]"]}
    label]
   [:span {:class ["num" "text-[#f6fefd]" "font-normal" "leading-[15px]"]}
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
  [:div {:class ["rounded-[10px]"
                 "border"
                 "border-[#1b2429]"
                 "bg-[#0f1a1f]"
                 "p-4"
                 "space-y-3"]}
   [:div {:class ["space-y-1"]}
    [:h3 {:class ["text-sm" "font-normal" "text-[#f6fefd]"]}
     title]
    [:p {:class ["text-xs" "text-[#9aa3a4]"]}
     description]]
   [:div {:class ["flex" "items-center" "gap-2"]}
    [:input {:id input-id
             :type "text"
             :inputmode "decimal"
             :placeholder "0.0"
             :value amount
             :class ["h-10"
                     "w-full"
                     "rounded-lg"
                     "border"
                     "border-[#1b2429]"
                     "bg-[#0f1a1f]"
                     "px-3"
                     "text-sm"
                     "text-[#f6fefd]"
                     "focus:outline-none"
                     "focus:ring-0"
                     "focus:ring-offset-0"]
             :on {:input [on-change]}}]
    [:button {:type "button"
              :class ["h-10"
                      "rounded-lg"
                      "border"
                      "border-[#1b2429]"
                      "px-2.5"
                      "text-xs"
                      "font-normal"
                      "text-[#9aa3a4]"
                      "transition-colors"
                      "hover:bg-[#1d2a30]"
                      "focus:outline-none"
                      "focus:ring-0"
                      "focus:ring-offset-0"]
              :on {:click [on-max]}}
     "Max"]]
   [:button {:type "button"
             :class ["h-10"
                     "w-full"
                     "rounded-lg"
                     "border"
                     "border-[#2f7f73]/70"
                     "bg-[#0e4d46]"
                     "text-xs"
                     "font-normal"
                     "text-[#97fce4]"
                     "transition-colors"
                     "hover:bg-[#126158]"
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
            :class (into ["border-b"
                          "px-0"
                          "mr-4"
                          "text-xs"
                          "font-normal"
                          "leading-[34px]"
                          "transition-colors"
                          "focus:outline-none"
                          "focus:ring-0"
                          "focus:ring-offset-0"]
                         (if active?
                           ["border-[#303030]" "text-[#f6fefd]"]
                           ["border-[#303030]" "text-[#949e9c]" "hover:text-[#c5d0ce]"]))
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
           commission]}
   selected-validator]
  (let [selected? (and (seq validator)
                       (= validator selected-validator))]
    [:tr {:class (into ["border-b"
                        "border-[#1b2429]"
                        "text-xs"
                        "cursor-pointer"
                        "transition-colors"
                        "hover:bg-[#1d2a30]"]
                       (when selected?
                         ["bg-[#1a2c31]"]))
          :on {:click [[:actions/select-staking-validator validator]]}
        :data-role "staking-validator-row"}
     [:td {:class ["px-3" "py-2.5" "font-normal" "text-[#f6fefd]"]}
      name]
     [:td {:class ["px-3" "py-2.5" "text-[#9aa3a4]" "max-w-[260px]" "truncate"]}
      (or description "--")]
     [:td {:class ["px-3" "py-2.5" "num" "text-[#f6fefd]"]} (format-table-hype stake)]
     [:td {:class ["px-3" "py-2.5" "num" "text-[#f6fefd]"]}
      (if (pos? (or your-stake 0))
        (format-table-hype your-stake)
        "-")]
     [:td {:class ["px-3" "py-2.5" "num" "text-[#f6fefd]"]} (format-percent uptime-fraction)]
     [:td {:class ["px-3" "py-2.5" "num" "text-[#f6fefd]"]} (format-percent predicted-apr)]
     [:td {:class ["px-3" "py-2.5"]} (status-pill status)]
     [:td {:class ["px-3" "py-2.5" "num" "text-[#f6fefd]"]} (format-percent commission)]]))

(defn- history-table
  [rows columns empty-text row-render]
  [:div {:class ["overflow-x-auto" "rounded-[10px]" "border" "border-[#1b2429]"]}
   [:table {:class ["min-w-full" "bg-[#0f1a1f]"]}
    [:thead
     [:tr {:class ["text-xs" "text-[#949e9c]"]}
      (for [column columns]
        ^{:key column}
        [:th {:class ["px-3" "py-2.5" "text-left" "font-normal"]}
         column])]]
    [:tbody
     (if (seq rows)
       (map row-render rows)
       [:tr
        [:td {:col-span (count columns)
              :class ["px-3" "py-6" "text-center" "text-sm" "text-[#949e9c]"]}
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
                   "gap-2"
                   "app-shell-gutter"
                   "pt-3"
                   "pb-10"]
           :data-parity-id "staking-root"}
     [:div {:class ["bg-[#04251f]"
                    "px-4"
                    "py-3"
                    "space-y-3"
                    "rounded-[10px]"]}
      [:div {:class ["flex" "flex-wrap" "items-start" "justify-between" "gap-3"]}
       [:div {:class ["space-y-2" "max-w-[980px]"]}
        [:h1 {:class ["text-[24px]" "md:text-[34px]" "font-normal" "leading-[1.08]" "text-[#ffffff]"]}
         "Staking"]
        [:p {:class ["text-sm" "leading-[15px]" "text-[#f6fefd]" "max-w-[1200px]"]}
         "The Hyperliquid L1 is a proof-of-stake blockchain where stakers delegate the native token HYPE to validators to earn staking rewards. Stakers only receive rewards when the validator successfully participates in consensus, so stakers should only delegate to reputable and trusted validators."]
        (when (seq effective-address)
          [:p {:class ["text-xs" "text-[#878c8f]" "num"]}
           (str "Account: " effective-address)])]
       (when-not connected?
         [:button {:type "button"
                   :class ["h-9"
                           "min-w-[90px]"
                           "rounded-lg"
                           "bg-[#50d2c1]"
                           "px-4"
                           "text-xs"
                           "font-normal"
                           "text-[#04060c]"
                           "transition-colors"
                           "hover:bg-[#72e5d7]"]
                   :data-role "staking-establish-connection"
                   :on {:click [[:actions/connect-wallet]]}}
          "Connect"])]

      [:div {:class ["grid" "gap-2" "lg:grid-cols-[340px_minmax(0,1fr)]"]}
       [:div {:class ["grid" "gap-2"]}
        (summary-card "Total Staked" (format-summary-hype (:total-staked summary)) "staking-summary-total")
        (summary-card "Your Stake" (format-summary-hype (:your-stake summary)) "staking-summary-user")]
       [:div {:class ["rounded-[10px]" "border" "border-[#1b2429]" "bg-[#0f1a1f]" "p-4" "space-y-2"]
              :data-role "staking-balance-panel"}
        [:div {:class ["text-sm" "leading-[15px]" "font-normal" "text-[#878c8f]"]}
         "Staking Balance"]
        (key-value-row "Available to Transfer to Staking Balance"
                       (format-balance-hype (:available-transfer balances)))
        (key-value-row "Available to Stake" (format-balance-hype (:available-stake balances)))
        (key-value-row "Total Staked" (format-balance-hype (:total-staked balances)))
        (key-value-row "Pending Transfers to Spot Balance"
                       (format-balance-hype (:pending-withdrawals balances)))]]]

     (when connected?
       [:div {:class ["space-y-2"]}
        [:div {:class ["grid" "gap-2" "lg:grid-cols-2"]}
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
                  :class ["text-xs" "uppercase" "tracking-[0.08em]" "text-[#9aa3a4]"]}
          "Selected Validator"]
         [:input {:id "staking-selected-validator"
                  :type "text"
                  :value selected-validator
                  :placeholder "0x..."
                  :class ["h-10"
                          "w-full"
                          "max-w-xl"
                          "rounded-lg"
                          "border"
                          "border-[#1b2429]"
                          "bg-[#0f1a1f]"
                          "px-3"
                          "text-sm"
                          "text-[#f6fefd]"
                          "focus:outline-none"
                          "focus:ring-0"
                          "focus:ring-offset-0"]
                  :on {:input [[:actions/set-staking-form-field :selected-validator [:event.target/value]]]}}]

        (when (and (empty? selected-validator)
                   (seq delegations))
          [:p {:class ["text-xs" "text-[#9aa3a4]"]}
           "Select a validator from the table to prefill stake/unstake actions."])]])

     [:div {:class ["rounded-[10px]" "border" "border-[#1b2429]" "bg-[#0f1a1f]" "overflow-hidden"]}
      [:div {:class ["flex" "flex-wrap" "items-end" "justify-between" "gap-2" "px-3" "pt-2" "pb-0"]}
       [:div {:class ["flex" "flex-wrap" "items-center" "gap-2"]}
        (for [{:keys [value label]} tabs]
          ^{:key value}
          (tab-button (= value active-tab)
                      label
                      [:actions/set-staking-active-tab value]))]
       (when (= :validator-performance active-tab)
         [:div {:class ["relative"]
                :data-role "staking-timeframe-toggle"}
          [:select {:value (name validator-timeframe)
                    :class ["h-8"
                            "rounded-lg"
                            "border"
                            "border-[#1b2429]"
                            "bg-[#0f1a1f]"
                            "pl-2.5"
                            "pr-7"
                            "text-xs"
                            "font-normal"
                            "text-[#f6fefd]"
                            "appearance-none"
                            "focus:outline-none"
                            "focus:ring-0"
                            "focus:ring-offset-0"]
                    :on {:change [[:actions/set-staking-validator-timeframe [:event.target/value]]]}}
           (for [{:keys [value label]} timeframe-options]
             ^{:key value}
             [:option {:value (name value)}
              label])]
          [:span {:class ["pointer-events-none"
                          "absolute"
                          "right-2.5"
                          "top-1/2"
                          "-translate-y-1/2"
                          "text-xs"
                          "text-[#949e9c]"]}
           "▾"]])]

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
             (format-balance-hype total-amount)]]))

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
             (format-balance-hype amount)]
            [:td {:class ["px-3" "py-2.5"]}
             (or status "--")]
            [:td {:class ["px-3" "py-2.5" "num"]}
             (or (some-> hash (subs 0 (min 10 (count hash)))) "--")]]))

        ;; Default: validator performance table
        [:div {:class ["overflow-x-auto"]}
         [:table {:class ["min-w-full" "bg-[#0f1a1f]"]
                  :data-role "staking-validator-table"}
          [:thead
           [:tr {:class ["border-b" "border-[#1b2429]" "text-xs" "text-[#949e9c]"]}
            [:th {:class ["px-3" "py-2.5" "text-left" "font-normal"]} "Name"]
            [:th {:class ["px-3" "py-2.5" "text-left" "font-normal"]} "Description"]
            [:th {:class ["px-3" "py-2.5" "text-left" "font-normal"]} "Stake"]
            [:th {:class ["px-3" "py-2.5" "text-left" "font-normal"]} "Your Stake"]
            [:th {:class ["px-3" "py-2.5" "text-left" "font-normal"]} "Uptime"]
            [:th {:class ["px-3" "py-2.5" "text-left" "font-normal"]} "Est. APR"]
            [:th {:class ["px-3" "py-2.5" "text-left" "font-normal"]} "Status"]
            [:th {:class ["px-3" "py-2.5" "text-left" "font-normal"]} "Commission"]]]
          [:tbody
           (if (seq validators)
             (for [row validators]
               ^{:key (:validator row)}
               (validator-row row selected-validator))
             [:tr
               [:td {:col-span 8
                    :class ["px-3" "py-6" "text-center" "text-sm" "text-[#949e9c]"]}
               (if loading?
                 "Loading validators..."
                 "No validator data available.")]])]]])]

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
        error])]))
