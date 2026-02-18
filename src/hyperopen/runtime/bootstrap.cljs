(ns hyperopen.runtime.bootstrap
  (:require [hyperopen.platform :as platform]))

(defn register-runtime!
  [{:keys [register-effects!
           effect-handlers
           register-actions!
           action-handlers
           register-system-state!
           register-placeholders!]}]
  (register-effects! effect-handlers)
  (register-actions! action-handlers)
  (register-system-state!)
  (register-placeholders!))

(defn install-render-loop!
  [{:keys [store
           render-watch-key
           set-dispatch!
           dispatch!
           render!
           document?
           request-animation-frame!]}]
  (set-dispatch! #(dispatch! store %1 %2))
  (when (if (some? document?)
          document?
          (exists? js/document))
    (let [request-frame! (or request-animation-frame!
                             platform/request-animation-frame!)
          pending-state (atom nil)
          frame-pending? (atom false)]
      (remove-watch store render-watch-key)
      (add-watch store
                 render-watch-key
                 (fn [_ _ _ new-state]
                   (reset! pending-state new-state)
                   (when-not @frame-pending?
                     (reset! frame-pending? true)
                     (request-frame!
                      (fn [_]
                        (let [state-to-render @pending-state]
                          (reset! pending-state nil)
                          (reset! frame-pending? false)
                          (when (some? state-to-render)
                            (render! state-to-render)))))))))))

(defn install-runtime-watchers!
  [{:keys [store
           install-store-cache-watchers!
           store-cache-watchers-deps
           install-websocket-watchers!
           websocket-watchers-deps]}]
  (install-store-cache-watchers!
   store
   store-cache-watchers-deps)
  (install-websocket-watchers!
   websocket-watchers-deps))

(defn install-state-validation!
  [{:keys [store
           install-store-state-validation!]}]
  (when (fn? install-store-state-validation!)
    (install-store-state-validation! store)))

(defn bootstrap-runtime!
  [{:keys [register-runtime-deps
           render-loop-deps
           watchers-deps
           validation-deps]}]
  (register-runtime! register-runtime-deps)
  (install-render-loop! render-loop-deps)
  (install-runtime-watchers! watchers-deps)
  (install-state-validation! validation-deps))
