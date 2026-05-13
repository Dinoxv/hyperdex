(ns hyperopen.portfolio.optimizer.application.view-model.index
  (:require [hyperopen.portfolio.optimizer.application.scenario-state :as scenario-state]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn- scenario-index
  [state]
  (or (get-in state contracts/scenario-index-path)
      (scenario-state/default-scenario-index)))

(defn- scenario-summaries
  [state]
  (let [{:keys [ordered-ids by-id]} (scenario-index state)]
    (vec (keep #(get by-id %) ordered-ids))))

(defn index-model
  [state]
  {:scenario-summaries (scenario-summaries state)})
