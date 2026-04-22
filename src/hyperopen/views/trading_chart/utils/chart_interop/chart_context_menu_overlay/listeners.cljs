(ns hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.listeners
  (:require [hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.geometry :as geometry]
            [hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.support :as support]))

(defn- touch-context-menu-event?
  [event]
  (or (= "touch" (aget event "pointerType"))
      (true? (some-> event (aget "sourceCapabilities") (aget "firesTouchEvents")))))

(defn- secondary-mouse-context-menu-event?
  [event]
  (and (not (touch-context-menu-event? event))
       (or (= 2 (support/parse-number (aget event "button")))
           (= 2 (support/parse-number (aget event "buttons")))
           (= 3 (support/parse-number (aget event "which"))))))

(defn attach-container-listeners!
  [chart-obj container {:keys [open-menu close-menu]}]
  (let [on-context-menu (fn [event]
                          (when (secondary-mouse-context-menu-event? event)
                            (.preventDefault event)
                            (.stopPropagation event)
                            (when-not (support/event-target-inside-root? (:root (support/overlay-state chart-obj)) event)
                              (when-let [anchor (geometry/relative-anchor-point container event)]
                                (open-menu chart-obj anchor)))))
        on-key-down (fn [event]
                      (let [context-menu-key? (= "ContextMenu" (.-key event))
                            shift-f10? (and (= "F10" (.-key event))
                                            (true? (.-shiftKey event))
                                            (not (.-altKey event))
                                            (not (.-ctrlKey event))
                                            (not (.-metaKey event)))]
                        (when (or context-menu-key? shift-f10?)
                          (.preventDefault event)
                          (.stopPropagation event)
                          (open-menu chart-obj (geometry/keyboard-anchor-point container)))))
        on-wheel (fn [_]
                   (when (:menu-open? (support/overlay-state chart-obj))
                     (close-menu chart-obj {:restore-focus? false})))
        on-pointer-down (fn [event]
                          (when (and (:menu-open? (support/overlay-state chart-obj))
                                     (not (support/event-target-inside-root? (:root (support/overlay-state chart-obj)) event)))
                            (close-menu chart-obj {:restore-focus? false})))]
    (.addEventListener container "contextmenu" on-context-menu)
    (.addEventListener container "keydown" on-key-down)
    (.addEventListener container "wheel" on-wheel)
    (.addEventListener container "pointerdown" on-pointer-down)
    {:container container
     :on-context-menu on-context-menu
     :on-key-down on-key-down
     :on-wheel on-wheel
     :on-pointer-down on-pointer-down}))

(defn teardown-container-listeners!
  [{:keys [container on-context-menu on-key-down on-wheel on-pointer-down]}]
  (when container
    (when on-context-menu
      (.removeEventListener container "contextmenu" on-context-menu))
    (when on-key-down
      (.removeEventListener container "keydown" on-key-down))
    (when on-wheel
      (.removeEventListener container "wheel" on-wheel))
    (when on-pointer-down
      (.removeEventListener container "pointerdown" on-pointer-down))))

(defn attach-document-listeners!
  [chart-obj document {:keys [close-menu]}]
  (let [on-pointer-down (fn [event]
                          (let [{:keys [root container menu-open?]} (support/overlay-state chart-obj)
                                target (.-target event)]
                            (when (and menu-open?
                                       (not (support/node-inside? root target))
                                       (not (support/node-inside? container target)))
                              (close-menu chart-obj {:restore-focus? false}))))
        on-key-down (fn [event]
                      (when (and (:menu-open? (support/overlay-state chart-obj))
                                 (= "Escape" (.-key event)))
                        (.preventDefault event)
                        (.stopPropagation event)
                        (close-menu chart-obj)))]
    (.addEventListener document "pointerdown" on-pointer-down)
    (.addEventListener document "keydown" on-key-down)
    {:document document
     :on-pointer-down on-pointer-down
     :on-key-down on-key-down}))

(defn teardown-document-listeners!
  [{:keys [document on-pointer-down on-key-down]}]
  (when (and document on-pointer-down)
    (.removeEventListener document "pointerdown" on-pointer-down))
  (when (and document on-key-down)
    (.removeEventListener document "keydown" on-key-down)))
