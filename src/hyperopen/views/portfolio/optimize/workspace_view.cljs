(ns hyperopen.views.portfolio.optimize.workspace-view
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]
            [hyperopen.views.portfolio.optimize.execution-modal :as execution-modal]
            [hyperopen.views.portfolio.optimize.infeasible-panel :as infeasible-panel]
            [hyperopen.views.portfolio.optimize.setup-v4-context :as setup-v4-context]
            [hyperopen.views.portfolio.optimize.setup-v4-header :as setup-v4-header]
            [hyperopen.views.portfolio.optimize.setup-v4-sections :as setup-v4]))

(defn workspace-view
  [state route]
  (let [{:keys [snapshot
                draft
                readiness
                preview-snapshot
                run-state
                optimization-progress
                running?
                run-triggerable?
                last-successful-run
                current-result?
                saving-scenario?
                history-load-state
                scenario-id
                result-path
                editor-state]} (optimizer-view-model/workspace-model state route)
        infeasible-result (infeasible-panel/infeasible-result run-state)
        highlighted-controls (infeasible-panel/highlighted-control-keys infeasible-result)]
    [:section {:class ["portfolio-optimizer-v4" "optimizer-setup-route-surface"
                       "space-y-3" "pb-16" "leading-4" "text-trading-text"]
               :data-role "portfolio-optimizer-setup-route-surface"
               :data-scenario-id scenario-id}
     (setup-v4-header/setup-header {:draft draft
                                    :route route
                                    :running? running?
                                    :run-triggerable? run-triggerable?
                                    :saving-scenario? saving-scenario?
                                    :solved-run? current-result?
                                    :result-path result-path})
     (setup-v4-header/preset-row draft)
     (infeasible-panel/infeasible-banner infeasible-result highlighted-controls)
     [:section {:class ["optimizer-setup-surface"
                        "grid"
                        "grid-cols-1"
                        "gap-5"
                        "xl:gap-6"
                        "xl:grid-cols-[minmax(420px,7fr)_minmax(0,11fr)_minmax(360px,6fr)]"]
                :data-role "portfolio-optimizer-setup-surface"}
      (setup-v4/control-rail {:state state
                              :draft draft
                              :highlighted-controls highlighted-controls
                              :readiness readiness
                              :history-load-state history-load-state})
      (setup-v4/summary-pane {:draft draft
                              :readiness readiness
                              :running? running?
                              :run-triggerable? run-triggerable?
                              :saving-scenario? saving-scenario?
                              :solved-run? current-result?
                              :result-path result-path})
      (setup-v4-context/context-rail {:draft draft
                                      :editor-state editor-state
                                      :readiness readiness
                                      :snapshot snapshot
                                      :preview-snapshot preview-snapshot
                                      :run-state run-state
                                      :optimization-progress optimization-progress
                                      :history-load-state history-load-state
                                      :last-successful-run last-successful-run
                                      :current-result? current-result?
                                      :result-path result-path})]
     (execution-modal/execution-modal state)]))
