(ns hyperopen.views.portfolio.optimize.results-panel
  (:require [hyperopen.views.portfolio.optimize.frontier-chart :as frontier-chart]
            [hyperopen.views.portfolio.optimize.results-diagnostics-rail :as diagnostics-rail]
            [hyperopen.views.portfolio.optimize.results-model :as results-model]
            [hyperopen.views.portfolio.optimize.results-rebalance-preview :as rebalance-preview]
            [hyperopen.views.portfolio.optimize.results-summary :as summary]
            [hyperopen.views.portfolio.optimize.target-exposure-table :as target-exposure-table]))

(defn results-panel
  ([last-successful-run]
   (results-panel last-successful-run nil))
  ([last-successful-run draft]
   (results-panel last-successful-run draft nil))
  ([last-successful-run draft {:keys [stale? include-rebalance? frontier-overlay-mode
                                      constrain-frontier?]
                               :or {include-rebalance? true
                                    frontier-overlay-mode :standalone}}]
   (let [result (results-model/enrich-result-labels (:result last-successful-run) draft)]
     (when (= :solved (:status result))
       [:section {:class ["optimizer-results-surface" "space-y-0" "leading-4"]
                  :data-role "portfolio-optimizer-results-surface"}
        (summary/stale-result-banner stale?)
        [:div {:class ["optimizer-results-grid" "grid" "grid-cols-1" "xl:grid-cols-[500px_minmax(0,1fr)_320px]"]
               :data-role "portfolio-optimizer-results-grid"}
         [:div {:class ["optimizer-results-left-panel" "min-h-0" "space-y-0"]
                :data-role "portfolio-optimizer-results-left-panel"}
          (target-exposure-table/target-exposure-table result)]
         [:div {:class ["optimizer-results-center-panel" "min-h-0" "bg-base-100" "p-6"]
                :data-role "portfolio-optimizer-results-center-panel"}
          (frontier-chart/frontier-chart
           draft
           result
           frontier-overlay-mode
           constrain-frontier?)]
         [:div {:class ["optimizer-results-right-panel" "min-h-0"]
                :data-role "portfolio-optimizer-results-right-panel"}
          (diagnostics-rail/trust-diagnostics-rail result)]]
        (when include-rebalance?
          (rebalance-preview/rebalance-preview result))]))))
