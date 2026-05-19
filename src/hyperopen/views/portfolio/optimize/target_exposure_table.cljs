(ns hyperopen.views.portfolio.optimize.target-exposure-table
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.view-model.rebalance :as rebalance-view-model]
            [hyperopen.views.asset-icon :as asset-icon]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(defn- format-delta-pct
  [value]
  (opt-format/format-pct-delta value
                               {:minimum-fraction-digits 1
                                :maximum-fraction-digits 1
                                :suffix "%"}))

(defn- format-compact-usdc
  [value]
  (if (opt-format/finite-number? value)
    (let [abs-value (js/Math.abs value)
          sign (cond
                 (pos? value) "+"
                 (neg? value) "-"
                 :else "")]
      (cond
        (>= abs-value 1000000)
        (str sign "$"
             (.toLocaleString (/ abs-value 1000000)
                               "en-US"
                               #js {:maximumFractionDigits 1})
             "m")

        (>= abs-value 1000)
        (str sign "$"
             (.toLocaleString (/ abs-value 1000)
                               "en-US"
                               #js {:maximumFractionDigits 0})
             "k")

        :else
        (str sign "$"
             (.toLocaleString abs-value
                               "en-US"
                               #js {:maximumFractionDigits 0}))))
    "N/A"))

(defn- data-role-token
  [value]
  (-> (str value)
      (str/replace #"[^A-Za-z0-9_-]+" "-")
      (str/replace #"(^-+|-+$)" "")))

(defn- allocation-asset-icon
  [asset {:keys [icon-kind market]}]
  (let [vault? (= :vault icon-kind)
        icon-url (when-not vault?
                   (asset-icon/market-icon-url market))]
    [:span {:class ["inline-flex" "h-4" "w-4" "shrink-0" "items-center" "justify-center"]
            :data-role (str "portfolio-optimizer-target-exposure-icon-"
                            (data-role-token asset))
            :aria-hidden true}
     (if vault?
       [:span {:class ["block" "h-2.5" "w-2.5" "rotate-45" "border" "border-cyan-300/70" "bg-cyan-300/15" "shadow-[0_0_6px_rgba(34,211,238,0.24)]"]
               :data-role (str "portfolio-optimizer-target-exposure-vault-diamond-"
                               (data-role-token asset))}]
       (if (seq icon-url)
         [:img {:class ["block" "h-4" "w-4" "rounded-full" "object-contain"]
                :src icon-url
                :alt ""
                :data-role (str "portfolio-optimizer-target-exposure-asset-icon-img-"
                                (data-role-token asset))}]
         [:span {:class ["block" "h-3" "w-3" "rounded-full" "border" "border-base-300" "bg-base-200"]}]))]))

(defn- exposure-row
  [{:keys [idx binding? hidden? current-sign target-sign leg-label current-weight
           target-weight delta delta-notional]}]
  [:tr {:class (cond-> ["optimizer-target-exposure-row" "optimizer-exposure-row"]
                binding? (conj "bg-warning/10")
                hidden? (conj "hidden"))
        :data-role (str "portfolio-optimizer-target-exposure-row-" idx)
        :data-binding (when binding? "true")
        :data-current-sign current-sign
        :data-target-sign target-sign}
   [:td {:class ["text-trading-muted"]} ""]
   [:td {:class ["pl-8" "text-trading-muted"]}
    leg-label]
   [:td {:class ["font-mono" "text-right" "tabular-nums"]} (opt-format/format-pct current-weight {:minimum-fraction-digits 1 :maximum-fraction-digits 1})]
   [:td {:class ["font-mono" "text-right" "tabular-nums"]} (opt-format/format-pct target-weight {:minimum-fraction-digits 1 :maximum-fraction-digits 1})]
   [:td {:class [(if (neg? delta) "text-trading-red" "text-trading-green")
                 "font-mono" "text-right" "tabular-nums"]}
    (format-delta-pct delta)]
   [:td {:class [(if (neg? delta) "text-trading-red" "text-trading-green")
                 "font-mono" "text-right" "tabular-nums"]}
    (format-compact-usdc delta-notional)]])

(defn- exposure-group-row
  [{:keys [asset current-weight target-weight delta delta-notional binding?
           expandable? target-sign] :as group}]
  [:tr {:class ["optimizer-target-exposure-asset" "optimizer-exposure-row" "cursor-pointer"]
        :data-role (str "portfolio-optimizer-target-exposure-asset-"
                        (data-role-token asset))
        :data-target-sign target-sign}
   [:td {:class ["w-5" "font-mono" "text-trading-muted/70"]}
    (when expandable? "▾")]
   [:td {:class ["font-mono" "font-semibold" "text-trading-text"]}
    [:span {:class ["inline-flex" "min-w-0" "items-center" "gap-2"]}
     (allocation-asset-icon asset group)
     [:span {:data-role (str "portfolio-optimizer-target-exposure-group-"
                             (data-role-token asset))}
      asset]]
    (when binding?
      [:span {:class ["ml-2" "border" "border-warning/50" "px-1.5" "py-0.5"
                      "font-mono" "text-[0.5rem]" "font-semibold" "uppercase"
                      "tracking-[0.08em]" "text-warning"]}
       "capped"])]
   [:td {:class ["font-mono" "text-right" "font-semibold" "tabular-nums"]} (opt-format/format-pct current-weight {:minimum-fraction-digits 1 :maximum-fraction-digits 1})]
   [:td {:class ["font-mono" "text-right" "font-semibold" "tabular-nums"]} (opt-format/format-pct target-weight {:minimum-fraction-digits 1 :maximum-fraction-digits 1})]
   [:td {:class [(if (neg? delta) "text-trading-red" "text-trading-green")
                 "font-mono" "text-right" "font-semibold" "tabular-nums"]}
    (format-delta-pct delta)]
   [:td {:class [(if (neg? delta) "text-trading-red" "text-trading-green")
                 "font-mono" "text-right" "font-semibold" "tabular-nums"]}
    (format-compact-usdc delta-notional)]])

(defn target-exposure-table
  [result]
  (let [{:keys [groups]} (rebalance-view-model/target-exposure-table-model result)]
    [:section {:class ["optimizer-target-exposure-table"
                       "min-h-0" "border-r" "border-base-300" "bg-base-100/95" "leading-4"]
               :data-role "portfolio-optimizer-target-exposure-table"}
     [:div {:class ["flex" "items-center" "justify-between" "border-b" "border-base-300" "px-4" "py-3"]}
      [:div
       [:p {:class ["font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
        "Allocation"]
       [:p {:class ["mt-1" "text-xs" "text-trading-text"]}
        "By asset · click to expand legs"]]
      [:div {:class ["flex" "border" "border-base-300" "text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]"]}
       [:button {:type "button"
                 :class ["border-r" "border-base-300" "bg-base-200/60" "px-3" "py-1" "text-trading-text"]}
        "By Asset"]
       [:button {:type "button"
                 :class ["px-3" "py-1" "text-trading-muted"]}
        "By Leg"]]]
     [:div {:class ["overflow-auto"]}
      [:table {:class ["w-full" "border-collapse" "text-[0.6875rem]"]}
       [:thead
        [:tr
         [:th {:class ["sticky" "top-0" "w-5" "border-b" "border-base-300" "bg-base-100" "px-2" "py-1.5" "text-left" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} ""]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-3" "py-2" "text-left" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Asset"]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-3" "py-2" "text-right" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Current"]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-3" "py-2" "text-right" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Target"]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-3" "py-2" "text-right" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Δ %"]
         [:th {:class ["sticky" "top-0" "border-b" "border-base-300" "bg-base-100" "px-3" "py-2" "text-right" "font-mono" "text-[0.58rem]" "font-normal" "uppercase" "tracking-[0.06em]" "text-trading-muted/70"]} "Δ $"]]]
	       (into
	        [:tbody]
	        (mapcat
	         (fn [{:keys [rows] :as group}]
	           (concat
	            [(exposure-group-row group)]
	            (map exposure-row rows)))
	         groups))]]]))
