(ns hyperopen.portfolio.optimizer.application.view-model
  (:require [hyperopen.portfolio.optimizer.application.view-model.execution :as execution]
            [hyperopen.portfolio.optimizer.application.view-model.index :as index]
            [hyperopen.portfolio.optimizer.application.view-model.scenario :as scenario]
            [hyperopen.portfolio.optimizer.application.view-model.tracking :as tracking]
            [hyperopen.portfolio.optimizer.application.view-model.universe :as universe]
            [hyperopen.portfolio.optimizer.application.view-model.workspace :as workspace]))

(defn index-model
  [state]
  (index/index-model state))

(defn route-mismatched?
  [state scenario-id]
  (scenario/route-mismatched? state scenario-id))

(defn scenario-scoped-state
  [state scenario-id]
  (scenario/scenario-scoped-state state scenario-id))

(defn scenario-name
  [state scenario-id]
  (scenario/scenario-name state scenario-id))

(defn optimizer-draft
  [state]
  (workspace/optimizer-draft state))

(defn optimizer-running?
  [state]
  (workspace/optimizer-running? state))

(defn result
  [state]
  (workspace/result state))

(defn solved-result?
  [state]
  (workspace/solved-result? state))

(defn scenario-stale?
  [state readiness]
  (workspace/scenario-stale? state readiness))

(defn current-result?
  [state readiness]
  (workspace/current-result? state readiness))

(defn workspace-model
  [state route]
  (workspace/workspace-model state route))

(defn scenario-detail-model
  [state route]
  (scenario/scenario-detail-model state route))

(defn selected-history-status
  [state readiness history-load-state history-status-by-id instrument]
  (universe/selected-history-status state
                                    readiness
                                    history-load-state
                                    history-status-by-id
                                    instrument))

(defn selected-history-label
  [state readiness history-load-state history-status-by-id instrument]
  (universe/selected-history-label state
                                   readiness
                                   history-load-state
                                   history-status-by-id
                                   instrument))

(defn universe-section-model
  ([state draft]
   (universe/universe-section-model state draft))
  ([state draft opts]
   (universe/universe-section-model state draft opts)))

(defn universe-panel-model
  [state draft]
  (universe/universe-panel-model state draft))

(defn tracking-model
  [state]
  (tracking/tracking-model state))

(defn execution-modal-model
  [state]
  (execution/execution-modal-model state))

(defn inputs-audit-model
  [state]
  (scenario/inputs-audit-model state))
