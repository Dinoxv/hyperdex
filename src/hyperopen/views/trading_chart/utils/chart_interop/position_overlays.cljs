(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlays
  (:require [clojure.string :as str]
            [hyperopen.views.account-info.shared :as account-shared]))

(defonce ^:private position-overlays-sidecar (js/WeakMap.))

(def ^:private long-line-color "rgba(34, 201, 151, 0.9)")
(def ^:private short-line-color "rgba(227, 95, 120, 0.9)")
(def ^:private long-badge-color "rgba(34, 201, 151, 0.14)")
(def ^:private short-badge-color "rgba(227, 95, 120, 0.14)")
(def ^:private liq-line-color "rgba(227, 95, 120, 0.88)")
(def ^:private liq-badge-color "rgba(227, 95, 120, 0.14)")
(def ^:private dark-badge-bg "rgba(7, 17, 25, 0.82)")
(def ^:private long-text-color "rgb(151, 252, 228)")
(def ^:private short-text-color "rgb(244, 187, 198)")
(def ^:private liq-text-color "rgb(255, 196, 203)")
(def ^:private badge-char-width-px 6.2)
(def ^:private chart-edge-padding-px 12)
(def ^:private min-visible-pnl-segment-px 24)
(def ^:private pnl-badge-left-anchor-ratio 0.17)
(def ^:private pnl-badge-left-anchor-min-px 96)
(def ^:private pnl-badge-left-anchor-max-px 180)

(defn- overlay-state
  [chart-obj]
  (if chart-obj
    (or (.get position-overlays-sidecar chart-obj) {})
    {}))

(defn- set-overlay-state!
  [chart-obj state]
  (when chart-obj
    (.set position-overlays-sidecar chart-obj state))
  state)

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))))

(defn- non-negative-number
  [value fallback]
  (if (and (finite-number? value)
           (not (neg? value)))
    value
    fallback))

(defn- parse-number
  [value]
  (account-shared/parse-optional-num value))

(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn- right-label-reserve-px
  [width]
  (clamp (* (non-negative-number width 0) 0.14)
         88
         168))

(defn- estimate-badge-width-px
  [base-width text]
  (let [chars (count (or (some-> text str) ""))]
    (clamp (+ base-width (* chars badge-char-width-px))
           72
           300)))

(defn- clamp-badge-center-x
  [width preferred-x badge-width]
  (let [safe-width (non-negative-number width 0)
        half-width (/ badge-width 2)
        min-x (+ chart-edge-padding-px half-width)
        max-x (- safe-width
                 (right-label-reserve-px safe-width)
                 half-width)
        fallback (/ safe-width 2)]
    (if (<= max-x min-x)
      fallback
      (clamp (non-negative-number preferred-x fallback) min-x max-x))))

(defn- preferred-pnl-badge-x
  [width]
  (let [safe-width (non-negative-number width 0)]
    (clamp (* safe-width pnl-badge-left-anchor-ratio)
           pnl-badge-left-anchor-min-px
           pnl-badge-left-anchor-max-px)))

(defn- apply-inline-style!
  [el style-map]
  (doseq [[k v] style-map]
    (aset (.-style el) k v))
  el)

(defn- invoke-method
  [target method-name & args]
  (let [method (when target
                 (aget target method-name))]
    (when (fn? method)
      (.apply method target (to-array args)))))

(defn- clear-children!
  [el]
  (loop []
    (when-let [child (.-firstChild el)]
      (.removeChild el child)
      (recur))))

(defn- resolve-document
  [document]
  (or document
      (some-> js/globalThis .-document)))

(defn- side-key
  [overlay]
  (if (= :short (:side overlay)) :short :long))

(defn- side-line-color
  [overlay]
  (if (= :short (side-key overlay)) short-line-color long-line-color))

(defn- side-badge-color
  [overlay]
  (if (= :short (side-key overlay)) short-badge-color long-badge-color))

(defn- side-text-color
  [overlay]
  (if (= :short (side-key overlay)) short-text-color long-text-color))

(defn- format-price-text
  [format-price value]
  (let [formatted (or (when (fn? format-price)
                         (or (try
                               (format-price value value)
                               (catch :default _ nil))
                             (try
                               (format-price value)
                               (catch :default _ nil))))
                       (account-shared/format-trade-price value)
                       "0.00")
        text (some-> formatted str str/trim)]
    (cond
      (not (seq text)) "$0.00"
      (or (str/starts-with? text "$")
          (str/starts-with? text "<$"))
      text
      :else
      (str "$" text))))

(defn- format-size-text
  [format-size value]
  (or (when (fn? format-size)
        (format-size value))
      (account-shared/format-currency value)
      "0.00"))

(defn- format-pnl-text
  [pnl]
  (let [pnl* (or (parse-number pnl) 0)
        sign (cond
               (pos? pnl*) "+"
               (neg? pnl*) "-"
               :else "")]
    (str sign "$" (account-shared/format-currency (js/Math.abs pnl*)))))

(defn- create-overlay-root!
  [document]
  (let [root (.createElement document "div")]
    (set! (.-className root) "chart-position-overlays")
    (apply-inline-style!
     root
     {"position" "absolute"
      "inset" "0px"
      "pointerEvents" "none"
      "zIndex" "13"
      "overflow" "hidden"})
    root))

(defn- ensure-overlay-root!
  [chart-obj container document]
  (let [{:keys [root]} (overlay-state chart-obj)
        mounted-root? (and root (identical? (.-parentNode root) container))
        next-root (if mounted-root?
                    root
                    (create-overlay-root! document))]
    (when (and root (not mounted-root?))
      (when-let [parent (.-parentNode root)]
        (.removeChild parent root)))
    (when (and next-root
               (not (identical? (.-parentNode next-root) container)))
      (.appendChild container next-root))
    next-root))

(defn- render-pnl-badge!
  [document row overlay center-x pnl-label-text]
  (let [badge (.createElement document "div")
        text-node (.createElement document "span")]
    (apply-inline-style!
     badge
     {"position" "absolute"
      "left" (str center-x "px")
      "top" "0px"
      "transform" "translate(-50%, -50%)"
      "display" "inline-flex"
      "alignItems" "center"
      "padding" "2px 7px"
      "fontSize" "11px"
      "lineHeight" "16px"
      "fontWeight" "600"
      "borderRadius" "3px"
      "border" (str "1px solid " (side-line-color overlay))
      "background" (side-badge-color overlay)
      "backdropFilter" "blur(0.5px)"
      "color" (side-text-color overlay)
      "pointerEvents" "none"})
    (set! (.-textContent text-node) pnl-label-text)
    (apply-inline-style!
     text-node
     {"whiteSpace" "nowrap"
      "userSelect" "none"})
    (.appendChild badge text-node)
    (.appendChild row badge)
    row))

(defn- build-pnl-row!
  [document overlay start-x end-x y width]
  (let [segment-left (min start-x end-x)
        segment-right (max start-x end-x)
        raw-segment-width (max 0 (- segment-right segment-left))
        show-segment-line? (>= raw-segment-width min-visible-pnl-segment-px)
        row (.createElement document "div")
        line (.createElement document "div")
        pnl-text (format-pnl-text (:unrealized-pnl overlay))
        size-text (format-size-text (:format-size overlay) (:abs-size overlay))
        pnl-label-text (str "PNL " pnl-text " | " size-text)
        estimated-badge-width (estimate-badge-width-px 56 pnl-label-text)
        center-x (clamp-badge-center-x width
                                       (preferred-pnl-badge-x width)
                                       estimated-badge-width)]
    (apply-inline-style!
     row
     {"position" "absolute"
      "left" "0px"
      "right" "0px"
      "top" (str y "px")
      "height" "0px"
      "pointerEvents" "none"})
    (when show-segment-line?
      (apply-inline-style!
       line
       {"position" "absolute"
        "left" (str segment-left "px")
        "width" (str raw-segment-width "px")
        "top" "0px"
        "borderTop" (str "2px solid " (side-line-color overlay))
        "opacity" "0.92"
        "pointerEvents" "none"})
      (.appendChild row line))
    (render-pnl-badge! document row overlay center-x pnl-label-text)
    row))

(defn- build-liquidation-row!
  [document overlay y width]
  (let [row (.createElement document "div")
        line (.createElement document "div")
        badge (.createElement document "div")
        label (.createElement document "span")
        price (.createElement document "span")
        liq-price-text (format-price-text (:format-price overlay)
                                          (:liquidation-price overlay))
        liq-label-text (str "Liq. Price " liq-price-text)
        estimated-badge-width (estimate-badge-width-px 52 liq-label-text)
        badge-x (clamp-badge-center-x width
                                      (+ chart-edge-padding-px
                                         (/ estimated-badge-width 2)
                                         10)
                                      estimated-badge-width)]
    (apply-inline-style!
     row
     {"position" "absolute"
      "left" "0px"
      "right" "0px"
      "top" (str y "px")
      "height" "0px"
      "pointerEvents" "none"})
    (apply-inline-style!
     line
     {"position" "absolute"
      "left" "0px"
      "right" "0px"
      "top" "0px"
      "borderTop" (str "1px dashed " liq-line-color)
      "opacity" "0.84"
      "pointerEvents" "none"})
    (apply-inline-style!
     badge
     {"position" "absolute"
      "left" (str badge-x "px")
      "top" "0px"
      "transform" "translate(-50%, -50%)"
      "display" "inline-flex"
      "alignItems" "center"
      "gap" "6px"
      "padding" "2px 6px"
      "fontSize" "11px"
      "lineHeight" "16px"
      "fontWeight" "600"
      "borderRadius" "3px"
      "border" (str "1px solid " liq-line-color)
      "background" liq-badge-color
      "backdropFilter" "blur(0.5px)"
      "color" liq-text-color
      "pointerEvents" "none"})
    (set! (.-textContent label) "Liq. Price")
    (set! (.-textContent price) liq-price-text)
    (.appendChild badge label)
    (.appendChild badge price)
    (.appendChild row line)
    (.appendChild row badge)
    row))

(declare render-overlays!)

(defn- teardown-subscription!
  [{:keys [time-scale main-series repaint]}]
  (when repaint
    (invoke-method time-scale "unsubscribeVisibleTimeRangeChange" repaint)
    (invoke-method time-scale "unsubscribeVisibleLogicalRangeChange" repaint)
    (invoke-method time-scale "unsubscribeSizeChange" repaint)
    (invoke-method main-series "unsubscribeDataChanged" repaint)))

(defn- subscribe-overlay-repaint!
  [chart-obj chart main-series]
  (let [time-scale (invoke-method chart "timeScale")
        repaint (fn [_]
                  (render-overlays! chart-obj))]
    (invoke-method time-scale "subscribeVisibleTimeRangeChange" repaint)
    (invoke-method time-scale "subscribeVisibleLogicalRangeChange" repaint)
    (invoke-method time-scale "subscribeSizeChange" repaint)
    (invoke-method main-series "subscribeDataChanged" repaint)
    {:chart chart
     :main-series main-series
     :time-scale time-scale
     :repaint repaint}))

(defn- resolve-time-coordinate
  [chart entry-time]
  (when (finite-number? entry-time)
    (let [time-scale (invoke-method chart "timeScale")]
      (invoke-method time-scale "timeToCoordinate" entry-time))))

(defn render-overlays!
  [chart-obj]
  (let [{:keys [root chart main-series overlay]} (overlay-state chart-obj)
        document (:document overlay)
        format-price (:format-price overlay)
        format-size (:format-size overlay)]
    (when root
      (clear-children! root)
      (when (and (map? overlay)
                 main-series
                 document)
        (let [entry-price (parse-number (:entry-price overlay))
              entry-y (when (and (finite-number? entry-price)
                                 (pos? entry-price))
                        (invoke-method main-series "priceToCoordinate" entry-price))
              liq-price (parse-number (:liquidation-price overlay))
              liq-y (when (and (finite-number? liq-price)
                               (pos? liq-price))
                      (invoke-method main-series "priceToCoordinate" liq-price))
              pane-size (invoke-method chart "paneSize" 0)
              pane-width (some-> pane-size (aget "width"))
              width (non-negative-number pane-width
                                         (non-negative-number (.-clientWidth root) 0))
              latest-time (parse-number (:latest-time overlay))
              entry-time (parse-number (:entry-time overlay))
              latest-x (or (resolve-time-coordinate chart latest-time)
                           (- width 8))
              entry-x (or (resolve-time-coordinate chart entry-time)
                          (max 0 (- latest-x 260)))
              start-x (clamp (non-negative-number entry-x 0) 0 (max 0 width))
              end-x (clamp (non-negative-number latest-x width) 0 (max 0 width))
              height (or (.-clientHeight root) 0)
              overlay* (assoc overlay
                              :document document
                              :format-price format-price
                              :format-size format-size)]
          (when (and (finite-number? entry-y)
                     (or (zero? height)
                         (and (> entry-y -30)
                              (< entry-y (+ height 30)))))
            (.appendChild root
                          (build-pnl-row! document overlay* start-x end-x entry-y width)))
          (when (and (finite-number? liq-y)
                     (or (zero? height)
                         (and (> liq-y -30)
                              (< liq-y (+ height 30)))))
            (.appendChild root
                          (build-liquidation-row! document overlay* liq-y width))))))))

(defn clear-position-overlays!
  [chart-obj]
  (when chart-obj
    (let [{:keys [root subscription]} (overlay-state chart-obj)]
      (teardown-subscription! subscription)
      (when root
        (clear-children! root)
        (when-let [parent (.-parentNode root)]
          (.removeChild parent root)))
      (.delete position-overlays-sidecar chart-obj))))

(defn sync-position-overlays!
  ([chart-obj container overlay]
   (sync-position-overlays! chart-obj container overlay {}))
  ([chart-obj container overlay {:keys [document format-price format-size]
                                 :or {format-price account-shared/format-trade-price
                                      format-size account-shared/format-currency}}]
   (if-not (and chart-obj container)
     (clear-position-overlays! chart-obj)
     (let [chart (.-chart ^js chart-obj)
           main-series (.-mainSeries ^js chart-obj)
           document* (resolve-document document)
           overlay-ref (when (map? overlay) overlay)]
       (if (and chart main-series document* overlay-ref)
         (let [root (ensure-overlay-root! chart-obj container document*)
               state (overlay-state chart-obj)
               current-subscription (:subscription state)
               needs-resubscribe?
               (or (nil? current-subscription)
                   (not (identical? chart (:chart current-subscription)))
                   (not (identical? main-series (:main-series current-subscription))))
               next-subscription (if needs-resubscribe?
                                   (do
                                     (teardown-subscription! current-subscription)
                                     (subscribe-overlay-repaint! chart-obj chart main-series))
                                   current-subscription)
               unchanged-inputs?
               (and (not needs-resubscribe?)
                    (identical? root (:root state))
                    (identical? chart (:chart state))
                    (identical? main-series (:main-series state))
                    (identical? document* (:document state))
                    (identical? format-price (:format-price state))
                    (identical? format-size (:format-size state))
                    (identical? overlay-ref (:overlay-ref state)))]
           (if unchanged-inputs?
             state
             (do
               (set-overlay-state!
                chart-obj
                (assoc state
                       :root root
                       :chart chart
                       :main-series main-series
                       :overlay-ref overlay-ref
                       :overlay (assoc overlay-ref
                                       :document document*
                                       :format-price format-price
                                       :format-size format-size)
                       :document document*
                       :format-price format-price
                       :format-size format-size
                       :subscription next-subscription))
               (render-overlays! chart-obj))))
         (clear-position-overlays! chart-obj))))))
