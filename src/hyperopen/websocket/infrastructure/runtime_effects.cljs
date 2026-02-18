(ns hyperopen.websocket.infrastructure.runtime-effects
  (:require [hyperopen.telemetry :as telemetry]
            [hyperopen.websocket.infrastructure.transport :as infra]))

(defn- now-ms [clock]
  (infra/now-ms* clock))

(defn- clear-timer! [scheduler io-state timer-key clear-fn]
  (when-let [timer-id (get-in @io-state [:timers timer-key])]
    (clear-fn scheduler timer-id)
    (swap! io-state update :timers dissoc timer-key)))

(defn- socket-for-id [io-state socket-id]
  (get-in @io-state [:sockets socket-id]))

(def ^:private projection-fingerprint-path
  [:projection-fingerprints])

(defn- applied-projection-fingerprint
  [io-state projection-key]
  (get-in @io-state (conj projection-fingerprint-path projection-key)))

(defn- store-projection-fingerprint!
  [io-state projection-key projection-fingerprint]
  (swap! io-state assoc-in (conj projection-fingerprint-path projection-key) projection-fingerprint))

(defn- maybe-update-projection!
  [io-state projection-key projection-fingerprint update-projection-fn]
  (when (not= (applied-projection-fingerprint io-state projection-key)
              projection-fingerprint)
    (update-projection-fn)
    (store-projection-fingerprint! io-state projection-key projection-fingerprint)))

(defn- update-connection-projection!
  [connection-state-atom io-state connection active-socket-id projection-fingerprint]
  (let [fingerprint (or projection-fingerprint
                        {:connection connection
                         :active-socket-id active-socket-id})]
    (maybe-update-projection! io-state :connection fingerprint
                              (fn []
                                (let [socket (socket-for-id io-state active-socket-id)]
                                  (reset! connection-state-atom
                                          (assoc connection :ws socket)))))))

(defn- update-stream-projection!
  [stream-runtime-atom io-state metrics tier-depth market-coalesce now-ms health-fingerprint streams transport projection-fingerprint]
  (let [projection {:metrics metrics
                    :tier-depth tier-depth
                    :market-coalesce market-coalesce
                    :now-ms now-ms
                    :health-fingerprint health-fingerprint
                    :streams streams
                    :transport transport}
        fingerprint (or projection-fingerprint projection)]
    (maybe-update-projection! io-state :stream fingerprint
                              (fn []
                                (reset! stream-runtime-atom projection)))))

(defn interpret-effect!
  [{:keys [transport
           scheduler
           clock
           io-state
           parse-raw-envelope
           hydrate-envelope
           dispatch!
           register-router-handler!
           dispatch-envelope!
           connection-state-atom
           stream-runtime-atom]}
   effect]
  (case (:fx/type effect)
    :fx/socket-connect
    (let [{:keys [ws-url socket-id]} effect
          socket (infra/connect-websocket!
                   transport
                   ws-url
                    {:on-open (fn [_]
                                (let [at-ms (now-ms clock)]
                                  (dispatch! {:msg/type :evt/socket-open
                                              :socket-id socket-id
                                              :at-ms at-ms
                                              :ts at-ms})))
                     :on-message (fn [event]
                                   (let [recv-at-ms (now-ms clock)]
                                     (dispatch! {:msg/type :evt/socket-message
                                                 :socket-id socket-id
                                                 :raw (.-data event)
                                                 :recv-at-ms recv-at-ms
                                                 :ts recv-at-ms})))
                     :on-close (fn [event]
                                 (let [at-ms (now-ms clock)]
                                   (dispatch! {:msg/type :evt/socket-close
                                               :socket-id socket-id
                                               :code (or (.-code event) 0)
                                               :reason (or (.-reason event) "")
                                               :was-clean? (boolean (.-wasClean event))
                                               :at-ms at-ms
                                               :ts at-ms})))
                     :on-error (fn [event]
                                 (let [at-ms (now-ms clock)]
                                   (dispatch! {:msg/type :evt/socket-error
                                               :socket-id socket-id
                                               :error event
                                               :ts at-ms})))})]
      (swap! io-state assoc-in [:sockets socket-id] socket)
      (swap! io-state assoc :active-socket-id socket-id))

    :fx/socket-send
    (let [{:keys [socket-id data]} effect
          socket (socket-for-id io-state socket-id)]
      (when socket
        (try
          (when (infra/socket-open? transport socket)
            (infra/send-json! transport socket data))
          (catch :default e
            (dispatch! {:msg/type :evt/socket-error
                        :socket-id socket-id
                        :error e
                        :ts (now-ms clock)})))))

    :fx/socket-close
    (let [{:keys [socket-id code reason]} effect
          socket (socket-for-id io-state socket-id)]
      (when socket
        (try
          (infra/close-socket! transport socket (or code 1000) (or reason ""))
          (catch :default _ nil))))

    :fx/socket-detach-handlers
    (let [socket (socket-for-id io-state (:socket-id effect))]
      (when socket
        (infra/detach-handlers! transport socket)))

    :fx/timer-set-timeout
    (let [{:keys [timer-key ms msg]} effect]
      (clear-timer! scheduler io-state timer-key infra/clear-timeout*)
      (let [timer-id (infra/schedule-timeout*
                       scheduler
                       (fn []
                         (swap! io-state update :timers dissoc timer-key)
                         (let [fired-at-ms (now-ms clock)]
                           (dispatch! (assoc msg :ts fired-at-ms :now-ms fired-at-ms))))
                       ms)]
        (swap! io-state assoc-in [:timers timer-key] timer-id)))

    :fx/timer-clear-timeout
    (clear-timer! scheduler io-state (:timer-key effect) infra/clear-timeout*)

    :fx/timer-set-interval
    (let [{:keys [timer-key ms msg]} effect]
      (clear-timer! scheduler io-state timer-key infra/clear-interval*)
      (let [timer-id (infra/schedule-interval*
                       scheduler
                       (fn []
                         (let [fired-at-ms (now-ms clock)]
                           (dispatch! (assoc msg :ts fired-at-ms :now-ms fired-at-ms))))
                       ms)]
        (swap! io-state assoc-in [:timers timer-key] timer-id)))

    :fx/timer-clear-interval
    (clear-timer! scheduler io-state (:timer-key effect) infra/clear-interval*)

    :fx/lifecycle-install-listeners
    (when-not (:lifecycle-installed? @io-state)
      (let [focus-handler (fn [_]
                            (dispatch! {:msg/type :evt/lifecycle-focus
                                        :ts (now-ms clock)}))
            online-handler (fn [_]
                             (dispatch! {:msg/type :evt/lifecycle-online
                                         :ts (now-ms clock)}))
            offline-handler (fn [_]
                              (dispatch! {:msg/type :evt/lifecycle-offline
                                          :ts (now-ms clock)}))
            visibility-handler (fn [_]
                                 (when-not (infra/hidden-tab?* scheduler)
                                   (dispatch! {:msg/type :evt/lifecycle-visible
                                               :ts (now-ms clock)})))]
        (infra/add-event-listener* scheduler (infra/window-object* scheduler) "focus" focus-handler)
        (infra/add-event-listener* scheduler (infra/window-object* scheduler) "online" online-handler)
        (infra/add-event-listener* scheduler (infra/window-object* scheduler) "offline" offline-handler)
        (infra/add-event-listener* scheduler (infra/document-object* scheduler) "visibilitychange" visibility-handler)
        (swap! io-state assoc
               :lifecycle-installed? true
               :lifecycle-handlers {:focus focus-handler
                                    :online online-handler
                                    :offline offline-handler
                                    :visibility visibility-handler})))

    :fx/router-register-handler
    (register-router-handler! (:topic effect) (:handler-fn effect))

    :fx/router-dispatch-envelope
    (let [envelope (:envelope effect)
          envelope* (if hydrate-envelope
                      (hydrate-envelope envelope)
                      envelope)]
      (dispatch-envelope! envelope*))

    :fx/parse-raw-message
    (let [{:keys [raw socket-id recv-at-ms]} effect
          decode-at-ms (or recv-at-ms (now-ms clock))
          {:keys [ok error]} (parse-raw-envelope {:raw raw
                                                  :socket-id socket-id})]
      (if ok
        (dispatch! {:msg/type :evt/decoded-envelope
                    :envelope ok
                    :recv-at-ms decode-at-ms
                    :ts decode-at-ms})
        (dispatch! {:msg/type :evt/parse-error
                    :error error
                    :raw raw
                    :recv-at-ms decode-at-ms
                    :ts decode-at-ms})))

    :fx/project-connection-state
    (update-connection-projection! connection-state-atom
                                   io-state
                                   (:connection effect)
                                   (:active-socket-id effect)
                                   (:projection-fingerprint effect))

    :fx/project-stream-metrics
    (update-stream-projection! stream-runtime-atom
                               io-state
                               (:metrics effect)
                               (:tier-depth effect)
                               (:market-coalesce effect)
                               (:now-ms effect)
                               (:health-fingerprint effect)
                               (:streams effect)
                               (:transport effect)
                               (:projection-fingerprint effect))

    :fx/log
    (let [{:keys [level message error]} effect]
      (telemetry/emit! :websocket/runtime-log
                       {:level (or level :info)
                        :message message
                        :error (when error (str error))}))

    :fx/dead-letter
    (telemetry/emit! :websocket/dead-letter effect)

    nil))
