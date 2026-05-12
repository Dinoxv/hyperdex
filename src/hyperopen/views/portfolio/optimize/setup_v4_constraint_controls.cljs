(ns hyperopen.views.portfolio.optimize.setup-v4-constraint-controls
  (:require [hyperopen.views.portfolio.optimize.setup-v4-controls :as controls]))

(def ^:private constraint-help
  {:long-only? "Restricts target weights to zero or positive values. Turn this off when short or hedged perp exposure is allowed."
   :max-asset-weight "Maximum target portfolio weight any single asset can receive. 0.5 means no asset can exceed 50%."
   :gross-max "Maximum total absolute exposure across all legs. 1 means long exposure plus short exposure can total up to 100% of capital."
   :net-min "Minimum signed net exposure allowed after optimization. Leave blank when only the maximum net exposure matters."
   :net-max "Maximum signed net exposure allowed after optimization. 1 means the portfolio can be net long up to 100% of capital."
   :dust-usdc "Small rebalance trades below this USDC notional are ignored so the output avoids noisy dust orders."
   :max-turnover "Maximum total portfolio turnover allowed for the rebalance. 1 means trades can sum to 100% of capital."
   :rebalance-tolerance "Minimum target-vs-current weight difference before a rebalance row is considered actionable. 0.03 means 3 percentage points."})

(defn- constraint-tooltip
  [tooltip-id copy]
  [:span {:class ["pointer-events-none" "absolute" "left-0" "top-[calc(100%+6px)]"
                  "z-30" "w-[min(22rem,calc(100vw-2rem))]" "border"
                  "border-base-300" "bg-base-100" "px-2" "py-1.5"
                  "font-sans" "text-[0.65625rem]" "font-normal"
                  "normal-case" "leading-[1.45]" "tracking-normal"
                  "text-trading-muted" "opacity-0" "shadow-[0_12px_32px_rgba(0,0,0,0.45)]"
                  "transition-opacity" "duration-150" "group-hover:opacity-100"
                  "group-focus-within:opacity-100"]
          :id tooltip-id
          :role "tooltip"
          :data-role tooltip-id}
   copy])

(defn- constraint-label
  [label tooltip-id help-copy]
  [:span {:class ["relative" "inline-flex" "min-w-0" "items-center" "gap-1.5"]}
   [:span {:class controls/eyebrow-class} label]
   [:span {:class ["font-mono" "text-[0.5625rem]" "text-trading-muted/70"]
           :aria-hidden "true"}
    "?"]
   (constraint-tooltip tooltip-id help-copy)])

(defn- constraint-row
  ([label constraint-key value role highlighted?]
   (constraint-row label nil constraint-key value role highlighted?))
  ([label hidden-label constraint-key value role highlighted?]
   (let [tooltip-id (str role "-tooltip")
         help-copy (get constraint-help constraint-key)]
     [:label {:class (cond-> ["group" "relative" "grid" "grid-cols-[minmax(0,1fr)_92px]" "items-center"
                              "gap-2" "border" "border-base-300" "bg-base-200/20"
                              "px-2" "py-1.5"]
                       highlighted? (conj "border-warning/70" "bg-warning/10"))}
      [:span {:class ["min-w-0"]}
       (if help-copy
         (constraint-label label tooltip-id help-copy)
         [:span {:class controls/eyebrow-class} label])
       (when hidden-label
         [:span {:class ["sr-only"]} hidden-label])
       [:span {:class ["ml-2" "font-mono" "text-[0.59375rem]" "uppercase"
                       "tracking-[0.08em]" "text-trading-muted"]}
        "edit"]]
      [:input {:type "text"
               :inputmode "decimal"
               :class controls/input-class
               :data-role role
               :data-infeasible (when highlighted? "true")
               :aria-invalid (when highlighted? "true")
               :aria-describedby (when help-copy tooltip-id)
               :value (str value)
               :on {:input [[:actions/set-portfolio-optimizer-constraint
                             constraint-key
                             [:event.target/value]]]}}]])))

(defn constraints-section
  [draft highlighted-controls]
  (let [constraints (:constraints draft)]
    (controls/disclosure-panel
     "portfolio-optimizer-constraints-panel"
     (controls/disclosure-heading "04" "Constraints" "mandatory")
     [:div {:class ["mt-3" "grid" "grid-cols-1" "gap-2"]}
      [:label {:class ["group" "relative" "flex" "items-center" "justify-between" "gap-3" "border"
                       "border-base-300" "bg-base-200/20" "p-2"]}
       [:span {:class ["min-w-0"]}
        (constraint-label "Long Only"
                          "portfolio-optimizer-constraint-long-only-tooltip"
                          (:long-only? constraint-help))]
       [:input {:type "checkbox"
                :class ["h-4" "w-4" "accent-warning" "outline-none"
                        "transition-shadow"
                        "focus:shadow-[0_0_0_2px_rgba(212,181,88,0.75)]"]
                :data-role "portfolio-optimizer-constraint-long-only-input"
                :aria-describedby "portfolio-optimizer-constraint-long-only-tooltip"
                :checked (true? (:long-only? constraints))
                :on {:change [[:actions/set-portfolio-optimizer-constraint
                               :long-only?
                               :event.target/checked]]}}]]
      (constraint-row "Per-asset cap" "Max Asset Weight"
                      :max-asset-weight (:max-asset-weight constraints)
                      "portfolio-optimizer-constraint-max-asset-weight-input"
                      (contains? highlighted-controls :max-asset-weight))
      (constraint-row "Gross exposure" "Gross Leverage"
                      :gross-max (:gross-max constraints)
                      "portfolio-optimizer-constraint-gross-max-input" false)
      (constraint-row "Net exposure min" :net-min (:net-min constraints)
                      "portfolio-optimizer-constraint-net-min-input" false)
      (constraint-row "Net exposure max" :net-max (:net-max constraints)
                      "portfolio-optimizer-constraint-net-max-input" false)
      (constraint-row "Dust threshold" :dust-usdc (:dust-usdc constraints)
                      "portfolio-optimizer-constraint-dust-usdc-input" false)
      (constraint-row "Turnover cap" :max-turnover (:max-turnover constraints)
                      "portfolio-optimizer-constraint-max-turnover-input" false)
      (constraint-row "Rebalance tolerance" "Rebalance Tolerance"
                      :rebalance-tolerance (:rebalance-tolerance constraints)
                      "portfolio-optimizer-constraint-rebalance-tolerance-input" false)])))
