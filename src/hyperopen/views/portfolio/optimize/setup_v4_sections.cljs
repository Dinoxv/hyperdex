(ns hyperopen.views.portfolio.optimize.setup-v4-sections
  (:require [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.views.portfolio.optimize.instrument-overrides-panel :as instrument-overrides-panel]
            [hyperopen.views.portfolio.optimize.setup-v4-constraint-controls :as constraint-controls]
            [hyperopen.views.portfolio.optimize.setup-v4-controls :as controls]
            [hyperopen.views.portfolio.optimize.setup-v4-model-controls :as model-controls]
            [hyperopen.views.portfolio.optimize.setup-v4-objective-controls :as objective-controls]
            [hyperopen.views.portfolio.optimize.setup-v4-setup-actions :as setup-actions]
            [hyperopen.views.portfolio.optimize.setup-v4-summary :as setup-v4-summary]
            [hyperopen.views.portfolio.optimize.setup-v4-universe :as setup-v4-universe]
            [hyperopen.views.portfolio.optimize.setup-v4-use-my-views-workspace :as use-my-views-workspace]))

(defn- active-preset
  [draft]
  (let [objective-kind (get-in draft [:objective :kind])
        return-kind (get-in draft [:return-model :kind])]
    (cond
      (= :black-litterman return-kind) :use-my-views
      (= :max-sharpe objective-kind) :risk-adjusted
      :else :conservative)))

(defn control-rail
  [{:keys [state draft highlighted-controls readiness history-load-state]}]
  [:aside {:class ["min-h-0" "overflow-hidden"] :data-role "portfolio-optimizer-setup-control-rail"}
   (setup-v4-universe/universe-section state draft
                                       {:readiness readiness
                                        :history-load-state history-load-state
                                        :history-status-by-id (setup-readiness/history-status-by-instrument
                                                               readiness)})
   (objective-controls/objective-section draft highlighted-controls)
   (model-controls/model-section draft)
   (constraint-controls/constraints-section draft highlighted-controls)
   [:details {:class ["border" "border-base-300" "bg-base-100/90" "p-3"]
              :data-role "portfolio-optimizer-advanced-overrides-shell"}
    [:summary {:class (into ["cursor-pointer" "select-none"] controls/section-title-class)}
     "Advanced Overrides"]
    [:div {:class ["mt-3"]}
     (instrument-overrides-panel/instrument-overrides-panel draft)]]])

(defn- summary-row
  [label title copy]
  [:div {:class ["grid" "grid-cols-[132px_minmax(0,1fr)]" "gap-4" "border-b"
                 "border-base-300" "px-4" "py-3"]}
   [:p {:class controls/eyebrow-class} label]
   [:div
    [:p {:class ["text-[0.6875rem]" "font-medium" "text-trading-text"]} title]
    [:p {:class ["mt-1" "text-[0.6875rem]" "leading-[1.45]" "text-trading-muted"]} copy]]])

(defn setup-bottom-actions
  [opts]
  (setup-actions/setup-bottom-actions opts))

(defn summary-pane
  [{:keys [draft readiness running? run-triggerable? saving-scenario? solved-run? result-path]}]
  (let [preset (active-preset draft)
        objective-kind (get-in draft [:objective :kind])
        return-kind (get-in draft [:return-model :kind])
        constraints (:constraints draft)
        bl? (= :black-litterman return-kind)]
    (into
     [:main {:class ["space-y-4" "leading-4"] :data-role "portfolio-optimizer-setup-summary-pane"}]
     (if bl?
       [(use-my-views-workspace/use-my-views-workspace
         {:draft draft
          :readiness readiness
          :running? running?
          :run-triggerable? run-triggerable?
          :saving-scenario? saving-scenario?
          :solved-run? solved-run?
          :result-path result-path})]
       [[:div {:class ["px-1" "pt-2" "pb-1"]
               :data-role "portfolio-optimizer-setup-summary-heading"}
         [:p {:class controls/eyebrow-class} "Summary"]
         [:h2 {:class ["mt-2" "text-[0.875rem]" "font-medium" "tracking-[-0.01em]"]}
          "What this scenario will solve for"]]
        [:section {:class ["border" "border-base-300" "bg-base-100/90"]
                   :data-role "portfolio-optimizer-setup-summary-panel"}
         (summary-row "Preset" (controls/labelize preset)
                      "You can deviate from the preset below without changing the universe.")
         (summary-row "Universe" (setup-v4-summary/universe-summary draft)
                      "Selected instruments are optimized as one cross-margin book.")
         (summary-row "Expected Returns" (controls/labelize return-kind)
                      "Funding-adjusted return assumptions are kept separate from covariance.")
         (summary-row "Objective" (controls/labelize objective-kind)
                      "Objective remains separate from return model selection.")
         (summary-row "Constraints"
                      (str "gross <= " (or (:gross-max constraints) "--")
                           " - cap <= " (controls/percent-label (:max-asset-weight constraints)))
                      "Constraints are enforced before the recommendation is accepted.")
         (summary-row "Horizon" "Annualized"
                      "Displayed return and volatility metrics use the optimizer annualization convention.")]
        [:div {:class ["space-y-2"]
               :data-role "portfolio-optimizer-model-assumptions-stack"}
         (setup-actions/model-assumptions-panel)
         (setup-actions/setup-bottom-actions {:draft draft
                                              :running? running?
                                              :run-triggerable? run-triggerable?
                                              :saving-scenario? saving-scenario?
                                              :solved-run? solved-run?
                                              :result-path result-path})]]))))
