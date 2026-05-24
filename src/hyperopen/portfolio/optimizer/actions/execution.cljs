(ns hyperopen.portfolio.optimizer.actions.execution
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.portfolio.optimizer.actions.common :as common]
            [hyperopen.portfolio.optimizer.application.execution :as execution]
            [hyperopen.portfolio.optimizer.application.rebalance-preview :as rebalance-preview]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]))

(defn open-portfolio-optimizer-execution-modal
  [state]
  (let [readiness (setup-readiness/build-readiness state)
        last-successful-run
        (rebalance-preview/last-successful-run-with-rebalance-preview
         (:request readiness)
         (get-in state contracts/last-successful-run-path))
        result (:result last-successful-run)
        preview (:rebalance-preview result)]
    (if (and (= :solved (:status result))
             (map? preview))
      [[:effects/save
        contracts/execution-modal-path
        {:open? true
         :plan (execution/build-execution-plan
                {:scenario-id (common/current-scenario-id state)
                 :rebalance-preview preview
                 :execution-assumptions (get-in state
                                                contracts/draft-execution-assumptions-path)
                 :mutations-blocked-message
                 (account-context/mutations-blocked-message state)})}]]
      [])))

(defn close-portfolio-optimizer-execution-modal
  [_state]
  [[:effects/save
    contracts/execution-modal-path
    (optimizer-defaults/default-execution-modal-state)]])

(defn confirm-portfolio-optimizer-execution
  [state]
  (let [modal (get-in state contracts/execution-modal-path)
        plan (:plan modal)
        ready-count (get-in plan [:summary :ready-count])]
    (cond
      (not (map? plan))
      []

      (:submitting? modal)
      []

      (:execution-disabled? plan)
      [[:effects/save
        contracts/execution-modal-error-path
        (or (:disabled-message plan)
            "Execution is disabled for this scenario.")]]

      (not (pos? (or ready-count 0)))
      [[:effects/save
        contracts/execution-modal-error-path
        "No executable rows are ready."]]

      :else
      [[:effects/save contracts/execution-modal-submitting-path true]
       [:effects/save contracts/execution-modal-error-path nil]
       [:effects/execute-portfolio-optimizer-plan plan]])))
