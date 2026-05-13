(ns hyperopen.portfolio.optimizer.application.view-model.universe
  (:require [hyperopen.portfolio.optimizer.application.universe-candidates :as universe-candidates]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]))

(def ^:private normalized-text coercion/non-blank-text)

(def ^:private missing-history-warning-codes
  #{:missing-history-coin
    :missing-candle-history
    :missing-vault-address
    :missing-vault-history})

(def ^:private insufficient-history-warning-codes
  #{:insufficient-candle-history
    :insufficient-vault-history})

(def ^:private history-status-labels
  {:queued "queued"
   :loading "loading"
   :shared-gap "shared gap"
   :sufficient "sufficient"
   :insufficient "insufficient"
   :missing "missing"
   :pending "pending"})

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

(defn selected-history-status
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
      :queued

      (= :loading prefetch-status)
      :loading

      (and (= :loading (:status history-load-state))
           (contains? loading-ids instrument-id))
      :loading

      (= :loaded-but-misaligned readiness-status)
      :shared-gap

      (= :aligned readiness-status)
      :sufficient

      (contains? eligible-ids instrument-id)
      :sufficient

      (and (= :insufficient readiness-status)
           (or load-validated? cached-history?))
      :insufficient

      (and (contains? insufficient-history-warning-codes warning-code)
           (or load-validated? cached-history?))
      :insufficient

      (and (= :missing readiness-status)
           load-validated?)
      :missing

      (and (contains? missing-history-warning-codes warning-code)
           load-validated?)
      :missing

      :else
      :pending)))

(defn selected-history-label
  [state readiness history-load-state history-status-by-id instrument]
  (get history-status-labels
       (selected-history-status state
                                readiness
                                history-load-state
                                history-status-by-id
                                instrument)
       "pending"))

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
