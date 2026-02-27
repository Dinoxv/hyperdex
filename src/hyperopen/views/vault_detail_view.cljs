(ns hyperopen.views.vault-detail-view
  (:require [hyperopen.utils.formatting :as fmt]
            [hyperopen.wallet.core :as wallet]
            [hyperopen.views.vaults.detail-vm :as detail-vm]))

(defn- format-currency
  ([value]
   (format-currency value {:missing "—"}))
  ([value {:keys [missing]
           :or {missing "—"}}]
   (if (number? value)
     (or (fmt/format-currency value)
         "$0.00")
     missing)))

(defn- format-price
  [value]
  (if (number? value)
    (fmt/format-trade-price-plain value)
    "—"))

(defn- format-size
  [value]
  (if (number? value)
    (.toFixed value 4)
    "—"))

(defn- format-percent
  ([value]
   (format-percent value {:missing "—"}))
  ([value {:keys [missing
                  signed?
                  decimals]
           :or {missing "—"
                signed? true
                decimals 2}}]
   (if (number? value)
     (let [n value
           sign (cond
                  (and signed? (pos? n)) "+"
                  (neg? n) "-"
                  :else "")]
       (str sign (.toFixed (js/Math.abs n) decimals) "%"))
     missing)))

(defn- metric-card
  [{:keys [label value accent]}]
  [:div {:class ["rounded-xl"
                 "border"
                 "border-[#1a3a37]"
                 "bg-[#091a23]/88"
                 "px-3.5"
                 "py-3"
                 "shadow-[inset_0_0_0_1px_rgba(8,38,45,0.35)]"]}
   [:div {:class ["text-xs"
                  "uppercase"
                  "tracking-[0.08em]"
                  "text-[#8ba0a7]"]}
    label]
   [:div {:class (into ["mt-1.5"
                        "num"
                        "text-[20px]"
                        "sm:text-[24px]"
                        "lg:text-[44px]"
                        "leading-[1.08]"
                        "font-semibold"]
                       (case accent
                         :positive ["text-[#5de2c0]"]
                         :negative ["text-[#e59ca8]"]
                         ["text-trading-text"]))}
    value]])

(defn- format-activity-count [count]
  (cond
    (not (number? count)) nil
    (<= count 0) nil
    (>= count 100) "100+"
    :else (str count)))

(defn- detail-tab-button [{:keys [value label]} selected-tab]
  [:button {:type "button"
            :class (into ["border-b"
                          "px-3"
                          "py-2.5"
                          "text-sm"
                          "font-medium"
                          "transition-colors"]
                         (if (= value selected-tab)
                           ["border-[#66e3c5]" "text-trading-text"]
                           ["border-transparent" "text-[#8ea0a7]" "hover:text-trading-text"]))
            :on {:click [[:actions/set-vault-detail-tab value]]}}
   label])

(defn- activity-tab-button [{:keys [value label count]} selected-tab]
  [:button {:type "button"
            :class (into ["whitespace-nowrap"
                          "border-b"
                          "px-3"
                          "py-2"
                          "text-sm"
                          "transition-colors"]
                         (if (= value selected-tab)
                           ["border-[#66e3c5]" "text-trading-text"]
                           ["border-transparent" "text-[#8da0a6]" "hover:text-trading-text"]))
            :on {:click [[:actions/set-vault-detail-activity-tab value]]}}
   (if-let [count-label (format-activity-count count)]
     (str label " (" count-label ")")
     label)])

(defn- render-address-list [addresses]
  (when (seq addresses)
    [:div {:class ["space-y-1.5"]}
     [:div {:class ["text-[#8da0a6]"]}
      "This vault uses the following vaults as component strategies:"]
     (for [address addresses]
       ^{:key (str "component-vault-" address)}
       [:div {:class ["num" "break-all" "text-[#33d1b7]"]}
        address])]))

(defn- render-about-panel [{:keys [description leader relationship]}]
  (let [component-addresses (or (:child-addresses relationship) [])
        parent-address (:parent-address relationship)]
    [:div {:class ["space-y-3" "px-3" "pb-3" "pt-2" "text-sm"]}
     [:div
      [:div {:class ["text-[#8da0a6]"]} "Leader"]
      [:div {:class ["num" "font-medium" "text-trading-text"]}
       (or (wallet/short-addr leader) "—")]]
     [:div
      [:div {:class ["text-[#8da0a6]"]} "Description"]
      [:p {:class ["mt-1" "leading-5" "text-trading-text"]}
       (if (seq description)
         description
         "No vault description available.")]]
     (when parent-address
       [:div {:class ["text-[#8da0a6]"]}
        "Parent strategy: "
        [:button {:type "button"
                  :class ["num" "text-[#66e3c5]" "hover:underline"]
                  :on {:click [[:actions/navigate (str "/vaults/" parent-address)]]}}
         parent-address]])
     (render-address-list component-addresses)]))

(defn- render-vault-performance-panel [{:keys [snapshot]}]
  [:div {:class ["grid" "grid-cols-2" "gap-3" "px-3" "pb-3" "pt-2" "text-sm"]}
   [:div
    [:div {:class ["text-[#8da0a6]"]} "24H"]
    [:div {:class ["num" "font-medium" "text-trading-text"]} (format-percent (:day snapshot))]]
   [:div
    [:div {:class ["text-[#8da0a6]"]} "7D"]
    [:div {:class ["num" "font-medium" "text-trading-text"]} (format-percent (:week snapshot))]]
   [:div
    [:div {:class ["text-[#8da0a6]"]} "30D"]
    [:div {:class ["num" "font-medium" "text-trading-text"]} (format-percent (:month snapshot))]]
   [:div
    [:div {:class ["text-[#8da0a6]"]} "All-time"]
    [:div {:class ["num" "font-medium" "text-trading-text"]} (format-percent (:all-time snapshot))]]])

(defn- render-your-performance-panel [metrics]
  [:div {:class ["space-y-3" "px-3" "pb-3" "pt-2" "text-sm"]}
   [:div
    [:div {:class ["text-[#8da0a6]"]} "Your Deposits"]
    [:div {:class ["num" "font-medium" "text-trading-text"]}
     (format-currency (:your-deposit metrics))]]
   [:div
    [:div {:class ["text-[#8da0a6]"]} "All-time Earned"]
    [:div {:class ["num" "font-medium" "text-trading-text"]}
     (format-currency (:all-time-earned metrics))]]])

(defn- render-tab-panel [{:keys [selected-tab] :as vm}]
  (case selected-tab
    :vault-performance (render-vault-performance-panel vm)
    :your-performance (render-your-performance-panel (:metrics vm))
    (render-about-panel vm)))

(defn- relationship-links [{:keys [relationship]}]
  (case (:type relationship)
    :child
    (when-let [parent-address (:parent-address relationship)]
      [:div {:class ["mt-1.5" "text-xs" "text-[#8fa3aa]"]}
       "Parent strategy: "
       [:button {:type "button"
                 :class ["num" "text-[#66e3c5]" "hover:underline"]
                 :on {:click [[:actions/navigate (str "/vaults/" parent-address)]]}}
        (wallet/short-addr parent-address)]])

    nil))

(defn- table-header [labels]
  [:thead
   [:tr {:class ["border-b" "border-[#203b3b]" "bg-[#0a1920]" "text-xs" "uppercase" "tracking-[0.08em]" "text-[#8ca0a8]"]}
    (for [label labels]
      ^{:key (str "activity-header-" label)}
      [:th {:class ["px-3" "py-2" "text-left"]} label])]])

(defn- empty-table-row [col-span message]
  [:tr
   [:td {:col-span col-span
         :class ["px-3" "py-5" "text-center" "text-sm" "text-[#8da0a6"]}
    message]])

(defn- balances-table [rows]
  [:div {:class ["overflow-x-auto"]}
   [:table {:class ["w-full" "border-collapse"]}
    (table-header ["Coin" "Total" "Available"])
    [:tbody
     (if (seq rows)
       (for [{:keys [coin total available]} rows]
         ^{:key (str "balance-" coin "-" total)}
         [:tr {:class ["border-b" "border-[#1e3638]" "text-sm" "text-trading-text"]}
          [:td {:class ["px-3" "py-2"]} (or coin "—")]
          [:td {:class ["px-3" "py-2" "num"]} (format-size total)]
          [:td {:class ["px-3" "py-2" "num"]} (format-size available)]])
       (empty-table-row 3 "No balances available."))]]])

(defn- position-pnl-class [pnl]
  (cond
    (and (number? pnl) (pos? pnl)) "text-[#5de2c0]"
    (and (number? pnl) (neg? pnl)) "text-[#e59ca8]"
    :else "text-trading-text"))

(defn- positions-table [rows]
  [:div {:class ["overflow-x-auto"]}
   [:table {:class ["w-full" "border-collapse"]}
    (table-header ["Coin" "Size" "Position Value" "Entry Price" "Mark Price" "PNL (ROE %)" "Liq. Price" "Margin" "Funding"])
    [:tbody
     (if (seq rows)
       (for [{:keys [coin leverage size position-value entry-price mark-price pnl roe liq-price margin funding]} rows]
         ^{:key (str "position-" coin "-" size "-" entry-price)}
         [:tr {:class ["border-b" "border-[#1e3638]" "text-sm" "text-trading-text"]}
          [:td {:class ["px-3" "py-2"]}
           (str (or coin "—")
                (when (number? leverage)
                  (str "  " leverage "x")))]
          [:td {:class ["px-3" "py-2" "num"]} (format-size size)]
          [:td {:class ["px-3" "py-2" "num"]}
           (if (number? position-value)
             (str (fmt/format-currency position-value) " USDC")
             "—")]
          [:td {:class ["px-3" "py-2" "num"]} (format-price entry-price)]
          [:td {:class ["px-3" "py-2" "num"]} (format-price mark-price)]
          [:td {:class ["px-3" "py-2" "num" (position-pnl-class pnl)]}
           (if (number? pnl)
             (str (format-currency pnl {:missing "—"}) " (" (format-percent roe) ")")
             "—")]
          [:td {:class ["px-3" "py-2" "num"]} (format-price liq-price)]
          [:td {:class ["px-3" "py-2" "num"]}
           (if (number? margin)
             (str (format-currency margin) " (Cross)")
             "—")]
          [:td {:class ["px-3" "py-2" "num" (position-pnl-class funding)]}
           (format-currency funding)]])
       (empty-table-row 9 "No active positions."))]]])

(defn- open-orders-table [rows]
  [:div {:class ["overflow-x-auto"]}
   [:table {:class ["w-full" "border-collapse"]}
    (table-header ["Time" "Coin" "Side" "Size" "Price" "Trigger"])
    [:tbody
     (if (seq rows)
       (for [{:keys [time-ms coin side size price trigger-price]} rows]
         ^{:key (str "open-order-" time-ms "-" coin "-" size "-" price)}
         [:tr {:class ["border-b" "border-[#1e3638]" "text-sm" "text-trading-text"]}
          [:td {:class ["px-3" "py-2" "num"]} (or (fmt/format-local-time-hh-mm-ss time-ms) "—")]
          [:td {:class ["px-3" "py-2"]} (or coin "—")]
          [:td {:class ["px-3" "py-2"]} (or side "—")]
          [:td {:class ["px-3" "py-2" "num"]} (format-size size)]
          [:td {:class ["px-3" "py-2" "num"]} (format-price price)]
          [:td {:class ["px-3" "py-2" "num"]} (format-price trigger-price)]])
       (empty-table-row 6 "No open orders."))]]])

(defn- fills-table [rows]
  [:div {:class ["overflow-x-auto"]}
   [:table {:class ["w-full" "border-collapse"]}
    (table-header ["Time" "Coin" "Side" "Size" "Price" "Closed PNL"])
    [:tbody
     (if (seq rows)
       (for [{:keys [time-ms coin side size price closed-pnl]} rows]
         ^{:key (str "fill-" time-ms "-" coin "-" size "-" price)}
         [:tr {:class ["border-b" "border-[#1e3638]" "text-sm" "text-trading-text"]}
          [:td {:class ["px-3" "py-2" "num"]} (or (fmt/format-local-time-hh-mm-ss time-ms) "—")]
          [:td {:class ["px-3" "py-2"]} (or coin "—")]
          [:td {:class ["px-3" "py-2"]} (or side "—")]
          [:td {:class ["px-3" "py-2" "num"]} (format-size size)]
          [:td {:class ["px-3" "py-2" "num"]} (format-price price)]
          [:td {:class ["px-3" "py-2" "num" (position-pnl-class closed-pnl)]}
           (format-currency closed-pnl)]])
       (empty-table-row 6 "No recent fills."))]]])

(defn- activity-panel [{:keys [selected-activity-tab
                               activity-tabs
                               activity-balances
                               activity-positions
                               activity-open-orders
                               activity-fills]}]
  [:section {:class ["rounded-2xl"
                     "border"
                     "border-[#1a3737]"
                     "bg-[#071820]"
                     "overflow-hidden"]}
   [:div {:class ["flex" "items-center" "justify-between" "border-b" "border-[#1f3b3c]" "gap-2" "pr-3"]}
    [:div {:class ["min-w-0" "overflow-x-auto"]}
     [:div {:class ["flex" "min-w-max" "items-center"]}
      (for [tab activity-tabs]
        ^{:key (str "activity-tab-" (name (:value tab)))}
        (activity-tab-button tab selected-activity-tab))]]
    [:button {:type "button"
              :disabled true
              :class ["hidden"
                      "md:flex"
                      "items-center"
                      "gap-1"
                      "text-xs"
                      "text-[#8fa2aa]"
                      "cursor-not-allowed"]}
     "Filter"
     [:span "⌄"]]]
   (case selected-activity-tab
     :balances (balances-table activity-balances)
     :positions (positions-table activity-positions)
     :open-orders (open-orders-table activity-open-orders)
     :trade-history (fills-table activity-fills)
     [:div {:class ["px-4" "py-8" "text-sm" "text-[#8ea2aa]"]}
      "This activity stream is not available yet for vaults."])])

(defn vault-detail-view
  [state]
  (let [{:keys [kind
                vault-address
                invalid-address?
                loading?
                error
                relationship
                tabs
                selected-tab
                metrics
                chart] :as vm} (detail-vm/vault-detail-vm state)
        vault-name (:name vm)
        month-return (:past-month-return metrics)
        month-return-accent (cond
                              (and (number? month-return) (pos? month-return)) :positive
                              (and (number? month-return) (neg? month-return)) :negative
                              :else nil)]
    [:div
     {:class ["w-full" "app-shell-gutter" "py-4" "space-y-4"]
      :data-parity-id "vault-detail-root"}
     (cond
       invalid-address?
       [:div {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4" "text-sm" "text-trading-text-secondary"]}
        "Invalid vault address."]

       (not= kind :detail)
       [:div {:class ["rounded-xl" "border" "border-base-300" "bg-base-100/95" "p-4" "text-sm" "text-trading-text-secondary"]}
        "Select a vault to view details."]

       :else
       [:div {:class ["space-y-4"]}
        [:section {:class ["rounded-2xl"
                           "border"
                           "border-[#19423e]"
                           "px-4"
                           "py-4"
                           "lg:px-6"
                           "bg-[radial-gradient(circle_at_82%_18%,rgba(41,186,147,0.20),transparent_42%),linear-gradient(180deg,#06382f_0%,#082029_56%,#051721_100%)]"]}
         [:div {:class ["flex" "flex-col" "gap-3" "lg:flex-row" "lg:items-start" "lg:justify-between"]}
          [:div {:class ["min-w-0"]}
           [:div {:class ["mb-2" "flex" "items-center" "gap-2" "text-xs" "text-[#8da5aa]"]}
            [:button {:type "button"
                      :class ["hover:text-trading-text"]
                      :on {:click [[:actions/navigate "/vaults"]]}}
             "Vaults"]
            [:span ">"]
            [:span {:class ["truncate"]} vault-name]]
           [:h1 {:class ["text-[30px]"
                         "leading-[0.96]"
                         "font-semibold"
                         "tracking-tight"
                         "text-trading-text"
                         "sm:text-[54px]"]}
            vault-name]
           [:div {:class ["mt-1.5" "num" "text-sm" "text-[#89a1a8]"]}
            (or (wallet/short-addr vault-address) vault-address)]
           (relationship-links {:relationship relationship})]
          [:div {:class ["grid" "w-full" "grid-cols-2" "gap-2" "lg:w-auto" "lg:flex"]}
           [:button
            {:type "button"
             :disabled true
             :class ["rounded-lg"
                     "border"
                     "border-[#2a4b4b]"
                     "bg-[#08202a]/55"
                     "px-4"
                     "py-2"
                     "text-sm"
                     "text-[#6c8e93]"
                     "opacity-70"
                     "cursor-not-allowed"]}
            "Withdraw"]
           [:button
            {:type "button"
             :disabled true
             :class ["rounded-lg"
                     "border"
                     "border-[#2a4b4b]"
                     "bg-[#08202a]/55"
                     "px-4"
                     "py-2"
                     "text-sm"
                     "text-[#6c8e93]"
                     "opacity-70"
                     "cursor-not-allowed"]}
            "Deposit"]]]
         [:div {:class ["mt-4" "grid" "grid-cols-2" "gap-2.5" "lg:mt-5" "lg:gap-3" "xl:grid-cols-4"]}
          (metric-card {:label "TVL"
                        :value (format-currency (:tvl metrics) {:missing "$0.00"})})
          (metric-card {:label "Past Month Return"
                        :value (format-percent month-return {:signed? false
                                                            :decimals 0})
                        :accent month-return-accent})
          (metric-card {:label "Your Deposits"
                        :value (format-currency (:your-deposit metrics))})
          (metric-card {:label "All-time Earned"
                        :value (format-currency (:all-time-earned metrics))})]]

        (when loading?
          [:div {:class ["rounded-xl" "border" "border-[#1f3d3d]" "bg-[#081820]" "px-4" "py-2.5" "text-sm" "text-[#8fa6ad]"]}
           "Loading vault details..."])

        (when error
          [:div {:class ["rounded-lg" "border" "border-red-500/40" "bg-red-900/20" "px-3" "py-2" "text-sm" "text-red-200"]}
           error])

        [:div {:class ["grid" "gap-3" "lg:grid-cols-[minmax(280px,1fr)_minmax(0,3fr)]"]}
         [:section {:class ["rounded-2xl"
                            "border"
                            "border-[#1b393a]"
                            "bg-[#071820]"]}
          [:div {:class ["flex" "items-center" "border-b" "border-[#1f3b3c]"]}
           (for [tab tabs]
             ^{:key (str "vault-detail-tab-" (name (:value tab)))}
             (detail-tab-button tab selected-tab))]
          (render-tab-panel vm)]

         [:section {:class ["rounded-2xl"
                            "border"
                            "border-[#1b393a]"
                            "bg-[#071820]"
                            "p-3"]}
          [:div {:class ["flex" "items-center" "border-b" "border-[#1f3b3c]" "pb-2"]}
           [:div {:class ["flex" "items-center" "gap-2"]}
            (for [{:keys [value label]} (:series-tabs chart)]
              ^{:key (str "chart-series-" (name value))}
              [:span {:class (into ["rounded-md"
                                    "border"
                                    "px-2.5"
                                    "py-1"
                                    "text-xs"]
                                   (if (= value (:selected-series chart))
                                     ["border-[#2f5e58]" "bg-[#0d252f]" "text-trading-text"]
                                     ["border-transparent" "text-[#8ea4ab]"]))}
               label])]]
          [:svg
           {:class ["mt-3" "h-[260px]" "w-full"]
            :viewBox (str "0 0 " (:width chart) " " (:height chart))
            :preserveAspectRatio "none"}
           [:line {:x1 0
                   :x2 (:width chart)
                   :y1 (:height chart)
                   :y2 (:height chart)
                   :stroke "rgba(140, 157, 165, 0.30)"
                   :stroke-width 1}]
           (when (seq (:path chart))
             [:path {:d (:path chart)
                     :fill "none"
                     :stroke "#e7ecef"
                     :stroke-width 2
                     :vector-effect "non-scaling-stroke"}])]]

        (activity-panel vm)]])]))
