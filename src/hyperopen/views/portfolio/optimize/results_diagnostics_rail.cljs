(ns hyperopen.views.portfolio.optimize.results-diagnostics-rail
  (:require [hyperopen.portfolio.optimizer.application.view-model.results :as results-model]
            [hyperopen.views.portfolio.optimize.format :as opt-format]
            [hyperopen.views.portfolio.optimize.results-summary :as summary]))

(defn- binding-constraint-row
  [labels-by-instrument binding]
  [:div {:class ["rounded-md" "border" "border-warning/40" "bg-warning/10" "p-2" "text-xs" "text-warning"]}
   [:span {:class ["font-semibold"]}
    (results-model/instrument-label labels-by-instrument (:instrument-id binding))]
   [:span {:class ["ml-2"]} (opt-format/keyword-label (:constraint binding))]])

(defn- sensitivity-row
  [labels-by-instrument [instrument-id row]]
  [:div {:class ["optimizer-row"
                 "rounded-md" "border" "border-base-300" "bg-base-200/40" "p-2" "text-xs"]
         :data-role (str "portfolio-optimizer-sensitivity-row-" instrument-id)}
   [:span {:class ["font-semibold"]} (results-model/instrument-label labels-by-instrument instrument-id)]
   [:span {:class ["ml-2" "text-trading-muted"]}
    (str "Base " (opt-format/format-pct (:base-expected-return row))
         " / Down " (opt-format/format-pct (:down-expected-return row))
         " / Up " (opt-format/format-pct (:up-expected-return row)))]])

(defn warning-row
  [warning]
  [:p {:class ["rounded-md" "border" "border-warning/40" "bg-warning/10" "p-2" "text-xs" "text-warning"]
       :data-role "portfolio-optimizer-result-warning"}
   [:span {:class ["font-semibold"]} (opt-format/keyword-label (:code warning))]
   (when-let [message (:message warning)]
     [:span {:class ["ml-2"]} message])])

(defn warnings-panel
  [result]
  (when (seq (:warnings result))
    (summary/panel-shell
     "portfolio-optimizer-result-warnings"
     "Result Warnings"
     "Warnings explain assumptions or mathematically valid outcomes that may require a rerun with different controls."
     (map warning-row (:warnings result)))))

(defn diagnostics-panel
  [result]
  (let [diagnostics (:diagnostics result)
        labels-by-instrument (or (:labels-by-instrument result) {})
        bindings (:binding-constraints diagnostics)
        conditioning (:covariance-conditioning diagnostics)
        sensitivity (:weight-sensitivity-by-instrument diagnostics)]
    (summary/panel-shell
     "portfolio-optimizer-diagnostics-panel"
     "Diagnostics"
     "Engine diagnostics are rendered from the run result, not recomputed in the view."
     [:div {:class ["grid" "grid-cols-2" "gap-2" "lg:grid-cols-4"]}
      (summary/summary-card "Gross" (opt-format/format-pct (:gross-exposure diagnostics)))
      (summary/summary-card "Net" (opt-format/format-pct (:net-exposure diagnostics)))
      (summary/summary-card "Effective N" (opt-format/format-decimal (:effective-n diagnostics)))
      (summary/summary-card "Turnover" (opt-format/format-pct (:turnover diagnostics)))]
     [:div {:class ["grid" "grid-cols-1" "gap-2" "lg:grid-cols-3"]}
      (summary/summary-card "Condition" (opt-format/keyword-label (:status conditioning)))
      (summary/summary-card "Condition #"
                            (opt-format/format-decimal (:condition-number conditioning)))
      (summary/summary-card "Min Eigen"
                            (opt-format/format-decimal (:min-eigenvalue conditioning)))]
     [:div {:class ["rounded-lg" "border" "border-base-300" "bg-base-200/40" "p-3"]}
      [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.18em]" "text-trading-muted"]}
       "Binding Constraints"]
      (if (seq bindings)
        (into [:div {:class ["mt-2" "space-y-2"]}]
              (map (partial binding-constraint-row labels-by-instrument) bindings))
        [:p {:class ["mt-2" "text-xs" "text-trading-muted"]}
         "No binding constraints reported."])]
     [:div {:class ["rounded-lg" "border" "border-base-300" "bg-base-200/40" "p-3"]
            :data-role "portfolio-optimizer-sensitivity-panel"}
      [:p {:class ["text-[0.65rem]" "font-semibold" "uppercase" "tracking-[0.18em]" "text-trading-muted"]}
       "Weight Sensitivity"]
      (if (seq sensitivity)
        (into [:div {:class ["mt-2" "space-y-2"]}]
              (map (partial sensitivity-row labels-by-instrument) sensitivity))
        [:p {:class ["mt-2" "text-xs" "text-trading-muted"]}
         "No sensitivity diagnostics reported."])])))

(defn- status-token
  [status]
  (case status
    :ok {:label "ok" :class "text-trading-green"}
    :healthy {:label "ok" :class "text-trading-green"}
    :warning {:label "caution" :class "text-warning"}
    :caution {:label "caution" :class "text-warning"}
    :ill-conditioned {:label "caution" :class "text-warning"}
    :singular {:label "bad" :class "text-trading-red"}
    {:label (opt-format/keyword-label status) :class "text-trading-muted"}))

(defn- top-sensitivity
  [sensitivity]
  (when (seq sensitivity)
    (let [row-span (fn [row]
                     (js/Math.abs
                      (- (or (:up-weight row) (:up-expected-return row) 0)
                         (or (:down-weight row) (:down-expected-return row) 0))))
          [instrument-id row] (->> sensitivity
                                   (sort-by (fn [[_ row*]]
                                              (- (row-span row*))))
                                   first)]
      {:instrument-id instrument-id
       :span (row-span row)})))

(defn- trust-row
  [{:keys [label status value subtext]}]
  (let [{status-label :label status-class :class} (status-token status)]
    [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
     [:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
      [:span {:class ["text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]" "text-trading-muted"]}
       label]
      [:span {:class [status-class "text-[0.62rem]" "font-semibold" "uppercase"]}
       (str "● " status-label)]]
     [:p {:class ["mt-1" "font-mono" "text-base" "font-semibold" "tabular-nums" "text-trading-text"]}
      value]
     [:p {:class ["mt-0.5" "text-[0.64rem]" "text-trading-muted/70"]}
      subtext]]))

(defn trust-diagnostics-rail
  [result]
  (let [diagnostics (:diagnostics result)
        conditioning (:covariance-conditioning diagnostics)
        sensitivity (:weight-sensitivity-by-instrument diagnostics)
        sensitivity-top (top-sensitivity sensitivity)
        effective-n (:effective-n diagnostics)
        universe-size (count (:instrument-ids result))
        conditioning-status (or (:status conditioning) :ok)
        weight-stability-status (if sensitivity-top :caution :ok)]
    [:aside {:class ["optimizer-trust-caution-panel"
                     "min-h-0" "border-l" "border-base-300" "bg-base-100/95"]
             :data-role "portfolio-optimizer-trust-caution-panel"}
     [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]}
      [:p {:class ["font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
       "How much to trust this"]]
     [:div {:class ["optimizer-diagnostics-list"]
            :data-role "portfolio-optimizer-diagnostics-panel"}
      (trust-row {:label "Conditioning"
                  :status conditioning-status
                  :value (if (= :ok conditioning-status) "Healthy" (opt-format/keyword-label conditioning-status))
                  :subtext "Correlation matrix is checked before weights are accepted."})
      (trust-row {:label "Diversification"
                  :status :ok
                  :value (str "Effective N · " (opt-format/format-effective-n effective-n universe-size) " of " universe-size)
                  :subtext "Higher effective N means less concentration in one name."})
      (trust-row {:label "Weight Stability"
                  :status weight-stability-status
                  :value (if sensitivity-top "Moderate" "Stable")
                  :subtext (if sensitivity-top
                            (str (results-model/instrument-label (:labels-by-instrument result)
                                                                 (:instrument-id sensitivity-top))
                                 " is most sensitive (±"
                                 (opt-format/format-pct (/ (:span sensitivity-top) 2))
                                 ").")
                            "No material sensitivity flags reported.")})
      (when (seq (:warnings result))
        [:div {:class ["border-b" "border-base-300" "px-4" "py-3"]
               :data-role "portfolio-optimizer-result-warnings"}
         [:p {:class ["text-[0.62rem]" "font-semibold" "uppercase" "tracking-[0.06em]" "text-warning"]}
          "Warnings"]
         (into [:div {:class ["mt-2" "space-y-2"]}]
               (map warning-row (:warnings result)))])
      [:details {:class ["border-b" "border-base-300"]}
       [:summary {:class ["cursor-pointer" "px-4" "py-3" "font-mono" "text-[0.62rem]" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"]}
        "More Diagnostics"]
       [:div {:class ["space-y-2" "px-4" "pb-4"]}
        (summary/summary-card "Gross" (opt-format/format-pct (:gross-exposure diagnostics)))
        (summary/summary-card "Net" (opt-format/format-pct (:net-exposure diagnostics)))
        (summary/summary-card "Turnover" (opt-format/format-pct (:turnover diagnostics)))
        (summary/summary-card "Condition #" (opt-format/format-decimal (:condition-number conditioning)))]]]]))
