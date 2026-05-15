(ns hyperopen.portfolio.optimizer.actions.run
  (:require [hyperopen.portfolio.optimizer.actions.common :as action-common]
            [hyperopen.portfolio.optimizer.application.run-identity :as run-identity]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.black-litterman-actions.common :as bl-common]
            [hyperopen.portfolio.optimizer.black-litterman-actions.editor-model :as bl-editor-model]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.query-state :as optimizer-query-state]
            [hyperopen.portfolio.routes :as portfolio-routes]))

(defn set-portfolio-optimizer-results-tab
  [_state tab]
  [[:effects/save
    contracts/ui-results-tab-path
    (optimizer-query-state/normalize-results-tab tab)]
   [:effects/replace-shareable-route-query]])

(defn load-portfolio-optimizer-history-from-draft
  [state]
  (if (seq (get-in state contracts/draft-universe-path))
    [[:effects/load-portfolio-optimizer-history]]
    []))

(defn- run-pipeline-effect
  []
  [:effects/run-portfolio-optimizer-pipeline])

(defn- black-litterman-run-effects
  [state]
  (let [pending (bl-editor-model/pending-editor-view-result state)
        active-views (bl-common/black-litterman-views state)]
    (case (:status pending)
      :valid
      (conj (bl-common/save-draft-path-values
             (bl-editor-model/materialized-view-path-values pending))
            (run-pipeline-effect))

      :invalid
      (bl-common/save-ui-path-values
       (bl-editor-model/error-path-values (:errors pending)))

      (if (seq active-views)
        [(run-pipeline-effect)]
        (bl-common/save-ui-path-values
         (bl-editor-model/missing-view-error-path-values))))))

(defn run-portfolio-optimizer-from-draft
  [state]
  (if (seq (get-in state contracts/draft-universe-path))
    (if (bl-common/black-litterman-return-model? state)
      (black-litterman-run-effects state)
      [(run-pipeline-effect)])
    []))

(defn run-portfolio-optimizer-from-ready-draft
  [state]
  (let [{:keys [request runnable?]} (setup-readiness/build-readiness state)]
    (if runnable?
      [[:effects/run-portfolio-optimizer
        request
        (action-common/build-request-signature request)]]
      [])))

(defn- optimizer-running?
  [state]
  (or (= :running (get-in state contracts/run-state-status-path))
      (= :running (get-in state contracts/optimization-progress-status-path))))

(defn save-portfolio-optimizer-scenario-from-current
  [state]
  (if (run-identity/current-solved-run?
       {:draft (get-in state contracts/draft-path)
        :readiness (setup-readiness/build-readiness state)
        :run-state (get-in state contracts/run-state-path)
        :running? (optimizer-running? state)
        :last-successful-run (get-in state contracts/last-successful-run-path)})
    [[:effects/save-portfolio-optimizer-scenario]]
    []))

(defn- asset-selector-market-fetch-effects
  [state]
  (if (= :full (get-in state [:asset-selector :phase]))
    []
    [[:effects/fetch-asset-selector-markets {:phase :full}]]))

(defn- history-discovery-effects
  [route]
  (if (contains? #{:optimize-index :optimize-new :optimize-scenario}
                 (:kind route))
    [[:effects/load-portfolio-optimizer-history-discovery]]
    []))

(defn load-portfolio-optimizer-route
  [state path]
  (let [route (portfolio-routes/parse-portfolio-route path)]
    (into
     (case (:kind route)
       :optimize-index [[:effects/load-portfolio-optimizer-scenario-index]]
       :optimize-scenario [[:effects/load-portfolio-optimizer-scenario
                            (:scenario-id route)]]
       [])
      (if (contains? #{:optimize-index :optimize-new :optimize-scenario}
                    (:kind route))
       (into (history-discovery-effects route)
             (into (asset-selector-market-fetch-effects state)
                   (action-common/vault-list-metadata-fetch-effects state)))
       []))))

(defn archive-portfolio-optimizer-scenario
  [_state scenario-id]
  (action-common/scenario-id-effect
   :effects/archive-portfolio-optimizer-scenario
   scenario-id))

(defn duplicate-portfolio-optimizer-scenario
  [_state scenario-id]
  (action-common/scenario-id-effect
   :effects/duplicate-portfolio-optimizer-scenario
   scenario-id))

(defn run-portfolio-optimizer
  [_state request request-signature]
  [[:effects/run-portfolio-optimizer request request-signature]])
