(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlay-dom)

(defn- apply-inline-style!
  [el style-map]
  (let [style (.-style el)]
    (doseq [[k v] style-map]
      (when (not= v (aget style k))
        (aset style k v))))
  el)

(defn- create-text-node!
  [document text]
  (.createTextNode document (or text "")))

(defn- set-text-node-value!
  [text-node text]
  (let [next-text (or text "")
        current-text (or (some-> text-node .-data)
                         (some-> text-node .-nodeValue)
                         "")]
    (when (not= next-text current-text)
      (aset text-node "data" next-text)
      (aset text-node "nodeValue" next-text)))
  text-node)

(defn- clear-children!
  [el]
  (loop []
    (when-let [child (.-firstChild el)]
      (.removeChild el child)
      (recur))))

(defn clear-overlay-root!
  [root]
  (when root
    (clear-children! root))
  root)

(defn- append-child!
  [parent child]
  (when (and parent child)
    (when-let [current-parent (.-parentNode child)]
      (when-not (identical? current-parent parent)
        (.removeChild current-parent child)))
    (when-not (identical? (.-parentNode child) parent)
      (.appendChild parent child)))
  child)

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

(defn ensure-overlay-root!
  [root container document]
  (let [mounted-root? (and root (identical? (.-parentNode root) container))
        next-root (if mounted-root?
                    root
                    (create-overlay-root! document))]
    (when (and root (not mounted-root?))
      (when-let [parent (.-parentNode root)]
        (.removeChild parent root)))
    (append-child! container next-root)
    next-root))

(defn- create-pnl-row-nodes!
  [document]
  (let [row (.createElement document "div")
        line (.createElement document "div")
        badge (.createElement document "div")
        badge-text (.createElement document "span")
        badge-text-node (create-text-node! document "")
        chip (.createElement document "div")
        chip-text (.createElement document "span")
        chip-text-node (create-text-node! document "")]
    (.setAttribute row "data-position-pnl-row" "true")
    (.setAttribute chip "data-position-pnl-price-chip" "true")
    (.appendChild badge-text badge-text-node)
    (.appendChild chip-text chip-text-node)
    (.appendChild badge badge-text)
    (.appendChild chip chip-text)
    (.appendChild row line)
    (.appendChild row badge)
    (.appendChild row chip)
    {:row row
     :line line
     :badge badge
     :badge-text badge-text
     :badge-text-node badge-text-node
     :chip chip
     :chip-text chip-text
     :chip-text-node chip-text-node}))

(defn hide-pnl-row!
  [{:keys [row badge-text-node chip-text-node]}]
  (apply-inline-style! row {"display" "none"})
  (set-text-node-value! badge-text-node "")
  (set-text-node-value! chip-text-node ""))

(defn apply-pnl-row!
  [{:keys [row line badge badge-text badge-text-node chip chip-text chip-text-node]}
   props]
  (apply-inline-style! row (:row-style props))
  (apply-inline-style! line (:line-style props))
  (apply-inline-style! badge (:badge-style props))
  (apply-inline-style! badge-text (:badge-text-style props))
  (set-text-node-value! badge-text-node (:badge-text props))
  (apply-inline-style! chip (:chip-style props))
  (apply-inline-style! chip-text (:chip-text-style props))
  (set-text-node-value! chip-text-node (:chip-text props)))

(defn- create-liquidation-row-nodes!
  [document begin-drag!]
  (let [row (.createElement document "div")
        hit-area (.createElement document "div")
        line (.createElement document "div")
        badge (.createElement document "div")
        label (.createElement document "span")
        label-text-node (create-text-node! document "")
        price (.createElement document "span")
        price-text-node (create-text-node! document "")
        drag-note (.createElement document "span")
        drag-note-text-node (create-text-node! document "")
        chip (.createElement document "div")
        chip-text (.createElement document "span")
        chip-text-node (create-text-node! document "")
        trigger-drag! (fn [source-node event]
                        (when event
                          (.preventDefault event)
                          (.stopPropagation event))
                        (when (fn? begin-drag!)
                          (begin-drag! source-node event)))
        on-hit-pointer-down (fn [event]
                              (trigger-drag! hit-area event))
        on-badge-pointer-down (fn [event]
                                (trigger-drag! badge event))]
    (.setAttribute row "data-position-liq-row" "true")
    (.setAttribute hit-area "data-position-liq-drag-hit" "true")
    (.setAttribute hit-area "data-position-margin-trigger" "true")
    (.setAttribute badge "title" "Drag to adjust liquidation target")
    (.setAttribute badge "data-position-liq-drag-handle" "true")
    (.setAttribute badge "data-position-margin-trigger" "true")
    (.setAttribute chip "data-position-liq-price-chip" "true")
    (.addEventListener hit-area "pointerdown" on-hit-pointer-down)
    (.addEventListener hit-area "mousedown" on-hit-pointer-down)
    (.addEventListener hit-area "touchstart" on-hit-pointer-down)
    (.addEventListener badge "pointerdown" on-badge-pointer-down)
    (.addEventListener badge "mousedown" on-badge-pointer-down)
    (.addEventListener badge "touchstart" on-badge-pointer-down)
    (.appendChild label label-text-node)
    (.appendChild price price-text-node)
    (.appendChild drag-note drag-note-text-node)
    (.appendChild chip-text chip-text-node)
    (.appendChild badge label)
    (.appendChild badge price)
    (.appendChild badge drag-note)
    (.appendChild chip chip-text)
    (.appendChild row hit-area)
    (.appendChild row line)
    (.appendChild row badge)
    (.appendChild row chip)
    {:row row
     :hit-area hit-area
     :line line
     :badge badge
     :label label
     :label-text-node label-text-node
     :price price
     :price-text-node price-text-node
     :drag-note drag-note
     :drag-note-text-node drag-note-text-node
     :chip chip
     :chip-text chip-text
     :chip-text-node chip-text-node}))

(defn hide-liquidation-row!
  [{:keys [row label-text-node price-text-node drag-note-text-node chip-text-node]}]
  (apply-inline-style! row {"display" "none"})
  (set-text-node-value! label-text-node "")
  (set-text-node-value! price-text-node "")
  (set-text-node-value! drag-note-text-node "")
  (set-text-node-value! chip-text-node ""))

(defn apply-liquidation-row!
  [{:keys [row hit-area line badge label label-text-node price price-text-node drag-note drag-note-text-node chip chip-text chip-text-node]}
   props]
  (apply-inline-style! row (:row-style props))
  (apply-inline-style! hit-area (:hit-area-style props))
  (apply-inline-style! line (:line-style props))
  (apply-inline-style! badge (:badge-style props))
  (apply-inline-style! label (:label-style props))
  (set-text-node-value! label-text-node (:label-text props))
  (apply-inline-style! price (:price-style props))
  (set-text-node-value! price-text-node (:price-text props))
  (apply-inline-style! drag-note (:drag-note-style props))
  (set-text-node-value! drag-note-text-node (:drag-note-text props))
  (apply-inline-style! chip (:chip-style props))
  (apply-inline-style! chip-text (:chip-text-style props))
  (set-text-node-value! chip-text-node (:chip-text props)))

(defn- create-overlay-dom!
  [document begin-drag!]
  {:document document
   :pnl (create-pnl-row-nodes! document)
   :liquidation (create-liquidation-row-nodes! document begin-drag!)})

(defn ensure-overlay-dom!
  [overlay-dom document root begin-drag!]
  (let [overlay-dom* (if (identical? document (get-in overlay-dom [:document]))
                       overlay-dom
                       (create-overlay-dom! document begin-drag!))]
    (append-child! root (get-in overlay-dom* [:pnl :row]))
    (append-child! root (get-in overlay-dom* [:liquidation :row]))
    overlay-dom*))
