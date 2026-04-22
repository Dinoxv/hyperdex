(ns hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.geometry
  (:require [hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.support :as support]))

(defn- event-client-x
  [event]
  (or (support/parse-number (.-clientX event))
      (support/parse-number (.-pageX event))
      (support/parse-number (.-x event))
      (support/parse-number (.-offsetX event))))

(defn- event-client-y
  [event]
  (or (support/parse-number (.-clientY event))
      (support/parse-number (.-pageY event))
      (support/parse-number (.-y event))
      (support/parse-number (.-offsetY event))))

(defn- container-rect
  [container]
  (try
    (when-let [rect-fn (some-> container (aget "getBoundingClientRect"))]
      (when (fn? rect-fn)
        (.call rect-fn container)))
    (catch :default _ nil)))

(defn- container-width
  [container rect]
  (or (some-> rect .-width support/parse-number)
      (support/parse-number (.-clientWidth container))
      0))

(defn- container-height
  [container rect]
  (or (some-> rect .-height support/parse-number)
      (support/parse-number (.-clientHeight container))
      0))

(defn relative-anchor-point
  [container event]
  (let [rect (container-rect container)
        left (some-> rect .-left support/parse-number)
        top (some-> rect .-top support/parse-number)
        client-x (event-client-x event)
        client-y (event-client-y event)
        offset-x (support/parse-number (.-offsetX event))
        offset-y (support/parse-number (.-offsetY event))
        x (cond
            (and (support/finite-number? client-x)
                 (support/finite-number? left))
            (- client-x left)

            (support/finite-number? offset-x)
            offset-x

            :else nil)
        y (cond
            (and (support/finite-number? client-y)
                 (support/finite-number? top))
            (- client-y top)

            (support/finite-number? offset-y)
            offset-y

            :else nil)]
    (when (and (support/finite-number? x)
               (support/finite-number? y))
      {:x x
       :y y})))

(defn keyboard-anchor-point
  [container]
  (let [rect (container-rect container)
        width (container-width container rect)
        height (container-height container rect)]
    {:x (max support/edge-padding-px
             (min (- width support/edge-padding-px) (/ width 2)))
     :y (max support/edge-padding-px
             (min (- height support/edge-padding-px) (/ height 2)))}))

(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn menu-position
  [container anchor]
  (let [rect (container-rect container)
        width (container-width container rect)
        height (container-height container rect)
        menu-height (support/menu-height-px)
        preferred-left (+ (:x anchor) support/anchor-offset-px)
        preferred-top (+ (:y anchor) support/anchor-offset-px)
        flipped-left (- (:x anchor) support/panel-width-px support/anchor-offset-px)
        flipped-top (- (:y anchor) menu-height support/anchor-offset-px)
        max-left (max support/edge-padding-px
                      (- width support/panel-width-px support/edge-padding-px))
        max-top (max support/edge-padding-px
                     (- height menu-height support/edge-padding-px))
        left (if (> (+ preferred-left support/panel-width-px support/edge-padding-px) width)
               flipped-left
               preferred-left)
        top (if (> (+ preferred-top menu-height support/edge-padding-px) height)
              flipped-top
              preferred-top)]
    {:left (clamp left support/edge-padding-px max-left)
     :top (clamp top support/edge-padding-px max-top)}))
