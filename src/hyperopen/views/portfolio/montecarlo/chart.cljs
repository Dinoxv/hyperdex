(ns hyperopen.views.portfolio.montecarlo.chart
  "Canvas renderers for the Monte Carlo surface, attached through Replicant's
  `:replicant/on-render` lifecycle hook. The spaghetti/confidence-band chart can
  draw ~120 paths over hundreds of days, which is far too many nodes for SVG, so
  it draws to a `<canvas>` created imperatively inside the host element — a port
  of the designer prototype `charts.js`.

  All animation state (a `requestAnimationFrame` reveal sweep) is kept local to
  the renderer in a per-element holder atom, never in app state, consistent with
  the project's rule that live objects and timers stay out of core state."
  (:require [hyperopen.portfolio.montecarlo.engine :as engine]))

(def ^:private palette
  {:accent "#00d4aa"
   :red "#ff6b6b"
   :gold "#d4b558"
   :border "#21333b"
   :text-2 "#8b9ba2"
   :text-3 "#5d6f76"})

(def ^:private mono-font "\"JetBrains Mono\", ui-monospace, monospace")

(defn- axis-time-label
  "Compact elapsed-time tick label (the x-axis is calendar time, not a day count
  — vault points are sparse and irregular)."
  [y]
  (cond
    (<= y 0) "0"
    (< y (/ 1.0 12)) (str (js/Math.round (* y 365)) "d")
    (< y 1) (str (js/Math.round (* y 12)) "mo")
    :else (str (.toFixed y 1) "y")))

(defn- hex->rgba
  [hex a]
  (let [h (subs hex 1)
        r (js/parseInt (subs h 0 2) 16)
        g (js/parseInt (subs h 2 4) 16)
        b (js/parseInt (subs h 4 6) 16)]
    (str "rgba(" r "," g "," b "," a ")")))

(defn- setup!
  "Size the canvas for the device pixel ratio and return its 2d context plus CSS
  width/height."
  [canvas css-h]
  (let [dpr (or (.-devicePixelRatio js/window) 1)
        cw (.-clientWidth canvas)
        w (if (pos? cw) cw (.. canvas -parentElement -clientWidth))
        ctx (.getContext canvas "2d")]
    (set! (.-width canvas) (js/Math.round (* w dpr)))
    (set! (.-height canvas) (js/Math.round (* css-h dpr)))
    (set! (.. canvas -style -height) (str css-h "px"))
    (.setTransform ctx dpr 0 0 dpr 0 0)
    {:ctx ctx :w w :h css-h}))

;; ---------------------------------------------------------------------------
;; Spaghetti + confidence-band equity chart
;; ---------------------------------------------------------------------------

(defn draw-spaghetti!
  [canvas {:keys [result accent band bust-color show-paths? path-count height progress]}]
  (let [{:keys [ctx w h]} (setup! canvas (or height 420))
        pad-l 64 pad-r 16 pad-t 14 pad-b 26
        accent (or accent (:accent palette))
        band-col (or band accent)
        bust-col (or bust-color (:red palette))
        start (get-in result [:meta :start-equity])
        H (get-in result [:meta :horizon])
        span-years (or (get-in result [:meta :span-years]) 0)
        bust (get-in result [:meta :bust])
        goal (get-in result [:meta :goal])
        progress (if (nil? progress) 1 progress)
        {:keys [p5 p25 p50 p75 p95]} (:band result)
        times (:times result)
        n (count times)
        bust-eq (* start (+ 1 bust))
        goal-eq (* start (+ 1 goal))
        lo0 (reduce min (-> (vec p5) (conj bust-eq) (conj start)))
        hi0 (reduce max (-> (vec p95) (conj goal-eq) (conj start)))
        range0 (max 1e-9 (- hi0 lo0))
        pad-range (* range0 0.06)
        lo (- lo0 pad-range)
        hi (+ hi0 pad-range)
        plot-w (- w pad-l pad-r)
        plot-h (- h pad-t pad-b)
        x-of (fn [t] (+ pad-l (* (/ t H) plot-w)))
        y-of (fn [v] (+ pad-t (* (- 1 (/ (- v lo) (- hi lo))) plot-h)))
        tx (fn [i] (+ pad-l (* (/ (nth times i) H) plot-w)))
        max-i (max 1 (js/Math.floor (* progress (dec n))))]
    (.clearRect ctx 0 0 w h)
    ;; bust zone shading + dashed threshold
    (let [bust-y (y-of bust-eq)]
      (when (< bust-y (+ pad-t plot-h))
        (set! (.-fillStyle ctx) (hex->rgba bust-col 0.07))
        (.fillRect ctx pad-l bust-y plot-w (- (+ pad-t plot-h) bust-y))
        (set! (.-strokeStyle ctx) (hex->rgba bust-col 0.55))
        (.setLineDash ctx #js [4 4])
        (set! (.-lineWidth ctx) 1)
        (.beginPath ctx)
        (.moveTo ctx pad-l bust-y)
        (.lineTo ctx (+ pad-l plot-w) bust-y)
        (.stroke ctx)
        (.setLineDash ctx #js [])))
    ;; horizontal grid + return-percent labels
    (set! (.-font ctx) (str "10px " mono-font))
    (set! (.-textBaseline ctx) "middle")
    (dotimes [i 6]
      (let [v (+ lo (* (- hi lo) (/ i 5)))
            y (y-of v)
            ret-pct (* (- (/ v start) 1) 100)]
        (set! (.-strokeStyle ctx) (hex->rgba (:border palette) 0.6))
        (set! (.-lineWidth ctx) 1)
        (.beginPath ctx)
        (.moveTo ctx pad-l y)
        (.lineTo ctx (+ pad-l plot-w) y)
        (.stroke ctx)
        (set! (.-fillStyle ctx) (:text-3 palette))
        (set! (.-textAlign ctx) "right")
        (.fillText ctx (str (when (>= ret-pct 0) "+") (.toFixed ret-pct 0) "%") (- pad-l 10) y)))
    ;; x-axis elapsed-time labels (calendar time, not a day count)
    (set! (.-textAlign ctx) "center")
    (set! (.-textBaseline ctx) "top")
    (dotimes [i 5]
      (let [frac (/ i 4)
            t (js/Math.round (* frac H))]
        (set! (.-fillStyle ctx) (:text-3 palette))
        (.fillText ctx (axis-time-label (* frac span-years)) (x-of t) (+ pad-t plot-h 8))))
    ;; p5..p95 band
    (.beginPath ctx)
    (doseq [i (range 0 (inc max-i))] (.lineTo ctx (tx i) (y-of (nth p95 i))))
    (doseq [i (range max-i -1 -1)] (.lineTo ctx (tx i) (y-of (nth p5 i))))
    (.closePath ctx)
    (set! (.-fillStyle ctx) (hex->rgba band-col 0.10))
    (.fill ctx)
    ;; inner p25..p75 band
    (.beginPath ctx)
    (doseq [i (range 0 (inc max-i))] (.lineTo ctx (tx i) (y-of (nth p75 i))))
    (doseq [i (range max-i -1 -1)] (.lineTo ctx (tx i) (y-of (nth p25 i))))
    (.closePath ctx)
    (set! (.-fillStyle ctx) (hex->rgba band-col 0.16))
    (.fill ctx)
    ;; faint individual sampled paths
    (when show-paths?
      (let [paths (:draw-paths result)
            shown (min (count paths) (or path-count 120))
            last-t (js/Math.floor (* progress H))]
        (set! (.-lineWidth ctx) 0.8)
        (dotimes [p shown]
          (let [path (nth paths p)]
            (set! (.-strokeStyle ctx) (hex->rgba accent 0.05))
            (.beginPath ctx)
            (loop [t 0]
              (when (<= t last-t)
                (let [x (x-of t) y (y-of (aget path t))]
                  (if (zero? t) (.moveTo ctx x y) (.lineTo ctx x y)))
                (recur (inc t))))
            (.stroke ctx)))))
    ;; median line
    (set! (.-lineWidth ctx) 2)
    (set! (.-strokeStyle ctx) accent)
    (.beginPath ctx)
    (doseq [i (range 0 (inc max-i))]
      (let [x (tx i) y (y-of (nth p50 i))]
        (if (zero? i) (.moveTo ctx x y) (.lineTo ctx x y))))
    (.stroke ctx)
    ;; p5 / p95 dashed edges
    (set! (.-lineWidth ctx) 1)
    (.setLineDash ctx #js [5 4])
    (doseq [edge [p95 p5]]
      (set! (.-strokeStyle ctx) (hex->rgba band-col 0.5))
      (.beginPath ctx)
      (doseq [i (range 0 (inc max-i))]
        (let [x (tx i) y (y-of (nth edge i))]
          (if (zero? i) (.moveTo ctx x y) (.lineTo ctx x y))))
      (.stroke ctx))
    (.setLineDash ctx #js [])
    ;; start-equity marker
    (set! (.-strokeStyle ctx) (hex->rgba (:text-2 palette) 0.4))
    (set! (.-lineWidth ctx) 1)
    (.beginPath ctx)
    (.moveTo ctx pad-l (y-of start))
    (.lineTo ctx (+ pad-l plot-w) (y-of start))
    (.stroke ctx)
    ;; goal line (gold)
    (when (<= goal-eq hi)
      (let [gy (y-of goal-eq)]
        (set! (.-strokeStyle ctx) (hex->rgba (:gold palette) 0.6))
        (.setLineDash ctx #js [4 4])
        (.beginPath ctx)
        (.moveTo ctx pad-l gy)
        (.lineTo ctx (+ pad-l plot-w) gy)
        (.stroke ctx)
        (.setLineDash ctx #js [])
        (set! (.-fillStyle ctx) (:gold palette))
        (set! (.-font ctx) (str "9px " mono-font))
        (set! (.-textAlign ctx) "left")
        (set! (.-textBaseline ctx) "bottom")
        (.fillText ctx "GOAL" (+ pad-l 4) (- gy 3))))
    ;; bust label
    (let [bust-y (y-of bust-eq)]
      (when (< bust-y (+ pad-t plot-h))
        (set! (.-fillStyle ctx) bust-col)
        (set! (.-font ctx) (str "9px " mono-font))
        (set! (.-textAlign ctx) "left")
        (set! (.-textBaseline ctx) "top")
        (.fillText ctx "BUST" (+ pad-l 4) (+ bust-y 3))))
    ;; endpoint dots once the reveal completes
    (when (>= progress 0.999)
      (let [ex (tx (dec n))]
        (doseq [[edge col r] [[p95 band-col 2.3] [p50 accent 3.2] [p5 band-col 2.3]]]
          (let [y (y-of (nth edge (dec n)))]
            (set! (.-fillStyle ctx) col)
            (.beginPath ctx)
            (.arc ctx ex y r 0 (* 2 js/Math.PI))
            (.fill ctx)))))))

;; ---------------------------------------------------------------------------
;; Distribution histogram
;; ---------------------------------------------------------------------------

(defn draw-histogram!
  [canvas {:keys [dist fmt accent threshold sign-by-value? median height bins progress]}]
  (let [{:keys [ctx w h]} (setup! canvas (or height 92))
        bins (or bins 34)
        hist (engine/histogram (:raw dist) bins)
        counts (:counts hist)
        max-c (max 1 (reduce max counts))
        accent (or accent (:accent palette))
        neg (:red palette)
        is-pct (= fmt :pct)
        progress (if (nil? progress) 1 progress)
        padb 16
        plot-h (- h padb)
        bw (/ w bins)
        mn (:min hist)
        span (let [s (:span hist)] (if (zero? s) 1 s))
        bin-width (:bin-width hist)
        fmt-fn (fn [v]
                 (if is-pct
                   (str (when (>= v 0) "+") (.toFixed (* v 100) 0) "%")
                   (.toFixed v 2)))]
    (.clearRect ctx 0 0 w h)
    (dotimes [i bins]
      (let [bin-val (+ mn (* (+ i 0.5) bin-width))
            bh (* (/ (nth counts i) max-c) plot-h progress)
            x (* i bw)
            col (cond
                  (and (some? threshold) (<= bin-val threshold)) neg
                  (and sign-by-value? (< bin-val 0)) neg
                  :else accent)]
        (set! (.-fillStyle ctx) (hex->rgba col 0.62))
        (.fillRect ctx (+ x 0.5) (- plot-h bh) (max 1 (- bw 1)) bh)))
    (when (number? median)
      (let [mx (if (:degenerate? hist)
                 (/ w 2)                       ; centered spike for a single-valued metric
                 (* (/ (- median mn) span) w))]
        (set! (.-strokeStyle ctx) "#ffffff")
        (set! (.-globalAlpha ctx) 0.8)
        (set! (.-lineWidth ctx) 1.5)
        (.beginPath ctx)
        (.moveTo ctx mx 0)
        (.lineTo ctx mx plot-h)
        (.stroke ctx)
        (set! (.-globalAlpha ctx) 1)))
    (set! (.-font ctx) (str "9px " mono-font))
    (set! (.-fillStyle ctx) (:text-3 palette))
    (set! (.-textBaseline ctx) "top")
    (set! (.-textAlign ctx) "left")
    (.fillText ctx (fmt-fn (:min hist)) 2 (+ plot-h 3))
    (set! (.-textAlign ctx) "right")
    (.fillText ctx (fmt-fn (:max hist)) (- w 2) (+ plot-h 3))
    (set! (.-strokeStyle ctx) (hex->rgba (:border palette) 0.8))
    (set! (.-lineWidth ctx) 1)
    (.beginPath ctx)
    (.moveTo ctx 0 (+ plot-h 0.5))
    (.lineTo ctx w (+ plot-h 0.5))
    (.stroke ctx)))

;; ---------------------------------------------------------------------------
;; Replicant lifecycle wiring
;; ---------------------------------------------------------------------------

(defn- start-animation!
  [canvas draw-fn holder]
  (when-let [raf (:raf @holder)]
    (js/cancelAnimationFrame raf))
  (let [dur 700
        t0 (.now js/performance)
        spec (:spec @holder)]
    (letfn [(tick [now]
              (let [p (min 1 (/ (- now t0) dur))
                    eased (- 1 (js/Math.pow (- 1 p) 3))]
                (draw-fn canvas (assoc spec :progress eased))
                (if (< p 1)
                  (swap! holder assoc :raf (js/requestAnimationFrame tick))
                  (swap! holder assoc :raf nil))))]
      (swap! holder assoc :raf (js/requestAnimationFrame tick)))))

(defn- draw-final!
  [canvas draw-fn holder]
  (draw-fn canvas (assoc (:spec @holder) :progress 1)))

(defn- render-hook
  "Build a `:replicant/on-render` handler that owns a `<canvas>` child of the
  host node and draws `spec` with `draw-fn`. When `spec` carries `:animate?`,
  a fresh `:update-key` triggers a one-shot reveal sweep; otherwise it draws the
  final frame."
  [draw-fn spec]
  (fn [{:keys [replicant/life-cycle replicant/node replicant/memory replicant/remember]}]
    (case life-cycle
      :replicant.life-cycle/mount
      (let [canvas (.createElement js/document "canvas")
            holder (atom {:spec spec})
            on-resize (fn [] (draw-final! canvas draw-fn holder))]
        (set! (.. canvas -style -display) "block")
        (set! (.. canvas -style -width) "100%")
        (.appendChild node canvas)
        (.addEventListener js/window "resize" on-resize)
        (if (:animate? spec)
          (start-animation! canvas draw-fn holder)
          (draw-final! canvas draw-fn holder))
        (remember {:canvas canvas :holder holder :on-resize on-resize}))

      :replicant.life-cycle/update
      (let [{:keys [canvas holder]} memory]
        (when (and canvas holder)
          (let [prev-key (:update-key (:spec @holder))]
            (swap! holder assoc :spec spec)
            (when (not= prev-key (:update-key spec))
              (if (:animate? spec)
                (start-animation! canvas draw-fn holder)
                (draw-final! canvas draw-fn holder)))))
        (remember memory))

      :replicant.life-cycle/unmount
      (let [{:keys [holder on-resize]} memory]
        (when (and holder (:raf @holder))
          (js/cancelAnimationFrame (:raf @holder)))
        (when on-resize
          (.removeEventListener js/window "resize" on-resize)))

      nil)))

(defn spaghetti-on-render
  "`:replicant/on-render` handler for the equity-paths chart."
  [spec]
  (render-hook draw-spaghetti! (assoc spec :animate? true)))

(defn histogram-on-render
  "`:replicant/on-render` handler for a distribution histogram."
  [spec]
  (render-hook draw-histogram! spec))
