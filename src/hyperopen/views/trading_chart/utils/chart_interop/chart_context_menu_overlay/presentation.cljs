(ns hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.presentation
  (:require [hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.support :as support]))

(defn sync-root-visibility!
  [chart-obj]
  (let [{:keys [root menu-open?]} (support/overlay-state chart-obj)
        visible? (boolean menu-open?)]
    (when root
      (let [style (.-style root)]
        (support/set-style-value! style "opacity" (if visible? "1" "0"))
        (support/set-style-value! style "pointerEvents" (if visible? "auto" "none"))
        (support/set-style-value! style "visibility" (if visible? "visible" "hidden"))))))

(defn sync-button-presentation!
  [button {:keys [disabled? copied?]}]
  (when button
    (let [style (.-style button)]
      (support/set-style-value! style "opacity" (if disabled? "0.45" "1"))
      (support/set-style-value! style "cursor" (if disabled? "default" "pointer"))
      (support/set-style-value! style "color" (if copied? "#f8fafc" "#d1d5db"))
      (support/set-style-value! style "background" "transparent"))
    (.setAttribute button "aria-disabled" (if disabled? "true" "false"))
    (aset button "disabled" (boolean disabled?))))

(defn sync-copy-button-label!
  [chart-obj]
  (let [{:keys [copy-button copy-label copied? copy-enabled?]} (support/overlay-state chart-obj)
        label-text (cond
                     copied? "Copied"
                     (seq copy-label) (str "Copy price " copy-label)
                     :else "Copy price --")]
    (when copy-button
      (set! (.-textContent copy-button) "")
      (.appendChild copy-button (aget copy-button "iconNode"))
      (let [label-node (aget copy-button "labelNode")]
        (set! (.-textContent label-node) label-text)
        (.appendChild copy-button label-node))
      (sync-button-presentation! copy-button {:disabled? (not copy-enabled?)
                                              :copied? copied?}))))

(defn sync-reset-button!
  [chart-obj]
  (when-let [reset-button (:reset-button (support/overlay-state chart-obj))]
    (sync-button-presentation! reset-button {:disabled? false
                                             :copied? false})))

(defn set-button-highlight!
  [button highlighted?]
  (when button
    (let [style (.-style button)]
      (support/set-style-value! style "background" (if highlighted?
                                                     "rgba(30, 41, 59, 0.9)"
                                                     "transparent"))
      (support/set-style-value! style "color" (if highlighted?
                                                "#f8fafc"
                                                (or (aget button "baseColor") "#d1d5db"))))))

(defn- icon-node
  [document path]
  (let [svg (.createElementNS document "http://www.w3.org/2000/svg" "svg")
        path-node (.createElementNS document "http://www.w3.org/2000/svg" "path")]
    (.setAttribute svg "viewBox" "0 0 20 20")
    (.setAttribute svg "aria-hidden" "true")
    (let [style (.-style svg)]
      (support/set-style-value! style "width" "14px")
      (support/set-style-value! style "height" "14px")
      (support/set-style-value! style "flex" "0 0 14px"))
    (.setAttribute path-node "d" path)
    (.setAttribute path-node "fill" "none")
    (.setAttribute path-node "stroke" "currentColor")
    (.setAttribute path-node "stroke-width" "1.5")
    (.setAttribute path-node "stroke-linecap" "round")
    (.setAttribute path-node "stroke-linejoin" "round")
    (.appendChild svg path-node)
    svg))

(defn- menu-button!
  [chart-obj document {:keys [item-id data-role label icon-path title]}
   {:keys [keydown-handler action-handler]}]
  (let [button (.createElement document "button")
        icon (icon-node document icon-path)
        label-node (.createElement document "span")
        on-keydown (keydown-handler item-id)]
    (.setAttribute button "type" "button")
    (.setAttribute button "role" "menuitem")
    (.setAttribute button "data-role" data-role)
    (.setAttribute button "title" title)
    (let [style (.-style button)]
      (support/set-style-value! style "display" "flex")
      (support/set-style-value! style "alignItems" "center")
      (support/set-style-value! style "gap" "10px")
      (support/set-style-value! style "width" "100%")
      (support/set-style-value! style "height" (str support/row-height-px "px"))
      (support/set-style-value! style "padding" "0 10px")
      (support/set-style-value! style "border" "none")
      (support/set-style-value! style "background" "transparent")
      (support/set-style-value! style "color" "#d1d5db")
      (support/set-style-value! style "textAlign" "left")
      (support/set-style-value! style "fontSize" "14px")
      (support/set-style-value! style "lineHeight" "1")
      (support/set-style-value! style "cursor" "pointer")
      (support/set-style-value! style "outline" "none")
      (support/set-style-value! style "borderRadius" "6px"))
    (aset button "baseColor" "#d1d5db")
    (aset button "iconNode" icon)
    (aset button "labelNode" label-node)
    (set! (.-textContent label-node) label)
    (.appendChild button icon)
    (.appendChild button label-node)
    (.addEventListener button "mouseenter" (fn [_] (set-button-highlight! button true)))
    (.addEventListener button "mouseleave" (fn [_]
                                             (when (not= item-id (:focused-item (support/overlay-state chart-obj)))
                                               (set-button-highlight! button false))))
    (.addEventListener button "focus" (fn [_]
                                        (support/update-overlay-state! chart-obj assoc :focused-item item-id)
                                        (set-button-highlight! button true)))
    (.addEventListener button "blur" (fn [_]
                                       (when (not= item-id (:focused-item (support/overlay-state chart-obj)))
                                         (set-button-highlight! button false))))
    (.addEventListener button "keydown" on-keydown)
    (.addEventListener button "click"
                       (fn [event]
                         (.preventDefault event)
                         (.stopPropagation event)
                         (action-handler item-id)))
    button))

(defn create-overlay-root!
  [chart-obj document {:keys [keydown-handler action-handler close-menu]}]
  (let [root (.createElement document "div")
        panel (.createElement document "div")
        button-callbacks {:keydown-handler keydown-handler
                          :action-handler action-handler}
        reset-button (menu-button! chart-obj
                                   document
                                   {:item-id :reset-view
                                    :data-role "chart-context-menu-reset"
                                    :label "Reset chart view"
                                    :title "Reset chart view"
                                    :icon-path "M15 6.8V4.5h-2.3M15 4.5l-2.4 2.3M14.7 10a4.7 4.7 0 1 1-1.3-3.2"}
                                   button-callbacks)
        divider (.createElement document "div")
        copy-button (menu-button! chart-obj
                                  document
                                  {:item-id :copy-price
                                   :data-role "chart-context-menu-copy"
                                   :label "Copy price --"
                                   :title "Copy price"
                                   :icon-path "M7 7.5h6.5v8H7zM5 12H3.8A1.8 1.8 0 0 1 2 10.2V3.8A1.8 1.8 0 0 1 3.8 2h6.4A1.8 1.8 0 0 1 12 3.8V5"}
                                  button-callbacks)]
    (.setAttribute root "data-role" "chart-context-menu")
    (.setAttribute panel "role" "menu")
    (.setAttribute panel "aria-label" "Chart context menu")
    (let [style (.-style root)]
      (support/set-style-value! style "position" "absolute")
      (support/set-style-value! style "zIndex" "118")
      (support/set-style-value! style "opacity" "0")
      (support/set-style-value! style "pointerEvents" "none")
      (support/set-style-value! style "visibility" "hidden"))
    (let [style (.-style panel)]
      (support/set-style-value! style "display" "flex")
      (support/set-style-value! style "flexDirection" "column")
      (support/set-style-value! style "width" (str support/panel-width-px "px"))
      (support/set-style-value! style "padding" (str support/panel-padding-px "px"))
      (support/set-style-value! style "background" "rgba(10, 14, 20, 0.98)")
      (support/set-style-value! style "border" "1px solid rgba(56, 65, 79, 0.95)")
      (support/set-style-value! style "boxShadow" "0 10px 28px rgba(0, 0, 0, 0.35)")
      (support/set-style-value! style "borderRadius" "10px")
      (support/set-style-value! style "pointerEvents" "auto"))
    (let [style (.-style divider)]
      (support/set-style-value! style "height" (str support/divider-height-px "px"))
      (support/set-style-value! style "margin" "4px 0")
      (support/set-style-value! style "background" "rgba(56, 65, 79, 0.9)"))
    (.addEventListener panel "keydown"
                       (fn [event]
                         (when (= "Escape" (.-key event))
                           (.preventDefault event)
                           (.stopPropagation event)
                           (close-menu chart-obj))))
    (.appendChild panel reset-button)
    (.appendChild panel divider)
    (.appendChild panel copy-button)
    (.appendChild root panel)
    {:root root
     :panel panel
     :reset-button reset-button
     :copy-button copy-button}))

(defn ensure-overlay-root!
  [chart-obj container document callbacks]
  (support/ensure-relative-container! container)
  (let [{:keys [root panel reset-button copy-button]} (support/overlay-state chart-obj)
        mounted-root? (and root (identical? (.-parentNode root) container))
        next-root (if mounted-root?
                    {:root root
                     :panel panel
                     :reset-button reset-button
                     :copy-button copy-button}
                    (create-overlay-root! chart-obj document callbacks))]
    (when (and root (not mounted-root?))
      (when-let [parent (.-parentNode root)]
        (.removeChild parent root)))
    (when-let [root-node (:root next-root)]
      (when (not (identical? (.-parentNode root-node) container))
        (.appendChild container root-node)))
    next-root))
