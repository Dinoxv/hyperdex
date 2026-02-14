(ns hyperopen.telemetry.console-preload
  (:require [hyperopen.platform :as platform]
            [hyperopen.system :as app-system]
            [hyperopen.telemetry :as telemetry]
            [hyperopen.websocket.client :as ws-client]))

(def ^:private debug-api-key
  "HYPEROPEN_DEBUG")

(defn- snapshot-map
  []
  {:captured-at-ms (platform/now-ms)
   :app-state @app-system/store
   :runtime-state @app-system/runtime
   :websocket {:connection-state @ws-client/connection-state
               :stream-runtime @ws-client/stream-runtime
               :client-runtime-state @ws-client/runtime-state}
   :telemetry {:event-count (count (telemetry/events))
               :events (telemetry/events)}})

(defn- snapshot-js
  []
  (clj->js (snapshot-map)))

(defn- snapshot-json
  []
  (js/JSON.stringify (snapshot-js) nil 2))

(defn- download-snapshot!
  []
  (when-let [document (some-> js/globalThis .-document)]
    (let [payload (snapshot-json)
          blob (js/Blob. #js [payload] #js {:type "application/json"})
          object-url (js/URL.createObjectURL blob)
          link (.createElement document "a")
          timestamp (platform/now-ms)]
      (set! (.-href link) object-url)
      (set! (.-download link) (str "hyperopen-debug-snapshot-" timestamp ".json"))
      (.appendChild (.-body document) link)
      (.click link)
      (.remove link)
      (js/URL.revokeObjectURL object-url))
    true))

(defn- debug-api
  []
  #js {:snapshot snapshot-js
       :snapshotJson snapshot-json
       :downloadSnapshot download-snapshot!
       :events (fn []
                 (clj->js (telemetry/events)))
       :eventsJson telemetry/events-json
       :clearEvents telemetry/clear-events!})

(when ^boolean goog.DEBUG
  (let [global js/globalThis
        api (debug-api)]
    (aset global debug-api-key api)
    ;; Convenience aliases for direct console use.
    (aset global "hyperopenSnapshot" (aget api "snapshot"))
    (aset global "hyperopenSnapshotJson" (aget api "snapshotJson"))
    (aset global "hyperopenDownloadSnapshot" (aget api "downloadSnapshot"))))
