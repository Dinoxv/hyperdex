(ns hyperopen.portfolio.optimizer.application.run-bridge
  (:require [hyperopen.portfolio.optimizer.application.run-bridge-workflow :as workflow]
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

(defn- interpret-start-command!
  [command]
  (case (:command/type command)
    :optimizer.workflow/install-worker-handler
    (worker-client/set-message-handler! handle-worker-message!)

    :optimizer.workflow/post-worker-run
    (worker-client/post-run! (:run-id command) (:request command))

    nil))

(defn request-run!
  [{:keys [request request-signature computed-at-ms store run-id]}]
  (let [store* (or store system/store)
        started-at-ms (or computed-at-ms (now-ms))
        result (workflow/start-run
                {:state @store*
                 :last-run-request @last-run-request
                 :request request
                 :request-signature request-signature
                 :run-id (or run-id (next-run-id))
                 :explicit-run-id? (some? run-id)
                 :computed-at-ms started-at-ms})]
    (when-let [run-id* (:run-id result)]
      (reset! last-run-request (:last-run-request result))
      (reset! store* (:state result))
      (doseq [command (:commands result)]
        (interpret-start-command! command))
      run-id*)))

(defn handle-worker-message!
  ([message]
   (handle-worker-message! message {:computed-at-ms (now-ms)}))
  ([message {:keys [computed-at-ms]}]
   (swap! system/store
          (fn [state]
            (:state (workflow/handle-worker-message
                     {:state state
                      :message message
                      :computed-at-ms computed-at-ms}))))
   nil))
