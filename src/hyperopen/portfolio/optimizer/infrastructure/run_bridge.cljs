(ns hyperopen.portfolio.optimizer.infrastructure.run-bridge
  (:require [hyperopen.portfolio.optimizer.application.run-bridge-workflow :as workflow]
            [hyperopen.portfolio.optimizer.infrastructure.worker-client :as worker-client]
            [hyperopen.system :as system]))

(declare handle-worker-message!)

(defn next-run-id
  []
  (str "optimizer-run-" (.now js/Date) "-" (rand-int 1000000)))

(defn- now-ms
  []
  (.now js/Date))

(defn make-controller
  ([] (make-controller {}))
  ([{:keys [store worker-ref worker-url]}]
   {:store (or store system/store)
    :last-run-request (atom nil)
    :worker-ref (or worker-ref
                    (delay (worker-client/make-worker!
                            (or worker-url worker-client/default-worker-url))))
    :worker-handler-installed? (atom false)}))

(defn- controller?
  [value]
  (and (map? value)
       (contains? value :store)
       (contains? value :last-run-request)
       (contains? value :worker-ref)
       (contains? value :worker-handler-installed?)))

(defn controller-for-store!
  [controllers store]
  (or (get @controllers store)
      (let [controller (make-controller {:store store})]
        (get (swap! controllers
                    (fn [controllers*]
                      (if (contains? controllers* store)
                        controllers*
                        (assoc controllers* store controller))))
             store))))

(defn make-controller-resolver
  []
  (let [controllers (atom {})]
    (fn [store]
      (controller-for-store! controllers store))))

(defn- install-worker-handler!
  [{:keys [worker-ref worker-handler-installed?] :as controller}]
  (when-not @worker-handler-installed?
    (when-let [worker (worker-client/current-worker worker-ref)]
      (when (worker-client/add-message-listener!
             worker
             (fn [message]
               (handle-worker-message! controller message)))
        (reset! worker-handler-installed? true)))))

(defn- interpret-start-command!
  [controller command]
  (case (:command/type command)
    :optimizer.workflow/install-worker-handler
    (install-worker-handler! controller)

    :optimizer.workflow/post-worker-run
    (worker-client/post-run! (:worker-ref controller)
                             (:run-id command)
                             (:request command))

    nil))

(defn request-run!
  [{:keys [controller request request-signature computed-at-ms store run-id]}]
  (let [controller* (or controller (make-controller {:store (or store system/store)}))
        store* (or (:store controller*) store system/store)
        last-run-request (:last-run-request controller*)
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
        (interpret-start-command! controller* command))
      run-id*)))

(defn handle-worker-message!
  ([message]
   (handle-worker-message! (make-controller {:store system/store})
                           message
                           {:computed-at-ms (now-ms)}))
  ([controller-or-message message-or-opts]
   (if (controller? controller-or-message)
     (handle-worker-message! controller-or-message
                             message-or-opts
                             {:computed-at-ms (now-ms)})
     (handle-worker-message! (make-controller {:store system/store})
                             controller-or-message
                             message-or-opts)))
  ([controller message {:keys [computed-at-ms]}]
   (swap! (:store controller)
          (fn [state]
            (:state (workflow/handle-worker-message
                     {:state state
                      :message message
                      :computed-at-ms computed-at-ms}))))
   nil))
