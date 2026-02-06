(ns hyperopen.websocket.client
  (:require [clojure.string :as str]))

(def ^:private ws-ready-state-connecting 0)
(def ^:private ws-ready-state-open 1)

(def ^:private default-config
  {:base-delay-ms 500
   :backoff-multiplier 2
   :jitter-ratio 0.2
   :max-visible-delay-ms 15000
   :max-hidden-delay-ms 60000
   :max-queue-size 1000
   :watchdog-interval-ms 10000
   :stale-visible-ms 45000
   :stale-hidden-ms 180000})

(defonce connection-config (atom default-config))

;; WebSocket connection state
(defonce connection-state (atom {:status :disconnected
                                 :attempt 0
                                 :next-retry-at-ms nil
                                 :last-close nil
                                 :last-activity-at-ms nil
                                 :queue-size 0
                                 :ws nil}))

;; Message handlers registry
(defonce message-handlers (atom {}))

(defonce runtime-state
  (atom {:ws-url nil
         :socket nil
         :socket-id 0
         :active-socket-id nil
         :intentional-close? false
         :retry-timer nil
         :watchdog-timer nil
         :lifecycle-hooks-installed? false
         :lifecycle-handlers nil}))

(defonce outbound-queue (atom []))
(defonce desired-subscriptions (atom {}))

(declare force-reconnect!)
(declare attempt-connect!)
(declare schedule-reconnect!)
(declare handle-message!)

(defn now-ms []
  (.now js/Date))

(defn random-value []
  (js/Math.random))

(defn schedule-timeout! [f ms]
  (js/setTimeout f ms))

(defn clear-timeout! [timer-id]
  (js/clearTimeout timer-id))

(defn schedule-interval! [f ms]
  (js/setInterval f ms))

(defn clear-interval! [timer-id]
  (js/clearInterval timer-id))

(defn create-websocket [ws-url]
  (js/WebSocket. ws-url))

(defn window-object []
  (when (exists? js/window)
    js/window))

(defn document-object []
  (when (exists? js/document)
    js/document))

(defn navigator-object []
  (when (exists? js/navigator)
    js/navigator))

(defn add-event-listener! [target event-name handler]
  (when target
    (try
      (.addEventListener target event-name handler)
      (catch :default _ nil))))

(defn- online? []
  (if-let [nav (navigator-object)]
    (if (nil? (.-onLine nav))
      true
      (boolean (.-onLine nav)))
    true))

(defn- hidden-tab? []
  (if-let [doc (document-object)]
    (= "hidden" (.-visibilityState doc))
    false))

(defn- socket-ready-state [socket]
  (when socket
    (.-readyState socket)))

(defn- socket-open? [socket]
  (= ws-ready-state-open (socket-ready-state socket)))

(defn- socket-connecting? [socket]
  (= ws-ready-state-connecting (socket-ready-state socket)))

(defn- socket-active? [socket]
  (or (socket-open? socket)
      (socket-connecting? socket)))

(defn- active-socket-id? [socket-id]
  (= socket-id (:active-socket-id @runtime-state)))

(defn- update-queue-size! []
  (swap! connection-state assoc :queue-size (count @outbound-queue)))

(defn- normalize-method [value]
  (some-> value str str/lower-case))

(defn- subscription-key [subscription]
  (let [key-fields [(:type subscription)
                    (:coin subscription)
                    (:user subscription)
                    (:dex subscription)
                    (:interval subscription)]]
    (if (some some? key-fields)
      key-fields
      [:raw (pr-str subscription)])))

(defn- track-subscription-intent! [data]
  (let [method (normalize-method (:method data))
        subscription (:subscription data)]
    (when (map? subscription)
      (case method
        "subscribe" (swap! desired-subscriptions assoc (subscription-key subscription) subscription)
        "unsubscribe" (swap! desired-subscriptions dissoc (subscription-key subscription))
        nil))))

(defn- send-json! [socket data]
  (when (socket-open? socket)
    (try
      (.send socket (js/JSON.stringify (clj->js data)))
      (swap! connection-state assoc :last-activity-at-ms (now-ms))
      true
      (catch :default e
        (println "Error sending WebSocket message:" e)
        false))))

(defn- enqueue-message! [data]
  (let [max-queue-size (:max-queue-size @connection-config)]
    (swap! outbound-queue
           (fn [queue]
             (let [next-queue (conj queue data)]
               (if (> (count next-queue) max-queue-size)
                 (do
                   (println "WebSocket queue overflow, dropping oldest queued message")
                   (vec (rest next-queue)))
                 next-queue)))))
  (update-queue-size!))

(defn calculate-retry-delay-ms
  ([attempt hidden?]
   (calculate-retry-delay-ms attempt hidden? @connection-config (random-value)))
  ([attempt hidden? config sample]
   (let [{:keys [base-delay-ms
                 backoff-multiplier
                 jitter-ratio
                 max-visible-delay-ms
                 max-hidden-delay-ms]} config
         attempt* (max 1 (or attempt 1))
         exponential-delay (* base-delay-ms (js/Math.pow backoff-multiplier (dec attempt*)))
         capped-delay (min exponential-delay (if hidden? max-hidden-delay-ms max-visible-delay-ms))
         centered-sample (- (* 2 (or sample 0.5)) 1)
         jitter-factor (+ 1 (* centered-sample jitter-ratio))
         jittered-delay (* capped-delay jitter-factor)]
     (-> jittered-delay
         (max 0)
         js/Math.round))))

(defn- clear-retry-timer! []
  (when-let [timer-id (:retry-timer @runtime-state)]
    (clear-timeout! timer-id)
    (swap! runtime-state assoc :retry-timer nil))
  (swap! connection-state assoc :next-retry-at-ms nil))

(defn- ensure-watchdog! []
  (when-not (:watchdog-timer @runtime-state)
    (let [timer-id (schedule-interval!
                     (fn []
                       (let [{:keys [status last-activity-at-ms]} @connection-state
                             socket (:socket @runtime-state)
                             threshold-ms (if (hidden-tab?)
                                            (:stale-hidden-ms @connection-config)
                                            (:stale-visible-ms @connection-config))]
                         (when (and (= status :connected)
                                    (socket-open? socket)
                                    (number? last-activity-at-ms)
                                    (> (- (now-ms) last-activity-at-ms) threshold-ms))
                           (println "WebSocket watchdog detected stale connection, forcing reconnect")
                           (swap! runtime-state assoc :intentional-close? false)
                           (try
                             (.close socket 4002 "Stale websocket connection")
                             (catch :default e
                               (println "Failed to close stale WebSocket:" e))))))
                     (:watchdog-interval-ms @connection-config))]
      (swap! runtime-state assoc :watchdog-timer timer-id))))

(defn- detach-socket-handlers! [socket]
  (when socket
    (set! (.-onopen socket) nil)
    (set! (.-onmessage socket) nil)
    (set! (.-onclose socket) nil)
    (set! (.-onerror socket) nil)))

(defn- flush-queued-messages! [socket]
  (when (socket-open? socket)
    (let [queued-messages @outbound-queue]
      (reset! outbound-queue [])
      (update-queue-size!)
      (loop [pending (seq queued-messages)]
        (when pending
          (if (send-json! socket (first pending))
            (recur (next pending))
            (do
              ;; Put unsent messages back at the front to preserve FIFO order.
              (swap! outbound-queue #(vec (concat pending %)))
              (update-queue-size!))))))))

(defn- replay-desired-subscriptions! [socket]
  (when (socket-open? socket)
    (doseq [subscription (->> @desired-subscriptions vals (sort-by pr-str))]
      (send-json! socket {:method "subscribe"
                          :subscription subscription}))))

(defn- reconnect-if-needed! []
  (let [{:keys [status]} @connection-state]
    (when (and (contains? #{:disconnected :reconnecting} status)
               (not (:intentional-close? @runtime-state))
               (:ws-url @runtime-state))
      (force-reconnect!))))

(defn- install-lifecycle-hooks! []
  (when-not (:lifecycle-hooks-installed? @runtime-state)
    (let [focus-handler (fn [_]
                          (reconnect-if-needed!))
          visibility-handler (fn [_]
                               (when-not (hidden-tab?)
                                 (reconnect-if-needed!)))
          online-handler (fn [_]
                           (println "Browser returned online, reconnecting WebSocket")
                           (reconnect-if-needed!))
          offline-handler (fn [_]
                            (println "Browser went offline, pausing WebSocket reconnect timer")
                            (clear-retry-timer!)
                            (swap! connection-state assoc :status :disconnected)
                            (when-let [socket (:socket @runtime-state)]
                              (swap! runtime-state assoc :intentional-close? false)
                              (try
                                (.close socket 4001 "Offline")
                                (catch :default e
                                  (println "Error closing WebSocket while offline:" e)))))]
      (add-event-listener! (window-object) "focus" focus-handler)
      (add-event-listener! (window-object) "online" online-handler)
      (add-event-listener! (window-object) "offline" offline-handler)
      (add-event-listener! (document-object) "visibilitychange" visibility-handler)
      (swap! runtime-state assoc
             :lifecycle-hooks-installed? true
             :lifecycle-handlers {:focus focus-handler
                                  :visibility visibility-handler
                                  :online online-handler
                                  :offline offline-handler}))))

(defn- on-socket-open! [socket socket-id]
  (when (active-socket-id? socket-id)
    (clear-retry-timer!)
    (swap! connection-state assoc
           :status :connected
           :attempt 0
           :ws socket
           :last-activity-at-ms (now-ms))
    (println "WebSocket connected")
    ;; Rebuild deterministic subscription state first, then replay queued intent.
    (replay-desired-subscriptions! socket)
    (flush-queued-messages! socket)))

(defn- on-socket-close! [event socket-id]
  (when (active-socket-id? socket-id)
    (let [close-info {:code (or (.-code event) 0)
                      :reason (or (.-reason event) "")
                      :was-clean? (boolean (.-wasClean event))
                      :at-ms (now-ms)}
          intentional? (:intentional-close? @runtime-state)]
      (swap! runtime-state assoc :socket nil :active-socket-id nil)
      (swap! connection-state assoc
             :ws nil
             :last-close close-info)
      (println "WebSocket disconnected. Code:" (:code close-info) "Reason:" (:reason close-info))
      (if intentional?
        (do
          (clear-retry-timer!)
          (swap! connection-state assoc :status :disconnected))
        (do
          (swap! connection-state update :attempt (fnil inc 0))
          (swap! connection-state assoc :status :reconnecting)
          (schedule-reconnect!))))))

(defn- on-socket-error! [event socket-id]
  (when (active-socket-id? socket-id)
    (println "WebSocket error:" event)))

(defn- create-and-bind-socket! [ws-url]
  (try
    (let [socket-id (inc (:socket-id @runtime-state))
          ws (create-websocket ws-url)
          reconnecting? (pos? (:attempt @connection-state))]
      (swap! runtime-state assoc
             :socket-id socket-id
             :active-socket-id socket-id
             :socket ws
             :intentional-close? false)
      (swap! connection-state assoc
             :status (if reconnecting? :reconnecting :connecting)
             :ws ws
             :next-retry-at-ms nil)
      (set! (.-onopen ws) (fn [_] (on-socket-open! ws socket-id)))
      (set! (.-onmessage ws) handle-message!)
      (set! (.-onclose ws) (fn [event] (on-socket-close! event socket-id)))
      (set! (.-onerror ws) (fn [event] (on-socket-error! event socket-id)))
      (println "Connecting to WebSocket:" ws-url))
    (catch :default e
      (println "Failed to create WebSocket connection:" e)
      (swap! connection-state update :attempt (fnil inc 0))
      (swap! connection-state assoc :status :reconnecting)
      (schedule-reconnect!))))

(defn schedule-reconnect! []
  (clear-retry-timer!)
  (cond
    (:intentional-close? @runtime-state)
    (swap! connection-state assoc :status :disconnected :next-retry-at-ms nil)

    (not (:ws-url @runtime-state))
    (swap! connection-state assoc :status :disconnected :next-retry-at-ms nil)

    (not (online?))
    (do
      (println "Skipping WebSocket retry while offline; waiting for online event")
      (swap! connection-state assoc :status :disconnected :next-retry-at-ms nil))

    :else
    (let [attempt (max 1 (:attempt @connection-state))
          delay-ms (calculate-retry-delay-ms attempt (hidden-tab?))
          retry-at (+ (now-ms) delay-ms)
          timer-id (schedule-timeout!
                     (fn []
                       (swap! runtime-state assoc :retry-timer nil)
                       (swap! connection-state assoc :next-retry-at-ms nil)
                       (attempt-connect!))
                     delay-ms)]
      (swap! runtime-state assoc :retry-timer timer-id)
      (swap! connection-state assoc
             :status :reconnecting
             :next-retry-at-ms retry-at)
      (println "Scheduling WebSocket reconnect in" delay-ms "ms"))))

(defn attempt-connect! []
  (when-let [ws-url (:ws-url @runtime-state)]
    (if (not (online?))
      (do
        (swap! connection-state assoc :status :disconnected :next-retry-at-ms nil)
        (println "WebSocket connect skipped while offline"))
      (let [existing-socket (:socket @runtime-state)]
        (when-not (socket-active? existing-socket)
          (create-and-bind-socket! ws-url))))))

;; Register a message handler
(defn register-handler! [message-type handler-fn]
  (swap! message-handlers assoc message-type handler-fn))

;; Handle incoming WebSocket messages
(defn handle-message! [event]
  (swap! connection-state assoc :last-activity-at-ms (now-ms))
  (try
    (let [data (js/JSON.parse (.-data event))
          js-data (js->clj data :keywordize-keys true)]
      ;; Route to registered handlers based on channel
      (when-let [channel (:channel js-data)]
        (when-let [handler (get @message-handlers channel)]
          (handler js-data))))
    (catch :default e
      (println "Error parsing WebSocket message:" e))))

;; Initialize WebSocket connection
(defn init-connection! [ws-url]
  (swap! runtime-state assoc :ws-url ws-url :intentional-close? false)
  (install-lifecycle-hooks!)
  (ensure-watchdog!)
  (if (socket-active? (:socket @runtime-state))
    (println "WebSocket connection already active, skipping duplicate init")
    (attempt-connect!)))

(defn disconnect! []
  (swap! runtime-state assoc :intentional-close? true)
  (clear-retry-timer!)
  (when-let [socket (:socket @runtime-state)]
    (try
      (.close socket 1000 "Intentional disconnect")
      (catch :default e
        (println "Error during WebSocket disconnect:" e))))
  (swap! runtime-state assoc :socket nil :active-socket-id nil)
  (swap! connection-state assoc :status :disconnected :ws nil :next-retry-at-ms nil))

(defn force-reconnect! []
  (swap! runtime-state assoc :intentional-close? false)
  (clear-retry-timer!)
  (when-let [socket (:socket @runtime-state)]
    ;; Disable handlers so replacing a live socket does not schedule duplicate retries.
    (detach-socket-handlers! socket)
    (try
      (.close socket 4000 "Force reconnect")
      (catch :default e
        (println "Error closing WebSocket during forced reconnect:" e)))
    (swap! runtime-state assoc :socket nil :active-socket-id nil))
  (attempt-connect!))

;; Send message function
(defn send-message! [data]
  (track-subscription-intent! data)
  (let [socket (:socket @runtime-state)]
    (if (and (= :connected (:status @connection-state))
             socket
             (send-json! socket data))
      true
      (do
        (enqueue-message! data)
        (when (and (not (:intentional-close? @runtime-state))
                   (:ws-url @runtime-state)
                   (nil? (:retry-timer @runtime-state))
                   (not (socket-connecting? (:socket @runtime-state))))
          (schedule-reconnect!))
        false))))

;; Connection status helpers
(defn connected? []
  (= (:status @connection-state) :connected))

(defn get-connection-status []
  (:status @connection-state))

(defn reset-manager-state! []
  ;; Primarily intended for tests.
  (disconnect!)
  (when-let [watchdog-id (:watchdog-timer @runtime-state)]
    (clear-interval! watchdog-id))
  (reset! outbound-queue [])
  (reset! desired-subscriptions {})
  (reset! connection-config default-config)
  (reset! connection-state {:status :disconnected
                            :attempt 0
                            :next-retry-at-ms nil
                            :last-close nil
                            :last-activity-at-ms nil
                            :queue-size 0
                            :ws nil})
  (swap! runtime-state assoc
         :socket nil
         :ws-url nil
         :retry-timer nil
         :watchdog-timer nil
         :socket-id 0
         :active-socket-id nil
         :intentional-close? false
         :lifecycle-hooks-installed? false
         :lifecycle-handlers nil))
