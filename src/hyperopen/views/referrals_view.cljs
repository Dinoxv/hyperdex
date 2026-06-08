(ns hyperopen.views.referrals-view
  (:require [hyperopen.referrals.vm :as referrals-vm]
            [hyperopen.views.referrals.modals :as referrals-modals]
            [hyperopen.wallet.core :as wallet]))

(defn- button
  [{:keys [label role action primary? disabled?]}]
  [:button {:type "button"
            :class (into ["h-9"
                          "rounded-lg"
                          "border"
                          "px-3"
                          "text-sm"
                          "transition-colors"
                          "disabled:cursor-not-allowed"
                          "disabled:opacity-50"]
                         (if primary?
                           ["border-[#50d2c1]"
                            "bg-[#50d2c1]"
                            "text-[#041914]"
                            "hover:bg-[#6de3d5]"]
                           ["border-[#2f7f73]"
                            "bg-[#061b1f]"
                            "text-[#97fce4]"
                            "hover:bg-[#0b262c]"]))
            :disabled disabled?
            :data-role role
            :on {:click [action]}}
   label])

(defn- stat-card
  [label value role]
  [:div {:class ["rounded-lg"
                 "border"
                 "border-[#1b2429]"
                 "bg-[#0f1a1f]"
                 "p-4"]
         :data-role role}
   [:div {:class ["text-xs" "uppercase" "tracking-[0.08em]" "text-[#878c8f]"]}
    label]
   [:div {:class ["mt-2" "text-2xl" "text-[#f6fefd]" "num"]}
    value]])

(defn- status-banner
  [{:keys [connected?
           loading?
           error
           last-error
           mutation-blocked-message
           owner
           owner-label]}]
  (cond
    error
    [:div {:class ["rounded-lg" "border" "border-[#7a2836]" "bg-[#2b1118]" "px-3" "py-2" "text-sm" "text-[#ff9db2]"]
           :data-role "referrals-error"}
     [:div error]
     (when owner
       (button {:label "Retry"
                :role "referrals-retry"
                :action [:actions/load-referrals-route "/referrals"]}))]

    last-error
    [:div {:class ["rounded-lg" "border" "border-[#7a2836]" "bg-[#2b1118]" "px-3" "py-2" "text-sm" "text-[#ff9db2]"]
           :data-role "referrals-form-error"}
     last-error]

    mutation-blocked-message
    [:div {:class ["rounded-lg" "border" "border-[#725f1d]" "bg-[#241f0b]" "px-3" "py-2" "text-sm" "text-[#ffe08a]"]
           :data-role "referrals-read-only"}
     mutation-blocked-message
     (when owner
       [:span
        " Showing referral state for "
        [:span {:class ["num" "text-[#f6fefd]"]} owner-label]])]

    loading?
    [:div {:class ["rounded-lg" "border" "border-[#224247]" "bg-[#0b2025]" "px-3" "py-2" "text-sm" "text-[#b7d3d0]"]
           :data-role "referrals-loading"}
     "Loading referral state..."]

    connected?
    [:div {:class ["rounded-lg" "border" "border-[#1b3d40]" "bg-[#071d22]" "px-3" "py-2" "text-sm" "text-[#b7d3d0]"]
           :data-role "referrals-owner"}
     "Showing master account referral state for "
     [:span {:class ["num" "text-[#f6fefd]"]} owner-label]]

    :else
    [:div {:class ["rounded-lg" "border" "border-[#224247]" "bg-[#0b2025]" "px-3" "py-2" "text-sm" "text-[#b7d3d0]"]
           :data-role "referrals-disconnected"}
     "Connect your wallet to load referral status."]))

(defn- action-card
  [{:keys [role title body value value-role secondary-value secondary-value-role
           action-label action disabled?]}]
  [:div {:class ["rounded-lg"
                 "border"
                 "border-[#1b2429]"
                 "bg-[#0f1a1f]"
                 "p-4"
                 "space-y-3"]
         :data-role (str role "-panel")}
   [:div {:class ["space-y-1"]}
    [:div {:class ["text-xs" "uppercase" "tracking-[0.08em]" "text-[#878c8f]"]}
     title]
    (when body
      [:div {:class ["text-sm" "leading-5" "text-[#b7d3d0]"]}
       body])]
   (when value
     [:div {:class ["text-xl" "text-[#f6fefd]" "num"]
            :data-role value-role}
      value])
   (when secondary-value
     [:div {:class ["break-all" "text-sm" "text-[#b7d3d0]"]
            :data-role secondary-value-role}
      secondary-value])
   (button {:label action-label
            :role role
            :primary? true
            :disabled? disabled?
            :action action})])

(defn- hero
  [view-state]
  (let [{:keys [connected?
                mutation-blocked-message
                submitting?
                stage
                stage-label
                referral-code
                join-link]} view-state
        mutation-disabled? (or (not connected?)
                               (seq mutation-blocked-message))
        create-disabled? (or mutation-disabled?
                             (= :need-to-trade stage))]
    [:div {:class ["bg-[#04251f]" "px-4" "py-4" "space-y-4" "rounded-lg"]
           :data-role "referrals-hero"}
     [:div {:class ["flex" "flex-wrap" "items-start" "justify-between" "gap-3"]}
      [:div {:class ["space-y-2" "max-w-[900px]"]}
       [:h1 {:class ["text-[24px]" "md:text-[34px]" "font-normal" "leading-[1.08]" "text-[#ffffff]"]}
        "Referrals"]
       [:p {:class ["text-sm" "leading-5" "text-[#d3f5ef]" "max-w-[880px]"]}
        "Refer users, enter a referral code, and claim referral rewards from Hyperliquid."]
       [:a {:href "https://hyperliquid.gitbook.io/hyperliquid-docs/referrals"
            :target "_blank"
            :rel "noreferrer"
            :class ["text-sm" "text-[#97fce4]" "hover:text-[#d2fff7]"]
            :data-role "referrals-learn-more"}
        "Learn more"]]
      [:div {:class ["rounded-lg" "border" "border-[#1b3d40]" "bg-[#071d22]" "px-3" "py-2" "text-sm" "text-[#d3f5ef]"]
             :data-role "referrals-stage"}
       stage-label]]
     [:div {:class ["grid" "gap-3" "lg:grid-cols-2"]}
      (action-card {:role "referrals-open-enter-code"
                    :title "Enter Referral Code"
                    :body "Apply another trader's code to this master account."
                    :action-label "Enter Code"
                    :disabled? (or mutation-disabled?
                                   (= :set-referrer submitting?))
                    :action [:actions/open-referrals-modal :enter-code]})
      (if (= :ready stage)
        (action-card {:role "referrals-open-share-code"
                      :title "Share Code"
                      :body "Send your Hyperopen join link to referred traders."
                      :value (or referral-code "Unavailable")
                      :value-role "referrals-own-code"
                      :secondary-value join-link
                      :secondary-value-role "referrals-join-link"
                      :action-label "Share Code"
                      :disabled? mutation-disabled?
                      :action [:actions/open-referrals-modal :share-code]})
        (action-card {:role "referrals-open-create-code"
                      :title "Create Referral Code"
                      :body (if (= :need-to-trade stage)
                              stage-label
                              "Choose the code traders will use when joining.")
                      :action-label (if (= :register-referrer submitting?)
                                      "Creating..."
                                      "Create Code")
                      :disabled? (or create-disabled?
                                     (= :register-referrer submitting?))
                      :action [:actions/open-referrals-modal :create-code]}))]]))

(defn- rewards-panel
  [{:keys [rewards connected? mutation-blocked-message submitting?]}]
  (let [claimable (:claimable rewards)
        disabled? (or (not connected?)
                      (seq mutation-blocked-message)
                      (zero? claimable)
                      (= :claim-rewards submitting?))]
    [:div {:class ["rounded-lg" "border" "border-[#1b2429]" "bg-[#0f1a1f]" "p-4" "space-y-3"]
           :data-role "referrals-rewards-panel"}
     [:div {:class ["flex" "flex-wrap" "items-center" "justify-between" "gap-3"]}
      [:div]
      (button {:label (if (= :claim-rewards submitting?) "Claiming..." "Claim Rewards")
               :role "referrals-open-claim-rewards"
               :primary? true
               :disabled? disabled?
               :action [:actions/open-referrals-modal :claim-rewards]})]
     (if (seq (:rows rewards))
       [:div {:class ["grid" "gap-2"]}
        (for [{:keys [token unclaimed claimed]} (:rows rewards)]
          ^{:key token}
          [:div {:class ["flex" "items-center" "justify-between" "gap-3" "text-sm"]
                 :data-role "referrals-reward-token-row"}
           [:span {:class ["text-[#b7d3d0]"]} token]
           [:span {:class ["num" "text-[#f6fefd]"]}
            "Claimable " unclaimed " | Claimed " claimed]])]
      [:div {:class ["text-sm" "text-[#878c8f]"]
              :data-role "referrals-no-rewards"}
        "No claimable rewards."])]))

(defn- tab-button
  [active? label tab]
  [:button {:type "button"
            :class (into ["border-b"
                          "px-0"
                          "mr-4"
                          "text-xs"
                          "leading-[34px]"
                          "transition-colors"]
                         (if active?
                           ["border-[#50d2c1]" "text-[#f6fefd]"]
                           ["border-[#303030]" "text-[#949e9c]" "hover:text-[#c5d0ce]"]))
            :data-role (str "referrals-tab-" (name tab))
            :on {:click [[:actions/set-referrals-active-tab tab]]}}
   label])

(defn- referral-row
  [row idx]
  (let [address (or (:user row) (get row "user") (:address row) (get row "address"))
        date-joined (or (:time row) (:timestamp row) (:joinedAt row)
                        (:date-joined row) (get row "time")
                        (get row "timestamp") (get row "joinedAt")
                        (get row "date-joined") "-")
        volume (or (:cumVlm row) (:cum-vlm row) (get row "cumVlm") (get row "cum-vlm") "0")
        fees (or (:cumFees row) (:cum-fees row) (get row "cumFees") (get row "cum-fees") "0")
        rewards (or (:cumReward row) (:cum-reward row) (get row "cumReward") (get row "cum-reward") "0")]
    [:div {:class ["grid" "min-w-[760px]" "grid-cols-[minmax(0,1.2fr)_120px_120px_100px_110px]" "gap-3" "px-3" "py-2" "text-sm"]
           :data-role "referrals-row"}
     [:span {:class ["truncate" "num"]} (or (wallet/short-addr address) address (str "Trader " (inc idx)))]
     [:span {:class ["num" "text-right"]} (str date-joined)]
     [:span {:class ["num" "text-right"]} (str volume)]
     [:span {:class ["num" "text-right"]} (str fees)]
     [:span {:class ["num" "text-right"]} (str rewards)]]))

(defn- legacy-row
  [row idx]
  (let [time (or (:time row) (:timestamp row) (get row "time") (get row "timestamp"))
        user-volume (or (:userVlm row) (:user-vlm row) (get row "userVlm") (get row "user-vlm") "0")
        referral-volume (or (:referralVlm row) (:referral-vlm row)
                            (get row "referralVlm") (get row "referral-vlm") "0")
        rewards (or (:totalRewards row) (:total-rewards row) (:amount row)
                    (get row "totalRewards") (get row "total-rewards")
                    (get row "amount") "0")]
    [:div {:class ["grid" "min-w-[680px]" "grid-cols-[minmax(0,1fr)_140px_160px_160px]" "gap-3" "px-3" "py-2" "text-sm"]
           :data-role "referrals-legacy-row"}
     [:span {:class ["truncate" "num"]} (or time (str "Reward " (inc idx)))]
     [:span {:class ["num" "text-right"]} (str user-volume)]
     [:span {:class ["num" "text-right"]} (str referral-volume)]
     [:span {:class ["num" "text-right"]} (str rewards)]]))

(defn- table-panel
  [{:keys [active-tab referral-rows legacy-rows]}]
  [:div {:class ["rounded-lg" "border" "border-[#1b2429]" "bg-[#0f1a1f]" "overflow-hidden"]
         :data-role "referrals-table-panel"}
   [:div {:class ["px-3" "pt-2"]}
    (tab-button (= :referrals active-tab) "Referrals" :referrals)
    (tab-button (= :legacy-reward-history active-tab) "Legacy Reward History" :legacy-reward-history)]
   (if (= :legacy-reward-history active-tab)
     (if (seq legacy-rows)
       [:div {:class ["overflow-x-auto"]
              :data-role "referrals-legacy-table"}
        [:div {:class ["grid" "min-w-[680px]" "grid-cols-[minmax(0,1fr)_140px_160px_160px]" "gap-3" "px-3" "py-2" "text-xs" "uppercase" "tracking-[0.08em]" "text-[#878c8f]"]}
         [:span "Date"]
         [:span {:class ["text-right"]} "Your Volume"]
         [:span {:class ["text-right"]} "Your Referral Volume"]
         [:span {:class ["text-right"]} "Total Rewards Earned"]]
        (map-indexed legacy-row legacy-rows)]
       [:div {:class ["px-3" "py-8" "text-center" "text-sm" "text-[#878c8f]"]
              :data-role "referrals-legacy-empty"}
        "No rewards earned"])
     (if (seq referral-rows)
       [:div {:class ["overflow-x-auto"]
              :data-role "referrals-table"}
        [:div {:class ["grid" "min-w-[760px]" "grid-cols-[minmax(0,1.2fr)_120px_120px_100px_110px]" "gap-3" "px-3" "py-2" "text-xs" "uppercase" "tracking-[0.08em]" "text-[#878c8f]"]}
         [:span "Address"]
         [:span {:class ["text-right"]} "Date Joined"]
         [:span {:class ["text-right"]} "Total Volume"]
         [:span {:class ["text-right"]} "Fees Paid"]
         [:span {:class ["text-right"]} "Your Rewards"]]
        (map-indexed referral-row referral-rows)]
       [:div {:class ["px-3" "py-8" "text-center" "text-sm" "text-[#878c8f]"]
              :data-role "referrals-empty"}
        "No referrals yet"]))])

(defn referrals-view
  [state]
  (let [view-state (referrals-vm/referrals-vm state)
        {:keys [traders-referred-label
                rewards]} view-state]
    [:div {:class ["flex"
                   "flex-1"
                   "min-h-0"
                   "w-full"
                   "overflow-hidden"
                   "flex-col"
                   "app-shell-gutter"
                   "pt-3"]
           :data-parity-id "referrals-root"}
     [:div {:class ["w-full"
                    "h-full"
                    "min-h-0"
                    "scrollbar-hide"
                    "overflow-y-auto"
                    "flex"
                    "flex-col"
                    "gap-3"
                    "pb-16"]}
      (hero view-state)
      (status-banner view-state)
      [:div {:class ["grid" "gap-2" "md:grid-cols-3"]}
       (stat-card "Traders Referred" traders-referred-label "referrals-stat-traders")
       (stat-card "Rewards Earned" (:earned-label rewards) "referrals-stat-rewards")
       (stat-card "Claimable Rewards" (:claimable-label rewards) "referrals-stat-claimable")]
      (rewards-panel view-state)
      (table-panel view-state)]
     (referrals-modals/referrals-modal view-state)]))

(def route-view referrals-view)
