(ns hyperopen.startup.watchers
  (:require [hyperopen.platform :as platform]))

(def ^:private active-market-display-watch-key
  ::active-market-display-cache)

(def ^:private asset-selector-markets-watch-key
  ::asset-selector-markets-cache)

(def ^:private websocket-status-watch-key
  ::ws-status)

(def ^:private websocket-health-watch-key
  ::ws-health)

(defn install-store-cache-watchers!
  [store {:keys [persist-active-market-display!
                 persist-asset-selector-markets-cache!]}]
  ;; Keep startup display metadata fresh whenever active-market becomes available.
  (add-watch store active-market-display-watch-key
    (fn [_ _ old-state new-state]
      (let [old-market (:active-market old-state)
            new-market (:active-market new-state)]
        (when (not= old-market new-market)
          (persist-active-market-display! new-market)))))

  ;; Persist non-empty selector market lists so symbols are available on next reload.
  (add-watch store asset-selector-markets-watch-key
    (fn [_ _ old-state new-state]
      (let [old-markets (get-in old-state [:asset-selector :markets] [])
            new-markets (get-in new-state [:asset-selector :markets] [])]
        (when (not= old-markets new-markets)
          (persist-asset-selector-markets-cache! new-markets new-state))))))

(defn- status->diagnostics-event
  [status]
  (case status
    :connected :connected
    :reconnecting :reconnecting
    :disconnected :offline
    nil))

(defn- projected-health-fingerprint
  [runtime-projection]
  (:health-fingerprint runtime-projection))

(defn- legacy-websocket-projection
  [connection-projection]
  (select-keys connection-projection [:status
                                      :attempt
                                      :next-retry-at-ms
                                      :last-close
                                      :queue-size]))

(defn install-websocket-watchers!
  [{:keys [store
           connection-state
           stream-runtime
           append-diagnostics-event!
           sync-websocket-health!
           on-websocket-connected!
           on-websocket-disconnected!]}]
  ;; Watch for WebSocket connection status changes.
  (add-watch connection-state websocket-status-watch-key
    (fn [_ _ old-state new-state]
      (let [old-status (:status old-state)
            new-status (:status new-state)
            status-transition? (not= old-status new-status)
            old-legacy-projection (legacy-websocket-projection old-state)
            new-legacy-projection (legacy-websocket-projection new-state)
            legacy-projection-transition? (not= old-legacy-projection new-legacy-projection)
            transition-event (status->diagnostics-event new-status)
            transition-at-ms (or (:now-ms new-state) (platform/now-ms))]
        (when legacy-projection-transition?
          ;; Defer store update to next tick to avoid nested renders.
          (platform/queue-microtask!
           #(do
              (swap! store
                     (fn [state]
                       (cond-> (update state :websocket merge new-legacy-projection)
                         (and status-transition?
                              (= :reconnecting new-status))
                         (update-in [:websocket-ui :reconnect-count] (fnil inc 0)))))
              (when (and status-transition? transition-event)
                (append-diagnostics-event! store transition-event transition-at-ms)))))
        (when status-transition?
          (sync-websocket-health! store :force? true))
        ;; Notify address watcher only on status transitions.
        (when status-transition?
          (if (= new-status :connected)
            (on-websocket-connected!)
            (on-websocket-disconnected!))))))

  (add-watch stream-runtime websocket-health-watch-key
    (fn [_ _ old-runtime new-runtime]
      (let [old-fingerprint (projected-health-fingerprint old-runtime)
            new-fingerprint (projected-health-fingerprint new-runtime)]
        (when (not= old-fingerprint new-fingerprint)
          (sync-websocket-health! store
                                  :projected-fingerprint new-fingerprint))))))
