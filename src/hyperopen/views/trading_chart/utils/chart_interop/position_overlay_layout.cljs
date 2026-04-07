(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlay-layout
  (:require [clojure.string :as str]
            [hyperopen.views.account-info.shared :as account-shared]))

(def ^:private long-line-color "rgba(34, 201, 151, 0.9)")
(def ^:private short-line-color "rgba(227, 95, 120, 0.9)")
(def ^:private long-badge-color "rgba(34, 201, 151, 0.14)")
(def ^:private short-badge-color "rgba(227, 95, 120, 0.14)")
(def ^:private liq-line-color "rgba(227, 95, 120, 0.88)")
(def ^:private liq-badge-color "rgba(227, 95, 120, 0.14)")
(def ^:private flat-line-color "rgba(148, 163, 184, 0.9)")
(def ^:private flat-badge-color "rgba(148, 163, 184, 0.14)")
(def ^:private long-text-color "rgb(151, 252, 228)")
(def ^:private short-text-color "rgb(244, 187, 198)")
(def ^:private flat-text-color "rgb(226, 232, 240)")
(def ^:private liq-text-color "rgb(255, 196, 203)")
(def ^:private liq-drag-text-color "rgb(252, 222, 157)")
(def ^:private pnl-chip-text-color "rgb(248, 250, 252)")
(def ^:private badge-char-width-px 6.2)
(def ^:private chart-edge-padding-px 12)
(def ^:private pnl-badge-left-anchor-ratio 0.17)
(def ^:private pnl-badge-left-anchor-min-px 96)
(def ^:private pnl-badge-left-anchor-max-px 180)
(def ^:private min-liquidation-drag-margin-amount 0.000001)
(def ^:private liquidation-drag-hit-height-px 14)

(defn finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))))

(defn non-negative-number
  [value fallback]
  (if (and (finite-number? value)
           (not (neg? value)))
    value
    fallback))

(defn parse-number
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

(defn clamp-badge-center-x
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

(defn visible-overlay-y?
  [height y]
  (and (finite-number? y)
       (or (zero? height)
           (and (> y -30)
                (< y (+ height 30))))))

(defn- pnl-tone
  [overlay]
  (let [pnl (parse-number (:unrealized-pnl overlay))]
    (cond
      (and (finite-number? pnl) (neg? pnl)) :loss
      (and (finite-number? pnl) (pos? pnl)) :profit
      :else :flat)))

(defn- pnl-line-color
  [overlay]
  (case (pnl-tone overlay)
    :loss short-line-color
    :profit long-line-color
    flat-line-color))

(defn- pnl-badge-color
  [overlay]
  (case (pnl-tone overlay)
    :loss short-badge-color
    :profit long-badge-color
    flat-badge-color))

(defn- pnl-text-color
  [overlay]
  (case (pnl-tone overlay)
    :loss short-text-color
    :profit long-text-color
    flat-text-color))

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

(defn- strip-dollar-prefix
  [text]
  (cond
    (str/starts-with? text "<$") (str "<" (subs text 2))
    (str/starts-with? text "$") (subs text 1)
    :else text))

(defn- format-axis-price-text
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
        text (some-> formatted str str/trim strip-dollar-prefix)]
    (if (seq text) text "0.00")))

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

(defn- event-client-coordinate
  [event k]
  (when event
    (parse-number (aget event k))))

(defn- event-client-y
  [event]
  (event-client-coordinate event "clientY"))

(defn- event-client-x
  [event]
  (event-client-coordinate event "clientX"))

(defn- number->px
  [value fallback]
  (if (finite-number? value)
    value
    fallback))

(defn event-anchor
  [overlay source-node event]
  (let [window* (:window overlay)
        viewport-width (or (some-> window* .-innerWidth parse-number)
                           (some-> js/globalThis .-innerWidth parse-number)
                           1280)
        viewport-height (or (some-> window* .-innerHeight parse-number)
                            (some-> js/globalThis .-innerHeight parse-number)
                            800)
        rect (try
               (when-let [method (some-> source-node (aget "getBoundingClientRect"))]
                 (when (fn? method)
                   (.call method source-node)))
               (catch :default _ nil))
        fallback-left (event-client-x event)
        fallback-top (event-client-y event)
        left (number->px (some-> rect (aget "left") parse-number) (or fallback-left 0))
        top (number->px (some-> rect (aget "top") parse-number) (or fallback-top 0))
        width (max 0 (number->px (some-> rect (aget "width") parse-number) 0))
        height (max 0 (number->px (some-> rect (aget "height") parse-number) 0))
        right (+ left width)
        bottom (+ top height)]
    {:left left
     :right right
     :top top
     :bottom bottom
     :width width
     :height height
     :viewport-width viewport-width
     :viewport-height viewport-height}))

(defn- liquidation-margin-delta
  [overlay current-liq-price target-liq-price]
  (let [abs-size (parse-number (:abs-size overlay))
        side (:side overlay)
        current* (parse-number current-liq-price)
        target* (parse-number target-liq-price)]
    (when (and (finite-number? abs-size)
               (pos? abs-size)
               (finite-number? current*)
               (pos? current*)
               (finite-number? target*)
               (pos? target*)
               (contains? #{:long :short} side))
      (case side
        :long (* abs-size (- current* target*))
        :short (* abs-size (- target* current*))
        nil))))

(defn liquidation-drag-suggestion
  [overlay current-liq-price target-liq-price]
  (let [margin-delta (liquidation-margin-delta overlay current-liq-price target-liq-price)
        margin-delta* (if (finite-number? margin-delta) margin-delta 0)
        abs-delta (when (finite-number? margin-delta)
                    (js/Math.abs margin-delta))]
    (when (and (finite-number? abs-delta)
               (>= abs-delta min-liquidation-drag-margin-amount))
      {:mode (if (neg? margin-delta*)
               :remove
               :add)
       :amount abs-delta
       :current-liquidation-price current-liq-price
       :target-liquidation-price target-liq-price})))

(defn liquidation-drag-label
  [overlay current-liq-price target-liq-price]
  (when-let [{:keys [mode amount]} (liquidation-drag-suggestion overlay
                                                                current-liq-price
                                                                target-liq-price)]
    (let [mode-label (if (= mode :remove) "Remove" "Add")
          amount-text (account-shared/format-currency amount)]
      (str mode-label " $" amount-text " Margin"))))

(defn pnl-row-props
  [overlay y width]
  (let [pnl-text (format-pnl-text (:unrealized-pnl overlay))
        size-text (format-size-text (:format-size overlay) (:abs-size overlay))
        pnl-label-text (str "PNL " pnl-text " | " size-text)
        estimated-badge-width (estimate-badge-width-px 56 pnl-label-text)
        center-x (clamp-badge-center-x width
                                       (preferred-pnl-badge-x width)
                                       estimated-badge-width)
        chip-color (pnl-line-color overlay)
        safe-width (non-negative-number width 0)]
    {:row-style {"position" "absolute"
                 "display" "block"
                 "left" "0px"
                 "right" "0px"
                 "top" (str y "px")
                 "height" "0px"
                 "pointerEvents" "none"}
     :line-style {"position" "absolute"
                  "left" "0px"
                  "right" "0px"
                  "top" "0px"
                  "borderTop" (str "1px dashed " chip-color)
                  "opacity" "0.88"
                  "pointerEvents" "none"}
     :badge-style {"position" "absolute"
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
                   "border" (str "1px solid " chip-color)
                   "background" (pnl-badge-color overlay)
                   "backdropFilter" "blur(0.5px)"
                   "color" (pnl-text-color overlay)
                   "pointerEvents" "none"}
     :badge-text-style {"whiteSpace" "nowrap"
                        "userSelect" "none"}
     :badge-text pnl-label-text
     :chip-style {"position" "absolute"
                  "left" (str safe-width "px")
                  "top" "0px"
                  "transform" "translate(2px, -50%)"
                  "display" "inline-flex"
                  "alignItems" "center"
                  "padding" "1px 6px"
                  "fontSize" "11px"
                  "lineHeight" "16px"
                  "fontWeight" "600"
                  "borderRadius" "2px"
                  "border" (str "1px solid " chip-color)
                  "background" chip-color
                  "color" pnl-chip-text-color
                  "whiteSpace" "nowrap"
                  "pointerEvents" "none"}
     :chip-text-style {"whiteSpace" "nowrap"}
     :chip-text (format-axis-price-text (:format-price overlay)
                                        (:entry-price overlay))}))

(defn liquidation-row-props
  [overlay y width]
  (let [liq-price-text (format-price-text (:format-price overlay)
                                          (:liquidation-price overlay))
        drag-label (liquidation-drag-label overlay
                                           (:current-liquidation-price overlay)
                                           (:liquidation-price overlay))
        liq-label-text (str "Liq. Price " liq-price-text)
        full-label-text (if (seq drag-label)
                          (str liq-label-text " | " drag-label)
                          liq-label-text)
        estimated-badge-width (estimate-badge-width-px 52 full-label-text)
        badge-x (clamp-badge-center-x width
                                      (+ chart-edge-padding-px
                                         (/ estimated-badge-width 2)
                                         10)
                                      estimated-badge-width)
        hit-half-height (/ liquidation-drag-hit-height-px 2)
        safe-width (non-negative-number width 0)]
    {:row-style {"position" "absolute"
                 "display" "block"
                 "left" "0px"
                 "right" "0px"
                 "top" (str y "px")
                 "height" "0px"
                 "pointerEvents" "none"}
     :hit-area-style {"position" "absolute"
                      "left" "0px"
                      "right" "0px"
                      "top" (str (- 0 hit-half-height) "px")
                      "height" (str liquidation-drag-hit-height-px "px")
                      "background" "transparent"
                      "cursor" "ns-resize"
                      "pointerEvents" "auto"}
     :line-style {"position" "absolute"
                  "left" "0px"
                  "right" "0px"
                  "top" "0px"
                  "borderTop" (str "1px dashed " liq-line-color)
                  "opacity" "0.84"
                  "pointerEvents" "none"}
     :badge-style {"position" "absolute"
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
                   "cursor" "ns-resize"
                   "pointerEvents" "auto"}
     :label-style {"whiteSpace" "nowrap"}
     :label-text "Liq. Price"
     :price-style {"whiteSpace" "nowrap"}
     :price-text liq-price-text
     :drag-note-style {"color" liq-drag-text-color
                       "whiteSpace" "nowrap"
                       "display" (if (seq drag-label) "inline" "none")}
     :drag-note-text drag-label
     :chip-style {"position" "absolute"
                  "left" (str safe-width "px")
                  "top" "0px"
                  "transform" "translate(2px, -50%)"
                  "display" "inline-flex"
                  "alignItems" "center"
                  "padding" "1px 6px"
                  "fontSize" "11px"
                  "lineHeight" "16px"
                  "fontWeight" "600"
                  "borderRadius" "2px"
                  "border" (str "1px solid " liq-line-color)
                  "background" liq-line-color
                  "color" pnl-chip-text-color
                  "whiteSpace" "nowrap"
                  "pointerEvents" "none"}
     :chip-text-style {"whiteSpace" "nowrap"}
     :chip-text (format-axis-price-text (:format-price overlay)
                                        (:liquidation-price overlay))}))
