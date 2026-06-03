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

(defn- format-usd
  [value]
  (if-let [value* (parse-number value)]
    (.format usd-formatter value*)
    "N/A"))

(defn- pill
  [label active?]
  [:span {:class (into ["inline-flex"
                        "items-center"
                        "rounded-full"
                        "border"
                        "px-2.5"
                        "py-1"
                        "text-xs"
                        "font-semibold"]
                       (if active?
                         ["border-[#2dceb3]" "bg-[#123a36]" "text-[#97fce4]"]
                         ["border-base-300" "bg-base-200/40" "text-trading-text-secondary"]))}
   label])

(defn- action-button
  [{:keys [data-role label on-click active? disabled?]}]
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
                           active?
                           ["border-[#2dceb3]" "bg-[#123a36]" "text-[#97fce4]"]

                           disabled?
                           ["cursor-not-allowed" "border-base-300" "bg-base-200/30" "text-trading-text-secondary"]

                           :else
                           ["border-base-300" "bg-base-200/40" "text-white" "hover:bg-base-200"]))
            :on {:click on-click}}
   label])

(defn- section-shell
  [children]
  (into [:section {:class ["rounded-lg"
                           "border"
                           "border-base-300"
                           "bg-base-100"
                           "p-4"
                           "space-y-3"]}]
        children))

(defn- table-header
  [labels]
  [:thead {:class ["text-left" "text-xs" "uppercase" "tracking-[0.08em]" "text-trading-text-secondary"]}
   [:tr
    (for [label labels]
      ^{:key label}
      [:th {:class ["px-3" "py-2" "font-semibold"]} label])]])

(defn- master-section
  [{:keys [owner-address selected-master? connected?]}]
  (section-shell
   [[:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
     [:div
      [:h2 {:class ["text-base" "font-semibold" "text-white"]} "Master Account"]
      [:p {:class ["text-sm" "text-trading-text-secondary"]}
       "Orders sign with this wallet. Selecting a subaccount sends order-like actions with that subaccount as the exchange vault address."]]
     (pill (if selected-master? "Selected" "Available") selected-master?)]
    [:div {:class ["overflow-x-auto"]}
     [:table {:class ["min-w-full" "text-sm"]}
      (table-header ["Name" "Address" "Status" "Action"])
      [:tbody
       [:tr {:data-role "subaccounts-master-row"
             :class ["border-t" "border-base-300"]}
        [:td {:class ["px-3" "py-3" "font-medium" "text-white"]} "Master"]
        [:td {:class ["px-3" "py-3" "num" "text-trading-text-secondary"]}
         (or (wallet/short-addr owner-address) "Not connected")]
        [:td {:class ["px-3" "py-3"]} (pill (if connected? "Connected" "Disconnected") connected?)]
        [:td {:class ["px-3" "py-3"]}
         (action-button {:data-role "subaccounts-select-master"
                         :label (if selected-master? "Selected" "Use Master")
                         :active? selected-master?
                         :disabled? (not connected?)
                         :on-click [[:actions/select-master-account]]})]]]]]]))

(defn- subaccount-row
  [{:keys [row selected-address subaccounts]}]
  (let [address (row-address row)
        selected? (= address selected-address)]
    [:tr {:data-role (str "subaccounts-row-" address)
          :class ["border-t" "border-base-300"]}
     [:td {:class ["px-3" "py-3" "font-medium" "text-white"]} (row-name row)]
     [:td {:class ["px-3" "py-3" "num" "text-trading-text-secondary"]} address]
     [:td {:class ["px-3" "py-3" "num" "text-trading-text"]} (format-usd (account-value row))]
     [:td {:class ["px-3" "py-3"]} (pill (if selected? "Selected" "Available") selected?)]
     [:td {:class ["px-3" "py-3"]}
      [:div {:class ["flex" "flex-col" "gap-2"]}
       (action-button {:data-role (str "subaccounts-select-" address)
                       :label (if selected? "Selected" "Use Subaccount")
                       :active? selected?
                       :on-click [[:actions/select-subaccount address]]})
       (management/row-controls {:address address
                                 :subaccounts subaccounts})]]]))

(defn- subaccounts-section
  [{:keys [rows selected-address status error subaccounts]}]
  (section-shell
   [[:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
     [:div
      [:h2 {:class ["text-base" "font-semibold" "text-white"]} "Sub-Accounts"]
      [:p {:class ["text-sm" "text-trading-text-secondary"]}
       "Select an owned subaccount to route trading, open orders, fills, and funding history to that account."]]
     (pill (str (count rows) " loaded") (seq rows))]
    (when (seq error)
      [:div {:class ["rounded-lg" "border" "border-red-500/30" "bg-red-500/10" "px-3" "py-2" "text-sm" "text-red-200"]}
       error])
    (cond
      (= :loading status)
      [:div {:class ["rounded-lg" "border" "border-base-300" "bg-base-200/30" "px-3" "py-6" "text-center" "text-sm" "text-trading-text-secondary"]}
       "Loading subaccounts..."]

      (empty? rows)
      [:div {:class ["rounded-lg" "border" "border-base-300" "bg-base-200/30" "px-3" "py-6" "text-center" "text-sm" "text-trading-text-secondary"]}
       "No subaccounts found for this master account."]

      :else
      [:div {:class ["overflow-x-auto"]}
       [:table {:class ["min-w-full" "text-sm"]}
        (table-header ["Name" "Address" "Account Value" "Status" "Action"])
        (into [:tbody]
              (map (fn [row]
                     ^{:key (row-address row)}
                     (subaccount-row {:row row
                                      :selected-address selected-address
                                      :subaccounts subaccounts}))
                   rows))]])]))

(defn subaccounts-view
  [state]
  (let [owner-address (account-context/owner-address state)
        connected? (seq owner-address)
        subaccounts (get-in state [:account-context :subaccounts])
        rows (vec (or (:rows subaccounts) []))
        selected-address (account-context/selected-subaccount-address state)
        selected-master? (nil? selected-address)
        status (:status subaccounts)
        error (:error subaccounts)]
    [:div {:class ["app-shell-gutter"
                   "flex"
                   "w-full"
                   "flex-col"
                   "gap-4"
                   "pt-4"
                   "pb-16"]
           :data-parity-id "subaccounts-root"}
     [:section {:class ["rounded-lg"
                        "border"
                        "border-base-300"
                        "bg-base-100"
                        "p-4"
                        "space-y-3"]}
      [:div {:class ["flex" "flex-col" "gap-3" "md:flex-row" "md:items-start" "md:justify-between"]}
       [:div {:class ["space-y-2"]}
        [:h1 {:class ["text-2xl" "font-semibold" "text-white"]} "Sub-Accounts"]
        (if connected?
          [:p {:class ["max-w-3xl" "text-sm" "text-trading-text-secondary"]}
           "Managing subaccounts for "
           [:span {:class ["num" "text-trading-text"]} (wallet/short-addr owner-address)]
           "."]
          [:p {:class ["max-w-3xl" "text-sm" "text-trading-text-secondary"]}
           "Connect a wallet to load and select subaccounts."])]
       (action-button {:data-role "subaccounts-refresh"
                       :label (if (= :loading status) "Refreshing..." "Refresh")
                       :disabled? (not connected?)
                       :on-click [[:actions/load-subaccounts-route subaccounts-actions/canonical-route]]})]]
     (master-section {:owner-address owner-address
                      :selected-master? selected-master?
                      :connected? connected?})
     (management/create-panel {:subaccounts subaccounts
                               :connected? (boolean connected?)})
     (subaccounts-section {:rows rows
                           :selected-address selected-address
                           :status status
                           :error error
                           :subaccounts subaccounts})]))

(defn ^:export route-view
  [state]
  (subaccounts-view state))

(goog/exportSymbol "hyperopen.views.subaccounts_view.route_view" route-view)
