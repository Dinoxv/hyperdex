(ns hyperopen.portfolio.optimizer.application.view-model
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.current-portfolio :as current-portfolio]
            [hyperopen.portfolio.optimizer.application.run-identity :as run-identity]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.application.universe-candidates :as universe-candidates]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]
            [hyperopen.portfolio.optimizer.ids :as ids]
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

(def ^:private trackable-statuses
  #{:executed :partially-executed :tracking})

(def ^:private manual-tracking-source-statuses
  #{:saved :computed})

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

(defn- labels-by-instrument
  [state]
  (or (get-in state (conj contracts/last-successful-run-result-path
                          :labels-by-instrument))
      {}))

(defn- instrument-label
  [labels-by-instrument instrument-id]
  (let [value (str instrument-id)]
    (if (ids/vault-instrument-id? value)
      (or (get labels-by-instrument instrument-id)
          value)
      value)))

(defn- enrich-instrument-row
  [labels-by-instrument row]
  (assoc row :instrument-label (instrument-label labels-by-instrument
                                                 (:instrument-id row))))

(defn- enrich-instrument-rows
  [labels-by-instrument rows]
  (mapv (partial enrich-instrument-row labels-by-instrument)
        (or rows [])))

(defn- enrich-snapshot
  [labels-by-instrument snapshot]
  (when snapshot
    (assoc snapshot :rows (enrich-instrument-rows labels-by-instrument
                                                  (:rows snapshot)))))

(defn- latest-record
  [records]
  (last (vec records)))

(defn- active-scenario-id
  [state]
  (or (get-in state contracts/active-scenario-loaded-id-path)
      (get-in state contracts/draft-id-path)))

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
  ([state draft {:keys [readiness
                        history-load-state
                        history-status-by-id
                        candidate-options
                        include-blank-candidates?]}]
   (let [universe (vec (or (:universe draft) []))
         history-load-state* (or history-load-state
                                 (get-in state contracts/history-load-state-path)
                                 (optimizer-defaults/default-history-load-state))
         history-status-by-id* (or history-status-by-id {})
         search-query (or (get-in state contracts/ui-universe-search-query-path) "")
         searching? (boolean (seq (normalized-text search-query)))
         query-candidates? (or searching?
                               include-blank-candidates?)
         markets (if query-candidates?
                   (universe-candidates/candidate-markets state
                                                          universe
                                                          search-query
                                                          candidate-options)
                   [])
         active-index (universe-candidates/active-index state markets)
         market-keys (if query-candidates?
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

(defn universe-panel-model
  [state draft]
  (universe-section-model state
                          draft
                          {:candidate-options {:ranking :asset-query}
                           :include-blank-candidates? true}))

(defn tracking-model
  [state]
  (let [scenario-status (get-in state contracts/active-scenario-status-path)
        tracking-record (get-in state contracts/tracking-path)
        labels-by-instrument* (labels-by-instrument state)
        latest-snapshot (enrich-snapshot labels-by-instrument*
                                         (latest-record (:snapshots tracking-record)))
        active-id (active-scenario-id state)]
    {:scenario-status scenario-status
     :tracking-record tracking-record
     :snapshots (:snapshots tracking-record)
     :latest-snapshot latest-snapshot
     :latest-rows (vec (:rows latest-snapshot))
     :labels-by-instrument labels-by-instrument*
     :active-scenario-id active-id
     :manual-tracking? (contains? manual-tracking-source-statuses scenario-status)
     :trackable? (contains? trackable-statuses scenario-status)
     :manual-tracking-enableable? (some? active-id)}))

(defn- enrich-execution-attempt
  [labels-by-instrument attempt]
  (when attempt
    (assoc attempt :rows (enrich-instrument-rows labels-by-instrument
                                                 (:rows attempt)))))

(defn execution-modal-model
  [state]
  (let [modal (or (get-in state contracts/execution-modal-path) {})
        plan (or (:plan modal) {})
        summary (:summary plan)
        labels-by-instrument* (labels-by-instrument state)
        latest-attempt (enrich-execution-attempt
                        labels-by-instrument*
                        (latest-record (get-in state contracts/execution-history-path)))
        submitting? (boolean (:submitting? modal))
        ready? (pos? (or (:ready-count summary) 0))
        confirm-disabled? (or submitting?
                              (:execution-disabled? plan)
                              (not ready?))
        plan* (assoc plan
                     :rows (enrich-instrument-rows labels-by-instrument*
                                                   (:rows plan)))]
    {:modal modal
     :open? (boolean (:open? modal))
     :plan plan*
     :summary summary
     :latest-attempt latest-attempt
     :labels-by-instrument labels-by-instrument*
     :submitting? submitting?
     :ready? ready?
     :confirm-disabled? (boolean confirm-disabled?)
     :disabled-message (or (:disabled-message plan)
                           "Order submission wiring is not enabled in this slice.")}))

(defn- vault-label
  [instrument]
  (or (normalized-text (:name instrument))
      (normalized-text (:symbol instrument))
      (ids/normalize-vault-address (:vault-address instrument))
      (ids/vault-address-from-value (:coin instrument))
      (ids/vault-address-from-value (:instrument-id instrument))
      (normalized-text (:instrument-id instrument))))

(defn- universe-audit-label
  [instrument]
  (if (ids/vault-instrument? instrument)
    (vault-label instrument)
    (:instrument-id instrument)))

(defn- draft-instrument-label
  [draft instrument-id]
  (or (some (fn [instrument]
              (when (= instrument-id (:instrument-id instrument))
                (or (when (ids/vault-instrument? instrument)
                      (vault-label instrument))
                    (normalized-text (:coin instrument))
                    (normalized-text (:symbol instrument))
                    (normalized-text (:name instrument)))))
            (:universe draft))
      (some-> instrument-id
              (str/split #":")
              last)
      instrument-id))

(defn- audit-view-primary-id
  [view]
  (or (:instrument-id view)
      (:long-instrument-id view)))

(defn- audit-view-comparator-id
  [view]
  (or (:comparator-instrument-id view)
      (:short-instrument-id view)))

(defn- audit-view-row
  [draft view]
  (assoc view
         :primary-label (draft-instrument-label draft
                                                (audit-view-primary-id view))
         :comparator-label (draft-instrument-label draft
                                                   (audit-view-comparator-id view))))

(defn inputs-audit-model
  [state]
  (let [draft (optimizer-draft state)
        views (vec (or (get-in draft [:return-model :views]) []))]
    {:draft draft
     :scenario-id (or (get-in state contracts/active-scenario-loaded-id-path)
                      (:id draft))
     :universe (vec (or (:universe draft) []))
     :universe-rows (mapv #(assoc % :audit-label (universe-audit-label %))
                          (or (:universe draft) []))
     :objective-kind (get-in draft [:objective :kind])
     :return-model-kind (get-in draft [:return-model :kind])
     :risk-model-kind (get-in draft [:risk-model :kind])
     :constraints (:constraints draft)
     :execution-assumptions (:execution-assumptions draft)
     :views views
     :view-rows (mapv (partial audit-view-row draft) views)}))
