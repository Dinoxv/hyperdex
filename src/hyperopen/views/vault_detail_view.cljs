(ns hyperopen.views.vault-detail-view
  (:require [hyperopen.utils.formatting :as fmt]
            [hyperopen.wallet.core :as wallet]
            [hyperopen.views.vaults.detail-vm :as detail-vm]))

(defn- format-currency
  [value]
  (or (fmt/format-currency value)
      "$0.00"))

(defn- format-percent
  [value]
  (let [n (if (number? value) value 0)
        sign (cond
               (pos? n) "+"
               (neg? n) "-"
               :else "")]
    (str sign (.toFixed (js/Math.abs n) 2) "%")))

(defn- detail-tab-button [{:keys [value label]} selected-tab]
  [:button {:type "button"
            :class (into ["rounded-md"
                          "px-2.5"
                          "py-1.5"
                          "text-xs"
                          "font-medium"
                          "transition-colors"]
                         (if (= value selected-tab)
                           ["bg-base-300" "text-trading-text"]
                           ["text-trading-text-secondary" "hover:bg-base-200" "hover:text-trading-text"]))
            :on {:click [[:actions/set-vault-detail-tab value]]}}
   label])

(defn- metric-card [label value]
  [:div {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "px-3" "py-2.5"]}
   [:div {:class ["text-xs" "uppercase" "tracking-wide" "text-trading-text-secondary"]} label]
   [:div {:class ["mt-1" "num" "text-xl" "font-semibold" "text-trading-text"]} value]])

(defn- render-about-panel [{:keys [description leader followers leader-commission relationship]}]
  [:div {:class ["space-y-2" "text-sm" "text-trading-text-secondary"]}
   [:p (if (seq description) description "No vault description available.")]
   [:div {:class ["grid" "grid-cols-2" "gap-2"]}
    [:div [:span {:class ["text-trading-text-secondary"]} "Leader "] [:span {:class ["num" "text-trading-text"]} (wallet/short-addr leader)]]
    [:div [:span {:class ["text-trading-text-secondary"]} "Followers "] [:span {:class ["num" "text-trading-text"]} followers]]
    [:div [:span {:class ["text-trading-text-secondary"]} "Profit share "] [:span {:class ["num" "text-trading-text"]} (format-percent leader-commission)]]
    [:div [:span {:class ["text-trading-text-secondary"]} "Relationship "] [:span {:class ["text-trading-text"]} (name (:type relationship))]]]])

(defn- render-vault-performance-panel [{:keys [snapshot]}]
  [:div {:class ["grid" "grid-cols-2" "gap-2" "text-sm"]}
   [:div [:span {:class ["text-trading-text-secondary"]} "24H "] [:span {:class ["num" "text-trading-text"]} (format-percent (:day snapshot))]]
   [:div [:span {:class ["text-trading-text-secondary"]} "7D "] [:span {:class ["num" "text-trading-text"]} (format-percent (:week snapshot))]]
   [:div [:span {:class ["text-trading-text-secondary"]} "30D "] [:span {:class ["num" "text-trading-text"]} (format-percent (:month snapshot))]]
   [:div [:span {:class ["text-trading-text-secondary"]} "All-time "] [:span {:class ["num" "text-trading-text"]} (format-percent (:all-time snapshot))]]])

(defn- render-your-performance-panel [metrics]
  [:div {:class ["space-y-2" "text-sm"]}
   [:div [:span {:class ["text-trading-text-secondary"]} "Your Deposits "] [:span {:class ["num" "text-trading-text"]} (format-currency (:your-deposit metrics))]]
   [:div [:span {:class ["text-trading-text-secondary"]} "All-time Earned "] [:span {:class ["num" "text-trading-text"]} (format-currency (:all-time-earned metrics))]]])

(defn- render-tab-panel [{:keys [selected-tab] :as vm}]
  (case selected-tab
    :vault-performance (render-vault-performance-panel vm)
    :your-performance (render-your-performance-panel (:metrics vm))
    (render-about-panel vm)))

(defn- relationship-links [{:keys [relationship]}]
  (case (:type relationship)
    :child
    (when-let [parent-address (:parent-address relationship)]
      [:div {:class ["text-xs" "text-trading-text-secondary"]}
       "Parent strategy: "
       [:button {:type "button"
                 :class ["num" "text-[#63e4c2]" "hover:underline"]
                 :on {:click [[:actions/navigate (str "/vaults/" parent-address)]]}}
        (wallet/short-addr parent-address)]])

    :parent
    (when-let [child-addresses (seq (:child-addresses relationship))]
      [:div {:class ["text-xs" "text-trading-text-secondary"]}
       "Child strategies: "
       (for [child-address child-addresses]
         ^{:key (str "child-vault-" child-address)}
         [:button {:type "button"
                   :class ["ml-1" "num" "text-[#63e4c2]" "hover:underline"]
                   :on {:click [[:actions/navigate (str "/vaults/" child-address)]]}}
          (wallet/short-addr child-address)])])

    nil))

(defn- activity-fill-row [{:keys [time-ms coin side size price]}]
  [:tr {:class ["border-b" "border-base-300/40" "text-xs" "text-trading-text"]}
   [:td {:class ["px-2.5" "py-1.5" "num"]} (or (fmt/format-local-time-hh-mm-ss time-ms) "—")]
   [:td {:class ["px-2.5" "py-1.5"]} (or coin "—")]
   [:td {:class ["px-2.5" "py-1.5"]} (or side "—")]
   [:td {:class ["px-2.5" "py-1.5" "num"]} (if (number? size) (.toFixed size 4) "—")]
   [:td {:class ["px-2.5" "py-1.5" "num"]} (if (number? price) (fmt/format-trade-price-plain price) "—")]])

(defn vault-detail-view
  [state]
  (let [{:keys [kind
                vault-address
                invalid-address?
                loading?
                error
                name
                leader
                relationship
                tabs
                selected-tab
                metrics
                chart
                activity-summary
                activity-fills] :as vm} (detail-vm/vault-detail-vm state)]
    [:div
     {:class ["w-full" "app-shell-gutter" "py-4" "space-y-4"]
      :data-parity-id "vault-detail-root"}
     [:div {:class ["flex" "items-center" "gap-3"]}
      [:button
       {:type "button"
        :class ["rounded-md" "border" "border-base-300" "bg-base-100" "px-2.5" "py-1.5" "text-xs" "text-trading-text-secondary" "hover:text-trading-text"]
        :on {:click [[:actions/navigate "/vaults"]]}}
       "Back to Vaults"]
      [:div {:class ["text-xs" "text-trading-text-secondary"]}
       (if (= kind :detail) "Vault Detail" "Vaults")]]

     (cond
       invalid-address?
       [:div {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4" "text-sm" "text-trading-text-secondary"]}
        "Invalid vault address."]

       (not= kind :detail)
       [:div {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4" "text-sm" "text-trading-text-secondary"]}
        "Select a vault to view details."]

       :else
       [:div {:class ["space-y-4"]}
        [:div {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4" "space-y-2"]}
         [:div {:class ["flex" "flex-wrap" "items-center" "justify-between" "gap-3"]}
          [:div
           [:h1 {:class ["text-xl" "font-semibold" "text-trading-text"]} name]
           [:div {:class ["mt-1" "num" "text-xs" "text-trading-text-secondary"]}
            vault-address]
           (relationship-links {:relationship relationship})]
          [:div {:class ["flex" "items-center" "gap-2"]}
           [:button
            {:type "button"
             :disabled true
             :class ["rounded-lg" "border" "border-base-300" "bg-base-100" "px-3" "py-2" "text-sm" "text-trading-text-secondary" "opacity-70" "cursor-not-allowed"]}
            "Deposit (Coming soon)"]
           [:button
            {:type "button"
             :disabled true
             :class ["rounded-lg" "border" "border-base-300" "bg-base-100" "px-3" "py-2" "text-sm" "text-trading-text-secondary" "opacity-70" "cursor-not-allowed"]}
            "Withdraw (Coming soon)"]]]
         (when loading?
           [:div {:class ["text-sm" "text-trading-text-secondary"]}
            "Loading vault details..."])
         (when error
           [:div {:class ["rounded-lg" "border" "border-red-500/40" "bg-red-900/20" "px-3" "py-2" "text-sm" "text-red-200"]}
            error])]

        [:div {:class ["grid" "gap-3" "sm:grid-cols-2" "xl:grid-cols-4"]}
         (metric-card "TVL" (format-currency (:tvl metrics)))
         (metric-card "Past Month Return" (format-percent (:past-month-return metrics)))
         (metric-card "Your Deposits" (format-currency (:your-deposit metrics)))
         (metric-card "All-time Earned" (format-currency (:all-time-earned metrics)))]

        [:div {:class ["grid" "gap-3" "xl:grid-cols-[minmax(0,1fr)_360px]"]}
         [:section {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-3" "space-y-3"]}
          [:div {:class ["flex" "items-center" "gap-1.5"]}
           (for [tab tabs]
             ^{:key (str "vault-detail-tab-" (name (:value tab)))}
             (detail-tab-button tab selected-tab))]
          (render-tab-panel vm)]

         [:section {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-3"]}
          [:div {:class ["flex" "items-center" "justify-between" "text-xs" "text-trading-text-secondary"]}
           [:span "Portfolio"]
           [:span (format-percent (:apr metrics))]]
          [:svg
           {:class ["mt-2" "w-full" "h-[220px]"]
            :viewBox (str "0 0 " (:width chart) " " (:height chart))
            :preserveAspectRatio "none"}
           [:line {:x1 0
                   :x2 (:width chart)
                   :y1 (:height chart)
                   :y2 (:height chart)
                   :stroke "rgba(150, 163, 175, 0.35)"
                   :stroke-width 1}]
           (when (seq (:path chart))
             [:path {:d (:path chart)
                     :fill "none"
                     :stroke "#5de2c0"
                     :stroke-width 2
                     :vector-effect "non-scaling-stroke"}])]]]

        [:section {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "overflow-hidden"]}
         [:div {:class ["flex" "items-center" "justify-between" "border-b" "border-base-300" "px-3" "py-2.5"]}
          [:h3 {:class ["text-sm" "font-semibold" "text-trading-text"]} "Account Activity"]
          [:div {:class ["text-xs" "text-trading-text-secondary"]}
           (str "Fills: " (:fill-count activity-summary)
                " · Orders: " (:open-order-count activity-summary)
                " · Positions: " (:position-count activity-summary))]]
         [:div {:class ["overflow-x-auto"]}
          [:table {:class ["w-full" "border-collapse"]}
           [:thead
            [:tr {:class ["border-b" "border-base-300" "bg-base-200/70" "text-xs" "uppercase" "tracking-wide" "text-trading-text-secondary"]}
             [:th {:class ["px-2.5" "py-2" "text-left"]} "Time"]
             [:th {:class ["px-2.5" "py-2" "text-left"]} "Coin"]
             [:th {:class ["px-2.5" "py-2" "text-left"]} "Side"]
             [:th {:class ["px-2.5" "py-2" "text-left"]} "Size"]
             [:th {:class ["px-2.5" "py-2" "text-left"]} "Price"]]]
           [:tbody
            (if (seq activity-fills)
              (for [fill-row activity-fills]
                ^{:key (str "vault-fill-" (:time-ms fill-row) "-" (:coin fill-row) "-" (:price fill-row))}
                (activity-fill-row fill-row))
              [:tr
               [:td {:col-span 5
                     :class ["px-2.5" "py-5" "text-center" "text-sm" "text-trading-text-secondary"]}
                "No recent fills."]])]]]]])]))
