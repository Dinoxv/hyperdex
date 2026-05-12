(ns hyperopen.portfolio.optimizer.application.scenario-workflow
  (:require [hyperopen.portfolio.optimizer.application.scenario-records :as scenario-records]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.defaults :as optimizer-defaults]))

(defn default-scenario-index
  []
  {:ordered-ids []
   :by-id {}})

(defn error-message
  [err]
  (or (when (map? err)
        (:message err))
      (some-> err .-message)
      (str err)))

(defn begin-scenario-save-state
  [scenario-id started-at-ms]
  {:status :saving
   :scenario-id scenario-id
   :started-at-ms started-at-ms
   :completed-at-ms nil
   :error nil})

(defn failed-scenario-save-state
  [scenario-id started-at-ms completed-at-ms err]
  {:status :failed
   :scenario-id scenario-id
   :started-at-ms started-at-ms
   :completed-at-ms completed-at-ms
   :error {:message (error-message err)}})

(defn solved-run?
  [last-successful-run]
  (= :solved (get-in last-successful-run [:result :status])))

(defn begin-save
  [{:keys [state address scenario-id started-at-ms]}]
  (if (and address
           scenario-id
           (solved-run? (get-in state contracts/last-successful-run-path)))
    {:state (assoc-in state
                      contracts/scenario-save-state-path
                      (begin-scenario-save-state scenario-id started-at-ms))
     :commands [{:command/type :optimizer.workflow/load-scenario-index
                 :address address
                 :scenario-id scenario-id
                 :started-at-ms started-at-ms}]}
    {:state (assoc-in state
                      contracts/scenario-save-state-path
                      (failed-scenario-save-state
                       scenario-id
                       started-at-ms
                       started-at-ms
                       {:message "Cannot save scenario without an address and solved run."}))
     :commands []}))

(defn continue-save-after-index
  [{:keys [state address scenario-id started-at-ms loaded-index]}]
  (let [draft (or (get-in state contracts/draft-path)
                  (optimizer-defaults/default-draft))
        last-successful-run (get-in state contracts/last-successful-run-path)
        existing-index (or (get-in state contracts/scenario-index-path)
                           (default-scenario-index))
        scenario-record (scenario-records/build-saved-scenario-record
                         {:address address
                          :scenario-id scenario-id
                          :draft draft
                          :last-successful-run last-successful-run
                          :saved-at-ms started-at-ms})
        scenario-index (scenario-records/upsert-scenario-index
                        (or loaded-index existing-index (default-scenario-index))
                        (scenario-records/scenario-summary scenario-record))]
    {:state state
     :scenario-record scenario-record
     :scenario-index scenario-index
     :commands [{:command/type :optimizer.workflow/save-scenario
                 :scenario-id scenario-id
                 :scenario-record scenario-record
                 :started-at-ms started-at-ms}
                {:command/type :optimizer.workflow/save-scenario-index
                 :address address
                 :scenario-index scenario-index
                 :scenario-id scenario-id
                 :started-at-ms started-at-ms}]}))
