(ns hyperopen.views.vaults.preview-shell
  (:require [hyperopen.utils.formatting :as fmt]
            [hyperopen.wallet.core :as wallet]))

(def ^:private preview-row-limit
  4)

(defn startup-preview-valid?
  [preview]
  (let [rows (or (:protocol-rows preview)
                 (:user-rows preview)
                 (:rows preview)
                 (:visible-rows preview))]
    (and (map? preview)
         (seq rows))))

(defn- format-total-currency
  [value]
  (or (fmt/format-large-currency (if (number? value) value 0))
      "$0"))

(defn- format-currency
  [value]
  (or (fmt/format-currency (if (number? value) value 0))
      "$0.00"))

(defn- format-percent
  [value]
  (let [n (if (number? value) value 0)]
    (str (.toFixed n 2) "%")))

(defn- preview-row
  [{:keys [name vault-address leader apr tvl your-deposit age-days is-closed?]}]
  (let [address-label (or (wallet/short-addr vault-address) vault-address)
        leader-label (or (wallet/short-addr leader) leader)]
    (if (seq vault-address)
      [:a {:href (str "/vaults/" vault-address)
           :class ["block"
                   "rounded-xl"
                   "border"
                   "border-base-300/80"
                   "bg-base-100/80"
                   "px-3"
                   "py-2.5"
                   "transition-colors"
                   "hover:bg-base-200"]
           :data-role "vault-preview-row-link"}
       [:div {:class ["flex" "items-start" "justify-between" "gap-3"]}
        [:div {:class ["min-w-0" "space-y-1"]}
         [:div {:class ["flex" "items-center" "gap-2"]}
          [:span {:class ["truncate" "font-semibold" "text-trading-text"]}
           (or name address-label)]
          (when is-closed?
            [:span {:class ["rounded"
                            "border"
                            "border-amber-600/40"
                            "px-1.5"
                            "py-0.5"
                            "text-xs"
                            "uppercase"
                            "tracking-wide"
                            "text-amber-300"]}
             "Closed"])]
         [:div {:class ["num" "text-xs" "text-trading-text-secondary"]}
          address-label]]
        [:div {:class ["text-right" "text-xs" "text-trading-text-secondary"]}
         [:div [:span "Leader "] [:span {:class ["num" "text-trading-text"]} leader-label]]
         [:div [:span "APR "] [:span {:class ["num" "text-trading-text"]} (format-percent apr)]]
         [:div [:span "TVL "] [:span {:class ["num" "text-trading-text"]} (format-currency tvl)]]
         [:div [:span "Deposit "] [:span {:class ["num" "text-trading-text"]} (format-currency your-deposit)]]
         [:div [:span "Age "] [:span {:class ["num" "text-trading-text"]} (str (or age-days 0)) "d"]]]]]
      [:div {:class ["rounded-xl"
                     "border"
                     "border-base-300/80"
                     "bg-base-100/80"
                     "px-3"
                     "py-2.5"]
             :data-role "vault-preview-row"}
       [:div {:class ["flex" "items-start" "justify-between" "gap-3"]}
        [:div {:class ["min-w-0" "space-y-1"]}
         [:div {:class ["truncate" "font-semibold" "text-trading-text"]}
          (or name "Unnamed vault")]
         [:div {:class ["num" "text-xs" "text-trading-text-secondary"]}
          (or address-label "Unknown address")]]
        [:div {:class ["text-right" "text-xs" "text-trading-text-secondary"]}
         [:div [:span "Leader "] [:span {:class ["num" "text-trading-text"]} leader-label]]
         [:div [:span "APR "] [:span {:class ["num" "text-trading-text"]} (format-percent apr)]]
         [:div [:span "TVL "] [:span {:class ["num" "text-trading-text"]} (format-currency tvl)]]
         [:div [:span "Deposit "] [:span {:class ["num" "text-trading-text"]} (format-currency your-deposit)]]
         [:div [:span "Age "] [:span {:class ["num" "text-trading-text"]} (str (or age-days 0)) "d"]]]]])))

(defn- preview-section
  [label rows]
  (when (seq rows)
    [:section {:class ["space-y-2"] :data-role "vault-preview-section"}
     [:div {:class ["flex" "items-center" "justify-between" "gap-2"]}
      [:h3 {:class ["text-sm" "font-normal" "text-trading-text"]} label]
      [:span {:class ["rounded-full"
                      "border"
                      "border-emerald-400/20"
                      "bg-emerald-500/10"
                      "px-2"
                      "py-0.5"
                      "text-xs"
                      "uppercase"
                      "tracking-[0.08em]"
                      "text-emerald-100"]}
       "Cached"]]
     [:div {:class ["space-y-2"]}
      (for [row (take preview-row-limit rows)]
        ^{:key (str "vault-preview-" label "-" (:vault-address row))}
        (preview-row row))]]))

(defn startup-preview-shell
  [state]
  (let [preview (get-in state [:vaults :startup-preview])
        protocol-rows (or (:protocol-rows preview)
                          (:rows preview)
                          [])
        user-rows (or (:user-rows preview)
                      [])
        total-visible-tvl (:total-visible-tvl preview)
        wallet-address (:wallet-address preview)
        stale? (true? (:stale? preview))]
    [:div {:class ["relative" "w-full" "app-shell-gutter" "py-4" "md:py-6"]
           :data-parity-id "vaults-root"
           :data-role "vaults-startup-preview-shell"}
     [:div {:class ["pointer-events-none"
                    "absolute"
                    "inset-x-0"
                    "top-0"
                    "h-[180px]"
                    "md:h-[300px]"
                    "rounded-b-[24px]"
                    "opacity-90"]
            :style {:background-image "radial-gradient(120% 120% at 15% -10%, rgba(0, 148, 111, 0.35), rgba(6, 30, 34, 0.05) 60%), radial-gradient(130% 140% at 85% 20%, rgba(0, 138, 96, 0.22), rgba(6, 30, 34, 0) 68%), linear-gradient(180deg, rgba(4, 43, 36, 0.72) 0%, rgba(6, 27, 32, 0.15) 100%)"}}]
     [:div {:class ["relative" "mx-auto" "w-full" "max-w-[1280px]" "space-y-4"]}
      [:div {:class ["flex" "flex-wrap" "items-center" "justify-between" "gap-3"]}
       [:div {:class ["space-y-1"]}
        [:h1 {:class ["text-2xl" "font-normal" "text-trading-text" "sm:text-[48px]" "sm:leading-[52px]"]}
         "Vaults"]
        [:div {:class ["text-sm" "text-trading-text-secondary"]}
         "Showing cached preview while the route module and fresh vault data load."]]
       [:div {:class ["inline-flex"
                      "items-center"
                      "gap-2"
                      "rounded-lg"
                      "border"
                      "border-emerald-400/20"
                      "bg-emerald-500/10"
                      "px-3"
                      "py-2"
                      "text-sm"
                      "text-emerald-100"]
              :data-role "vaults-refreshing-banner"}
        [:div {:class ["h-2" "w-2" "rounded-full" "bg-emerald-300" "animate-pulse"]
               :aria-hidden true}]
        "Refreshing vaults…"]]
      (when (seq wallet-address)
        [:div {:class ["inline-flex"
                       "items-center"
                       "gap-2"
                       "rounded-lg"
                       "border"
                       "border-base-300/80"
                       "bg-base-100/80"
                       "px-3"
                       "py-2"
                       "text-xs"
                       "text-trading-text-secondary"]}
         [:span "Preview for"]
         [:span {:class ["num" "text-trading-text"]} (or (wallet/short-addr wallet-address) wallet-address)]
         (when stale?
           [:span {:class ["rounded-full"
                           "border"
                           "border-amber-500/30"
                           "px-2"
                           "py-0.5"
                           "uppercase"
                           "tracking-[0.08em]"
                           "text-amber-200"]}
            "Stale"])])

      [:div {:class ["w-full" "max-w-[320px]" "rounded-xl" "bg-[#0f1a1f]" "px-3" "py-3" "md:max-w-[360px]" "md:rounded-2xl"]}
       [:div {:class ["text-sm" "font-normal" "text-trading-text-secondary"]}
        "Total Value Locked"]
       [:div {:class ["mt-1" "num" "text-[44px]" "leading-[46px]" "font-normal" "text-trading-text"]}
        (format-total-currency total-visible-tvl)]]

      [:div {:class ["rounded-lg"
                     "border"
                     "border-base-300/80"
                     "bg-base-100/90"
                     "p-2.5"
                     "md:rounded-2xl"
                     "md:p-3"]
             :data-role "vaults-startup-preview-toolbar"}
       [:div {:class ["flex" "flex-wrap" "items-center" "gap-2"]}
        [:div {:class ["h-8"
                       "min-w-[260px]"
                       "flex-1"
                       "rounded-lg"
                       "border"
                       "border-base-300"
                       "bg-base-100/70"]}]
        [:div {:class ["h-8"
                       "w-[180px]"
                       "rounded-lg"
                       "border"
                       "border-base-300"
                       "bg-base-100/70"]}]
        [:div {:class ["h-8"
                       "w-[120px]"
                       "rounded-lg"
                       "border"
                       "border-base-300"
                       "bg-base-100/70"]}]]]

      [:section {:class ["rounded-xl"
                         "border"
                         "border-base-300/80"
                         "bg-base-100/95"
                         "p-2.5"
                         "space-y-5"
                         "md:rounded-2xl"
                         "md:p-3"]}
       (preview-section "Protocol Vaults" protocol-rows)
       (preview-section "User Vaults" user-rows)
       (when-not (or (seq protocol-rows) (seq user-rows))
         [:div {:class ["rounded-lg"
                        "border"
                        "border-base-300"
                        "bg-base-200/60"
                        "px-3"
                        "py-4"
                        "text-center"
                        "text-sm"
                        "text-trading-text-secondary"]}
          "Cached preview unavailable."])]]]))
