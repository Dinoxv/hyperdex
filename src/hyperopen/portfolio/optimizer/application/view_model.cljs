(ns hyperopen.portfolio.optimizer.application.view-model
  (:require [hyperopen.portfolio.optimizer.application.current-portfolio :as current-portfolio]
            [hyperopen.portfolio.optimizer.application.run-identity :as run-identity]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.application.universe-candidates :as universe-candidates]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]
            [hyperopen.portfolio.optimizer.query-state :as optimizer-query-state]
            [hyperopen.portfolio.routes :as portfolio-routes]))

(def ^:private normalized-text coercion/non-blank-text)

(def ^:private missing-history-warning-codes
  #{:missing-history-coin
    :missing-candle-history
    :missing-vault-address
    :missing-vault-history})

(def ^:private insufficient-history-warning-codes
  #{:insufficient-candle-history
    :insufficient-vault-history})

(defn- active-tab
  [state]
  (optimizer-query-state/normalize-results-tab
   (get-in state contracts/ui-results-tab-path)))

(defn- loaded-scenario-matches-route?
  [state scenario-id]
  (= scenario-id
     (get-in state contracts/active-scenario-loaded-id-path)))

(defn- pending-route-load?
  [state scenario-id]
  (let [load-state (get-in state contracts/scenario-load-state-path)]
    (and (= scenario-id (:scenario-id load-state))
         (= :loading (:status load-state)))))

(defn- retained-unsaved-run?
  [state]
  (and (nil? (get-in state contracts/active-scenario-loaded-id-path))
       (some? (get-in state contracts/last-successful-run-path))))

(defn- retained-unsaved-route?
  [state scenario-id]
  (and (retained-unsaved-run? state)
       (contains? #{"draft"}
                  scenario-id)))

(defn route-mismatched?
  [state scenario-id]
  (let [loaded-id (get-in state contracts/active-scenario-loaded-id-path)]
    (and (not (retained-unsaved-route? state scenario-id))
         (or (and (some? loaded-id)
                  (not= loaded-id scenario-id))
             (and (nil? loaded-id)
                  (or (pending-route-load? state scenario-id)
                      (retained-unsaved-run? state)))))))

(defn scenario-scoped-state
  [state scenario-id]
  (if (route-mismatched? state scenario-id)
    (-> state
        (assoc-in contracts/draft-path (optimizer-defaults/default-draft))
        (assoc-in contracts/last-successful-run-path nil)
        (assoc-in contracts/tracking-path (optimizer-defaults/default-tracking-state))
        (assoc-in contracts/active-scenario-path
                  {:loaded-id nil
                   :status :loading
                   :read-only? true}))
    state))

(defn scenario-name
  [state scenario-id]
  (or (when (loaded-scenario-matches-route? state scenario-id)
        (or (get-in state contracts/active-scenario-name-path)
            (get-in state contracts/draft-name-path)))
      (when (retained-unsaved-route? state scenario-id)
        (or (get-in state contracts/draft-name-path)
            "Unsaved Optimization"))
      (when (= scenario-id (get-in state contracts/draft-id-path))
        (get-in state contracts/draft-name-path))
      (str "Scenario " scenario-id)))

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

(defn scenario-detail-model
  [state route]
  (let [scenario-id (:scenario-id route)
        loading? (route-mismatched? state scenario-id)
        state* (scenario-scoped-state state scenario-id)
        selected-tab (active-tab state)
        readiness (setup-readiness/build-readiness state*)
        current-result?* (current-result? state* readiness)
        stale? (scenario-stale? state* readiness)]
    {:state state*
     :route route
     :scenario-id scenario-id
     :loading? loading?
     :selected-tab selected-tab
     :readiness readiness
     :current-result? (boolean current-result?*)
     :stale? (boolean stale?)
     :scenario-name (scenario-name state* scenario-id)
     :draft (optimizer-draft state*)
     :result (result state*)
     :last-successful-run (get-in state* contracts/last-successful-run-path)
     :active-scenario (get-in state* contracts/active-scenario-path)
     :run-state (get-in state* contracts/run-state-path)
     :scenario-save-state (get-in state* contracts/scenario-save-state-path)
     :frontier-overlay-mode (get-in state* contracts/ui-frontier-overlay-mode-path)
     :constrain-frontier? (get-in state* contracts/ui-constrain-frontier-path)}))

(defn- instrument-ids
  [instruments]
  (into #{} (keep :instrument-id) instruments))

(defn- warning-by-instrument-id
  [readiness instrument-id]
  (some (fn [warning]
          (when (= instrument-id (:instrument-id warning))
            warning))
        (:blocking-warnings readiness)))

(defn- history-rows
  [state instrument]
  (if (= :vault (:market-type instrument))
    (get-in state (conj contracts/history-data-path
                        :vault-details-by-address
                        (:vault-address instrument)))
    (get-in state (conj contracts/history-data-path
                        :candle-history-by-coin
                        (:coin instrument)))))

(defn selected-history-label
  [state readiness history-load-state history-status-by-id instrument]
  (let [instrument-id (:instrument-id instrument)
        prefetch-status (get-in state
                                (conj contracts/history-prefetch-path
                                      :by-instrument-id
                                      instrument-id
                                      :status))
        loading-ids (instrument-ids (get-in history-load-state
                                            [:request-signature :universe]))
        eligible-ids (instrument-ids (get-in readiness [:request :universe]))
        readiness-status (get history-status-by-id instrument-id)
        warning (warning-by-instrument-id readiness instrument-id)
        warning-code (:code warning)
        load-validated? (and (= :succeeded (:status history-load-state))
                             (contains? loading-ids instrument-id))
        cached-history? (seq (history-rows state instrument))]
    (cond
      (= :queued prefetch-status)
      "queued"

      (= :loading prefetch-status)
      "loading"

      (and (= :loading (:status history-load-state))
           (contains? loading-ids instrument-id))
      "loading"

      (= :loaded-but-misaligned readiness-status)
      "shared gap"

      (= :aligned readiness-status)
      "sufficient"

      (contains? eligible-ids instrument-id)
      "sufficient"

      (and (= :insufficient readiness-status)
           (or load-validated? cached-history?))
      "insufficient"

      (and (contains? insufficient-history-warning-codes warning-code)
           (or load-validated? cached-history?))
      "insufficient"

      (and (= :missing readiness-status)
           load-validated?)
      "missing"

      (and (contains? missing-history-warning-codes warning-code)
           load-validated?)
      "missing"

      :else
      "pending")))

(defn universe-section-model
  ([state draft]
   (universe-section-model state draft nil))
  ([state draft {:keys [readiness history-load-state history-status-by-id]}]
   (let [universe (vec (or (:universe draft) []))
         history-load-state* (or history-load-state
                                 (get-in state contracts/history-load-state-path)
                                 (optimizer-defaults/default-history-load-state))
         history-status-by-id* (or history-status-by-id {})
         search-query (or (get-in state contracts/ui-universe-search-query-path) "")
         searching? (boolean (seq (normalized-text search-query)))
         markets (if searching?
                   (universe-candidates/candidate-markets state universe search-query)
                   [])
         active-index (universe-candidates/active-index state markets)
         market-keys (if searching?
                       (mapv :key markets)
                       [])]
     {:state state
      :draft draft
      :readiness readiness
      :history-load-state history-load-state*
      :history-status-by-id history-status-by-id*
      :universe universe
      :search-query search-query
      :searching? searching?
      :markets markets
      :active-index active-index
      :market-keys market-keys})))
