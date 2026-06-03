(ns hyperopen.views.header.account-selector
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.views.header.icons :as icons]
            [hyperopen.wallet.core :as wallet]))

(def ^:private master-value
  "master")

(defn- row-address
  [row]
  (account-context/normalize-address
   (or (:sub-account-user row)
       (:subAccountUser row)
       (get row "subAccountUser")
       (get row "sub-account-user"))))

(defn- row-master
  [row]
  (account-context/normalize-address
   (or (:master row)
       (get row "master"))))

(defn- row-name
  [row]
  (some-> (:name row) str str/trim not-empty))

(defn- owned-row
  [owner row]
  (when-let [address (row-address row)]
    (when (= owner (row-master row))
      (assoc row :sub-account-user address))))

(defn- owned-rows
  [state owner]
  (->> (get-in state [:account-context :subaccounts :rows])
       (keep #(owned-row owner %))
       vec))

(defn- master-option
  [owner selected?]
  {:kind :master
   :value master-value
   :label "Master"
   :address-label (wallet/short-addr owner)
   :selected? selected?
   :data-role "header-account-target-option-master"
   :action [[:actions/select-master-account]]})

(defn- subaccount-option
  [selected-address row]
  (let [address (:sub-account-user row)
        label (or (row-name row) "Subaccount")]
    {:kind :subaccount
     :value address
     :label label
     :address-label (wallet/short-addr address)
     :selected? (= selected-address address)
     :data-role (str "header-account-target-option-" address)
     :action [[:actions/select-subaccount address]]}))

(defn- selected-owned-address
  [selected rows]
  (some (fn [row]
          (when (= selected (:sub-account-user row))
            selected))
        rows))

(defn vm
  [state]
  (let [owner (account-context/owner-address state)
        connected? (true? (get-in state [:wallet :connected?]))
        rows (owned-rows state owner)
        selected (selected-owned-address
                  (account-context/selected-subaccount-address state)
                  rows)]
    (when (and connected?
               (seq owner)
               (seq rows))
      (let [options (into [(master-option owner (nil? selected))]
                          (mapv (partial subaccount-option selected) rows))
            selected-option (or (some #(when (:selected? %) %) options)
                                (first options))]
        {:trigger-label (:label selected-option)
         :trigger-address-label (:address-label selected-option)
         :options options}))))

(defn- option-row
  [{:keys [action address-label data-role label selected? value]}]
  ^{:key (str "header-account-target:" value)}
  [:button {:type "button"
            :data-role data-role
            :aria-current (when selected? "true")
            :class (into ["flex"
                          "w-full"
                          "items-center"
                          "justify-between"
                          "gap-3"
                          "px-3"
                          "py-2.5"
                          "text-left"
                          "text-xs"
                          "transition-colors"
                          "hover:bg-base-200"
                          "focus:outline-none"]
                         (if selected?
                           ["text-[#97fce4]"]
                           ["text-white"]))
            :on {:click action}}
   [:span {:class ["min-w-0" "truncate" "font-medium"]} label]
   [:span {:class ["num" "shrink-0" "text-trading-text-secondary"]} address-label]])

(defn render
  [{:keys [options trigger-address-label trigger-label] :as selector}]
  (when selector
    [:details {:class ["relative" "group" "hidden" "md:inline-flex"]
               :data-role "header-account-target-details"}
     [:summary {:class ["inline-flex"
                        "h-9"
                        "sm:h-10"
                        "cursor-pointer"
                        "list-none"
                        "items-center"
                        "gap-2"
                        "rounded-xl"
                        "border"
                        "border-base-300"
                        "bg-base-100"
                        "px-2.5"
                        "text-xs"
                        "text-white"
                        "transition-colors"
                        "hover:bg-base-200"
                        "[&::-webkit-details-marker]:hidden"]
                :data-role "header-account-target-trigger"
                :aria-haspopup "menu"
                :aria-label "Account target"}
      [:span {:class ["max-w-[7rem]" "truncate" "font-medium"]} trigger-label]
      [:span {:class ["num" "hidden" "text-trading-text-secondary" "xl:inline"]}
       trigger-address-label]
      (icons/chevron-down-icon
       {:class ["h-4" "w-4" "text-gray-300" "transition-transform" "group-open:rotate-180"]
        :data-role "header-account-target-chevron"})]
     [:div {:class ["ui-dropdown-panel"
                    "absolute"
                    "right-0"
                    "top-full"
                    "z-[250]"
                    "mt-2"
                    "w-60"
                    "overflow-hidden"
                    "rounded-xl"
                    "border"
                    "border-base-300"
                    "bg-trading-bg"
                    "shadow-2xl"]
            :data-ui-native-details-panel "true"
            :data-role "header-account-target-menu"}
      (map option-row options)]]))
