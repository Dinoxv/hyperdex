(ns hyperopen.views.portfolio.montecarlo.summary
  "Probability-of-goal / probability-of-bust cards and the terminal-outcome
  percentile table for the Monte Carlo tab. Data-role prefix and the dollar
  column label arrive via the model's `:chrome` so the portfolio and vault
  surfaces share this rail."
  (:require [hyperopen.views.portfolio.montecarlo.format :as fmt]))

(defn- prob-row
  [{:keys [label kind value sub data-role-prefix]}]
  (let [bust? (= kind :bust)]
    [:div {:class ["mc-prob"]
           :data-role (str data-role-prefix "-prob-" (name kind))}
     [:div {:class ["mc-prob-top"]}
      [:span {:class ["mc-prob-label"]}
       [:i {:class ["mc-dot" (if bust? "mc-dot-bust" "mc-dot-goal")]}]
       label]
      [:span {:class ["mc-prob-val" (if bust? "mc-prob-val-bust" "mc-prob-val-goal")]}
       (fmt/unsigned-pct value 1)]]
     [:div {:class ["mc-prob-bar"]}
      [:i {:class [(if bust? "mc-prob-fill-bust" "mc-prob-fill-goal")]
           :style {:width (str (max 2 (* value 100)) "%")}}]]
     [:div {:class ["mc-prob-sub"]} sub]]))

(defn prob-card
  [{:keys [result controls chrome]}]
  (let [{:keys [sims bust goal]} controls
        prefix (:data-role-prefix chrome)
        goal-prob (:goal-prob result)
        bust-prob (:bust-prob result)
        goal-n (js/Math.round (* goal-prob sims))
        bust-n (js/Math.round (* bust-prob sims))
        total (.toLocaleString sims)]
    [:div {:class ["mc-card" "mc-prob-card"]
           :data-role (str prefix "-probabilities")}
     (prob-row {:label "Probability of goal"
                :kind :goal
                :value goal-prob
                :sub (str "≥ " goal "% return · " goal-n " of " total " paths")
                :data-role-prefix prefix})
     (prob-row {:label "Probability of bust"
                :kind :bust
                :value bust-prob
                :sub (str "≤ " bust "% drawdown · " bust-n " of " total " paths")
                :data-role-prefix prefix})]))

(defn percentile-table
  [{:keys [result controls live-equity chrome]}]
  (let [term (:terminal result)
        rows [["P5 — Worst case" (:p5 term)]
              ["P25" (:p25 term)]
              ["P50 — Median" (:p50 term)]
              ["P75" (:p75 term)]
              ["P95 — Best case" (:p95 term)]]]
    [:div {:class ["mc-card" "mc-card-pad"]
           :data-role (str (:data-role-prefix chrome) "-terminal-table")}
     [:table {:class ["mc-pct-table"]}
      [:caption {:class ["mc-caption"]}
       (str "Terminal outcome · day " (:horizon controls))]
      [:thead
       [:tr
        [:th "Percentile"]
        [:th (or (:equity-label chrome) "Ending equity")]
        [:th "Return"]]]
      [:tbody
       (map-indexed
        (fn [i [label v]]
          ^{:key label}
          [:tr {:class (when (= i 2) ["mc-row-highlight"])}
           [:td {:class ["mc-row-label"]} label]
           [:td (fmt/usd (* live-equity (+ 1 v)))]
           [:td {:class [(if (>= v 0) "mc-pos" "mc-neg")]}
            (fmt/signed-pct v 1)]])
        rows)]]]))
