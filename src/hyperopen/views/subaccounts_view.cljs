(ns hyperopen.views.subaccounts-view
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.subaccounts.actions :as subaccounts-actions]
            [hyperopen.views.subaccounts-view.management :as management]
            [hyperopen.wallet.core :as wallet]))

(def ^:private usd-formatter
  (js/Intl.NumberFormat.
   "en-US"
   #js {:style "currency"
        :currency "USD"
        :minimumFractionDigits 2
        :maximumFractionDigits 2}))

(defn- normalize-address
  [value]
  (account-context/normalize-address value))

(defn- row-address
  [row]
  (normalize-address
   (or (:sub-account-user row)
       (:subAccountUser row)
       (get row "subAccountUser")
       (get row "sub-account-user"))))

(defn- row-name
  [row]
  (let [name* (some-> (or (:name row)
                          (get row "name"))
                      str
                      str/trim)]
    (if (seq name*) name* "Unnamed")))

(defn- nested-value
  [m paths]
  (some (fn [path]
          (let [value (get-in m path)]
            (when (some? value) value)))
        paths))

(defn- clearinghouse-state
  [row]
  (or (:clearinghouse-state row)
      (:clearinghouseState row)
      (get row "clearinghouseState")
      (get row "clearinghouse-state")))

(defn- spot-state
  [row]
  (or (:spot-state row)
      (:spotState row)
      (get row "spotState")
      (get row "spot-state")))

(defn- parse-number
  [value]
  (let [parsed (cond
                 (number? value) value
                 (string? value) (js/Number value)
                 :else js/NaN)]
    (when (and (number? parsed)
               (not (js/isNaN parsed))
               (js/isFinite parsed))
      parsed)))

(defn- account-value
  [row]
  (parse-number
   (nested-value (clearinghouse-state row)
                 [[:marginSummary :accountValue]
                  [:margin-summary :account-value]
                  ["marginSummary" "accountValue"]
                  ["margin-summary" "account-value"]])))

(defn- spot-account-value
  [row]
  (let [balances (or (:balances (spot-state row))
                     (get (spot-state row) "balances"))]
    (some (fn [balance]
            (let [coin (or (:coin balance) (get balance "coin"))
                  total (or (:total balance) (get balance "total"))]
              (when (= "USDC" (some-> coin str str/upper-case))
                (parse-number total))))
          balances)))

(defn- format-usd
  [value]
  (if-let [value* (parse-number value)]
    (.format usd-formatter value*)
    "N/A"))

(defn- pill
  [label active?]
  [:span {:class (into ["inline-flex"
                        "items-center"
                        "rounded-md"
                        "border"
                        "px-2"
                        "py-1"
                        "text-xs"
                        "font-semibold"]
                       (if active?
                         ["border-[#2dceb3]/70" "bg-[#0f3a35]" "text-[#97fce4]"]
                         ["border-base-300" "bg-[#121d20]" "text-trading-text-secondary"]))}
   label])

(defn- action-button
  [{:keys [data-role label on-click active? disabled?]}]
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
                           active?
                           ["border-[#2dceb3]" "bg-[#0f3a35]" "text-[#97fce4]"]

                           disabled?
                           ["cursor-not-allowed" "border-base-300" "bg-base-200/30" "text-trading-text-secondary"]

                           :else
                           ["border-base-300" "bg-[#121d20]" "text-white" "hover:border-[#2dceb3]/60" "hover:bg-[#172528]"]))
            :on {:click on-click}}
   label])

(defn- table-header
  [labels]
  [:thead {:class ["text-left" "text-xs" "text-trading-text-secondary"]}
   [:tr
    (for [label labels]
      ^{:key label}
      [:th {:class ["px-3" "py-2.5" "font-medium"]} label])]])

(defn- section-heading
  [title accessory]
  [:div {:class ["flex" "items-center" "justify-between" "gap-3" "px-3" "pb-1" "pt-3"]}
   [:h2 {:class ["text-sm" "font-semibold" "text-white"]} title]
   accessory])

(defn- empty-row
  [colspan message]
  [:tr
   [:td {:class ["px-3" "py-8" "text-center" "text-sm" "text-trading-text-secondary"]
         :colSpan colspan}
    message]])

(defn- master-section
  [{:keys [owner-address selected-master? connected? perps-value spot-value]}]
  [:div
   (section-heading "Master Account"
                    (pill (if selected-master? "Selected" "Available") selected-master?))
   [:div {:class ["overflow-x-auto"]}
    [:table {:class ["min-w-[760px]" "w-full" "text-sm"]}
     (table-header ["Name" "Address" "Perps Account Equity" "Spot Account Equity" "Action"])
     [:tbody
      [:tr {:data-role "subaccounts-master-row"
            :class ["border-t" "border-base-300" "transition-colors" "hover:bg-white/[0.025]"]}
       [:td {:class ["px-3" "py-4" "font-semibold" "text-white"]} "Master Account"]
       [:td {:class ["px-3" "py-4" "num" "text-trading-text-secondary"]}
        (or (wallet/short-addr owner-address) "Not connected")]
       [:td {:class ["px-3" "py-4" "num" "font-medium" "text-white"]} (format-usd perps-value)]
       [:td {:class ["px-3" "py-4" "num" "font-medium" "text-white"]} (format-usd spot-value)]
       [:td {:class ["px-3" "py-4" "text-right"]}
        (action-button {:data-role "subaccounts-select-master"
                        :label (if selected-master? "Selected" "Use Master")
                        :active? selected-master?
                        :disabled? (not connected?)
                        :on-click [[:actions/select-master-account]]})]]]]]])

(defn- subaccount-row
  [{:keys [row selected-address subaccounts]}]
  (let [address (row-address row)
        selected? (= address selected-address)]
    [:tr {:data-role (str "subaccounts-row-" address)
          :class (into ["border-t" "border-base-300" "transition-colors" "hover:bg-white/[0.025]"]
                       (when selected?
                         ["bg-[#123a36]/35"]))}
     [:td {:class ["px-3" "py-4" "align-top" "font-semibold" "text-white"]} (row-name row)]
     [:td {:class ["px-3" "py-4" "align-top" "num" "text-trading-text-secondary"]} address]
     [:td {:class ["px-3" "py-4" "align-top" "num" "font-medium" "text-white"]}
      (format-usd (account-value row))]
     [:td {:class ["px-3" "py-4" "align-top" "num" "font-medium" "text-white"]}
      (format-usd (spot-account-value row))]
     [:td {:class ["px-3" "py-4" "align-top" "text-right"]}
      [:div {:class ["flex" "justify-end" "gap-2"]}
       (action-button {:data-role (str "subaccounts-select-" address)
                       :label (if selected? "Selected" "Use")
                       :active? selected?
                       :on-click [[:actions/select-subaccount address]]})
       (management/row-controls {:address address
                                 :subaccounts subaccounts})]]]))

(defn- subaccounts-section
  [{:keys [rows selected-address status error subaccounts]}]
  [:div
   (section-heading "Sub-Accounts"
                    (pill (str (count rows) " loaded") (seq rows)))
   (when (seq error)
     [:div {:class ["mx-3" "mb-2" "rounded-md" "border" "border-red-500/30" "bg-red-500/10" "px-3" "py-2" "text-sm" "text-red-200"]}
      error])
   [:div {:class ["overflow-x-auto"]}
    [:table {:class ["min-w-[960px]" "w-full" "text-sm"]}
     (table-header ["Name" "Address" "Perps Account Equity" "Spot Account Equity" "Action"])
     (cond
       (= :loading status)
       [:tbody (empty-row 5 "Loading subaccounts...")]

       (empty? rows)
       [:tbody (empty-row 5 "No subaccounts found for this master account.")]

       :else
       (into [:tbody]
             (map (fn [row]
                    ^{:key (row-address row)}
                    (subaccount-row {:row row
                                     :selected-address selected-address
                                     :subaccounts subaccounts}))
                  rows)))]]])

(defn- page-kicker
  [connected? owner-address selected-master? selected-address]
  [:div {:class ["flex" "flex-wrap" "items-center" "gap-2" "text-xs" "text-trading-text-secondary"]}
   (if connected?
     [:span
      "Managing "
      [:span {:class ["num" "text-trading-text"]} (wallet/short-addr owner-address)]]
     [:span "Connect a wallet to load and select subaccounts."])
   (when connected?
     (pill (if selected-master?
             "Trading Master"
             (str "Trading " (wallet/short-addr selected-address)))
           true))])

(defn subaccounts-view
  [state]
  (let [owner-address (account-context/owner-address state)
        connected? (seq owner-address)
        subaccounts (get-in state [:account-context :subaccounts])
        rows (vec (or (:rows subaccounts) []))
        selected-address (account-context/selected-subaccount-address state)
        selected-master? (nil? selected-address)
        status (:status subaccounts)
        error (:error subaccounts)
        master-perps-value (account-value {:clearinghouse-state (get-in state [:webdata2 :clearinghouseState])})
        master-spot-value (spot-account-value {:spot-state (get-in state [:spot :clearinghouse-state])})]
    [:div {:class ["app-shell-gutter"
                   "flex"
                   "w-full"
                   "flex-col"
                   "gap-5"
                   "pt-5"
                   "pb-16"]
           :data-parity-id "subaccounts-root"}
     [:section {:class ["flex" "flex-col" "gap-4" "md:flex-row" "md:items-end" "md:justify-between"]}
      [:div {:class ["space-y-2"]}
       [:h1 {:class ["text-[2rem]" "font-semibold" "leading-tight" "text-white" "sm:text-[2.5rem]"]}
        "Sub-Accounts"]
       (page-kicker connected? owner-address selected-master? selected-address)]
      [:div {:class ["flex" "w-full" "flex-col" "gap-2" "sm:w-auto" "sm:flex-row" "sm:items-center"]}
       (management/create-panel {:subaccounts subaccounts
                                 :connected? (boolean connected?)})
       (action-button {:data-role "subaccounts-refresh"
                       :label (if (= :loading status) "Refreshing..." "Refresh")
                       :disabled? (not connected?)
                       :on-click [[:actions/load-subaccounts-route subaccounts-actions/canonical-route]]})]]
     [:section {:class ["overflow-hidden"
                        "rounded-lg"
                        "border"
                        "border-[#1b3133]"
                        "bg-[#0d171a]"
                        "shadow-[0_18px_60px_rgba(0,0,0,0.22)]"]
                :data-role "subaccounts-console"}
      (master-section {:owner-address owner-address
                       :selected-master? selected-master?
                       :connected? connected?
                       :perps-value master-perps-value
                       :spot-value master-spot-value})
      [:div {:class ["border-t" "border-base-300"]}]
      (subaccounts-section {:rows rows
                            :selected-address selected-address
                            :status status
                            :error error
                            :subaccounts subaccounts})]]))

(defn ^:export route-view
  [state]
  (subaccounts-view state))

(goog/exportSymbol "hyperopen.views.subaccounts_view.route_view" route-view)
