(ns hyperopen.portfolio.optimizer.application.view-model.workspace
  (:require [hyperopen.portfolio.optimizer.application.current-portfolio :as current-portfolio]
            [hyperopen.portfolio.optimizer.application.run-identity :as run-identity]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]
            [hyperopen.portfolio.routes :as portfolio-routes]))

(defn optimizer-draft
  [state]
  (or (get-in state contracts/draft-path)
      (optimizer-defaults/default-draft)))

(defn optimizer-running?
  [state]
  (or (= :running (get-in state contracts/run-state-status-path))
      (= :running (get-in state contracts/optimization-progress-status-path))))

(defn result
  [state]
  (get-in state contracts/last-successful-run-result-path))

(defn solved-result?
  [state]
  (= :solved (:status (result state))))

(defn scenario-stale?
  [state readiness]
  (run-identity/stale-run?
   {:draft (optimizer-draft state)
    :readiness readiness
    :run-state (get-in state contracts/run-state-path)
    :running? (optimizer-running? state)
    :last-successful-run (get-in state contracts/last-successful-run-path)}))

(defn current-result?
  [state readiness]
  (run-identity/current-solved-run?
   {:draft (optimizer-draft state)
    :readiness readiness
    :run-state (get-in state contracts/run-state-path)
    :running? (optimizer-running? state)
    :last-successful-run (get-in state contracts/last-successful-run-path)}))

(defn- retained-result-path
  [state]
  (portfolio-routes/portfolio-optimize-scenario-path
   (or (get-in state contracts/active-scenario-loaded-id-path)
       "draft")))

(defn workspace-model
  [state route]
  (let [snapshot (current-portfolio/current-portfolio-snapshot state)
        draft (optimizer-draft state)
        readiness (setup-readiness/build-readiness state)
        preview-snapshot (or (get-in readiness [:request :current-portfolio])
                             snapshot)
        run-state (or (get-in state contracts/run-state-path)
                      (optimizer-defaults/default-run-state))
        optimization-progress (or (get-in state contracts/optimization-progress-path)
                                  (optimizer-defaults/default-optimization-progress-state))
        progress-running? (= :running (:status optimization-progress))
        running? (or (= :running (:status run-state))
                     progress-running?)
        run-triggerable? (and (seq (:universe draft))
                              (not running?))
        last-successful-run (get-in state contracts/last-successful-run-path)
        current-result?* (run-identity/current-solved-run?
                          {:draft draft
                           :readiness readiness
                           :run-state run-state
                           :running? running?
                           :last-successful-run last-successful-run})
        scenario-save-state (or (get-in state contracts/scenario-save-state-path)
                                (optimizer-defaults/default-scenario-save-state))
        history-load-state (or (get-in state contracts/history-load-state-path)
                               (optimizer-defaults/default-history-load-state))]
    {:state state
     :route route
     :scenario-id (:scenario-id route)
     :snapshot snapshot
     :draft draft
     :readiness readiness
     :preview-snapshot preview-snapshot
     :run-state run-state
     :optimization-progress optimization-progress
     :progress-running? progress-running?
     :running? running?
     :run-triggerable? (boolean run-triggerable?)
     :last-successful-run last-successful-run
     :current-result? (boolean current-result?*)
     :solved-run? (boolean current-result?*)
     :scenario-save-state scenario-save-state
     :saving-scenario? (= :saving (:status scenario-save-state))
     :history-load-state history-load-state
     :editor-state (get-in state contracts/ui-black-litterman-editor-path)
     :result-path (retained-result-path state)}))
