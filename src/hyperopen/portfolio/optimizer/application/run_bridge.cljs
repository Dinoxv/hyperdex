(ns hyperopen.portfolio.optimizer.application.run-bridge
  (:require [hyperopen.portfolio.optimizer.application.progress :as progress]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.infrastructure.worker-client :as worker-client]
            [hyperopen.system :as system]))

(declare handle-worker-message!)

(defonce last-run-request
  (atom nil))

(defn next-run-id
  []
  (str "optimizer-run-" (.now js/Date) "-" (rand-int 1000000)))

(defn- now-ms
  []
  (.now js/Date))

(defn- run-state
  [state]
  (get-in state contracts/run-state-path))

(defn- active-scenario-id
  [state]
  (get-in state contracts/active-scenario-loaded-id-path))

(defn- stale-message?
  [state id]
  (let [current-run (run-state state)
        active-id (active-scenario-id state)
        scenario-id (:scenario-id current-run)]
    (or (not= id (:run-id current-run))
        (and active-id
             scenario-id
             (not= active-id scenario-id)))))

(defn- start-run-state
  [{:keys [run-id scenario-id request-signature started-at-ms]}]
  {:status :running
   :run-id run-id
   :scenario-id scenario-id
   :request-signature request-signature
   :started-at-ms started-at-ms
   :error nil})

(defn request-run!
  [{:keys [request request-signature computed-at-ms store run-id]}]
  (when (or run-id
            (not= request-signature (:request-signature @last-run-request)))
    (worker-client/set-message-handler! handle-worker-message!)
    (let [run-id (or run-id (next-run-id))
          scenario-id (:scenario-id request)
          started-at-ms (or computed-at-ms (now-ms))
          store* (or store system/store)]
      (reset! last-run-request {:request-signature request-signature
                                :run-id run-id})
      (swap! store* assoc-in
             contracts/run-state-path
             (start-run-state {:run-id run-id
                               :scenario-id scenario-id
                               :request-signature request-signature
                               :started-at-ms started-at-ms}))
      (worker-client/post-run! run-id request)
      run-id)))

(defn- current-progress
  [state]
  (get-in state contracts/optimization-progress-path))

(defn- update-progress
  [state id f & args]
  (let [progress-state (current-progress state)]
    (if (= id (:run-id progress-state))
      (assoc-in state
                contracts/optimization-progress-path
                (apply f progress-state args))
      state)))

(defn- apply-worker-progress
  [state id payload]
  (update-progress state id progress/worker-progress payload))

(defn- success-run-state
  [current-run completed-at-ms]
  {:status :succeeded
   :run-id (:run-id current-run)
   :scenario-id (:scenario-id current-run)
   :request-signature (:request-signature current-run)
   :completed-at-ms completed-at-ms
   :error nil})

(defn- failed-run-state
  [current-run completed-at-ms error]
  {:status :failed
   :run-id (:run-id current-run)
   :scenario-id (:scenario-id current-run)
   :request-signature (:request-signature current-run)
   :completed-at-ms completed-at-ms
   :error error})

(defn- non-solved-run-state
  [current-run completed-at-ms payload]
  {:status (:status payload)
   :run-id (:run-id current-run)
   :scenario-id (:scenario-id current-run)
   :request-signature (:request-signature current-run)
   :completed-at-ms completed-at-ms
   :error nil
   :result payload})

(defn- apply-worker-result
  [state id payload computed-at-ms]
  (if (stale-message? state id)
    state
    (let [current-run (run-state state)]
      (if (= :solved (:status payload))
        (let [scenario-id (:scenario-id current-run)]
          (-> state
              (assoc-in contracts/run-state-path
                        (success-run-state current-run computed-at-ms))
              (assoc-in contracts/last-successful-run-path
                        {:request-signature (:request-signature current-run)
                         :result payload
                         :computed-at-ms computed-at-ms})
              (assoc-in contracts/draft-dirty-path false)
              (update-in contracts/active-scenario-path
                         (fn [active-scenario]
                           (cond-> (assoc (or active-scenario {})
                                          :status :computed
                                          :read-only? false)
                             scenario-id (assoc :loaded-id scenario-id))))
              (update-progress id progress/succeed-progress computed-at-ms)))
        (-> state
            (assoc-in contracts/run-state-path
                      (non-solved-run-state current-run computed-at-ms payload))
            (update-progress id progress/fail-progress computed-at-ms payload))))))

(defn- apply-worker-error
  [state id payload computed-at-ms]
  (if (stale-message? state id)
    state
    (-> state
        (assoc-in contracts/run-state-path
                  (failed-run-state (run-state state) computed-at-ms payload))
        (update-progress id progress/fail-progress computed-at-ms payload))))

(defn handle-worker-message!
  ([message]
   (handle-worker-message! message {:computed-at-ms (now-ms)}))
  ([{:keys [id type payload]} {:keys [computed-at-ms]}]
   (case type
     "optimizer-result"
     (swap! system/store apply-worker-result id payload computed-at-ms)

     :optimizer-result
     (swap! system/store apply-worker-result id payload computed-at-ms)

     "optimizer-progress"
     (swap! system/store apply-worker-progress id payload)

     :optimizer-progress
     (swap! system/store apply-worker-progress id payload)

     "optimizer-error"
     (swap! system/store apply-worker-error id payload computed-at-ms)

     :optimizer-error
     (swap! system/store apply-worker-error id payload computed-at-ms)

     nil)))
