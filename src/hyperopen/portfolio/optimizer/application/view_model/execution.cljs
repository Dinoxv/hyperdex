(ns hyperopen.portfolio.optimizer.application.view-model.execution
  (:require [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.ids :as ids]))

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

(defn- latest-record
  [records]
  (last (vec records)))

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
