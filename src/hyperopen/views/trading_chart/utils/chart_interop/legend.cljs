(ns hyperopen.views.trading-chart.utils.chart-interop.legend
  (:require [hyperopen.utils.formatting :as fmt]
            [hyperopen.views.trading-chart.utils.chart-options :as chart-options]))

(defn- normalize-time-key
  [value]
  (let [business-day (cond
                       (and (map? value)
                            (number? (:year value))
                            (number? (:month value))
                            (number? (:day value)))
                       value

                       (and value
                            (not (number? value))
                            (not (string? value))
                            (not (keyword? value)))
                       (let [converted (js->clj value :keywordize-keys true)]
                         (when (and (map? converted)
                                    (number? (:year converted))
                                    (number? (:month converted))
                                    (number? (:day converted)))
                           converted))

                       :else
                       nil)]
    (cond
      (number? value) (str "ts:" value)
      (string? value) (str "txt:" value)
      business-day (str "bd:" (:year business-day) "-" (:month business-day) "-" (:day business-day))
      :else nil)))

(defn- build-legend-state
  [legend-meta]
  (let [symbol (or (:symbol legend-meta) "—")
        timeframe-label (or (:timeframe-label legend-meta) "—")
        venue (or (:venue legend-meta) "—")
        header-text (str symbol " · " timeframe-label " · " venue)
        market-open? (not (false? (:market-open? legend-meta)))
        candle-data (or (:candle-data legend-meta) [])
        candle-lookup (when (seq candle-data)
                        (loop [remaining candle-data
                               prev-close nil
                               acc {}]
                          (if (empty? remaining)
                            acc
                            (let [c (first remaining)
                                  key* (normalize-time-key (:time c))
                                  acc* (if key*
                                         (assoc acc key* {:candle c :prev-close prev-close})
                                         acc)
                                  prev-close* (:close c)]
                              (recur (rest remaining) prev-close* acc*)))))
        latest-candle (last candle-data)
        latest-prev-close (when (> (count candle-data) 1)
                            (:close (nth candle-data (- (count candle-data) 2))))
        latest-entry (when latest-candle
                       {:candle latest-candle
                        :prev-close latest-prev-close})]
    {:header-text header-text
     :market-open? market-open?
     :candle-lookup candle-lookup
     :latest-entry latest-entry}))

(defn- create-value-node!
  [document row label]
  (let [pair-node (let [doc document]
                    (.createElement doc "span"))
        label-node (let [doc document]
                     (.createElement doc "span"))
        value-node (let [doc document]
                     (.createElement doc "span"))]
    (set! (.-cssText (.-style pair-node)) "display:inline-flex;align-items:center;gap:2px;color:#9ca3af;")
    (set! (.-textContent label-node) label)
    (set! (.-cssText (.-style label-node)) "opacity:0.78;")
    (set! (.-textContent value-node) "--")
    (.appendChild pair-node label-node)
    (.appendChild pair-node value-node)
    (.appendChild row pair-node)
    {:pair-node pair-node
     :value-node value-node}))

(defn create-legend!
  "Create legend element that adapts to different chart types."
  ([container chart legend-meta]
   (create-legend! container chart legend-meta {}))
  ([container chart legend-meta {:keys [document
                                        format-price
                                        format-delta
                                        format-pct]}]
   (let [global-document (aget js/globalThis "document")
         document* (or document global-document)]
     (when-not document*
       (throw (js/Error. "Legend rendering requires a DOM document.")))
     (let [container-style (.-style container)]
       (when (or (not (.-position container-style))
                 (= (.-position container-style) "static"))
         (set! (.-position container-style) "relative")))
     (let [legend (let [doc document*]
                    (.createElement doc "div"))
           legend-font-family (chart-options/resolve-chart-font-family)
           header-row (let [doc document*]
                        (.createElement doc "div"))
           header-text-node (let [doc document*]
                              (.createElement doc "span"))
           market-status-node (let [doc document*]
                                (.createElement doc "span"))
           values-row (let [doc document*]
                        (.createElement doc "div"))
           open-value (create-value-node! document* values-row "O")
           open-node (:value-node open-value)
           open-pair-node (:pair-node open-value)
           high-value (create-value-node! document* values-row "H")
           high-node (:value-node high-value)
           high-pair-node (:pair-node high-value)
           low-value (create-value-node! document* values-row "L")
           low-node (:value-node low-value)
           low-pair-node (:pair-node low-value)
           close-value (create-value-node! document* values-row "C")
           close-node (:value-node close-value)
           close-pair-node (:pair-node close-value)
           delta-node (let [doc document*]
                        (.createElement doc "span"))]
       (set! (.-cssText (.-style legend))
             (str "position:absolute;left:12px;top:8px;z-index:100;"
                  "font-size:12px;font-family:" legend-font-family ";"
                  "font-variant-numeric:tabular-nums lining-nums;"
                  "font-feature-settings:'tnum' 1,'lnum' 1;"
                  "line-height:1.2;font-weight:500;color:#ffffff;"
                  "padding:4px 0;display:flex;align-items:center;gap:10px;"
                  "max-width:calc(100% - 24px);white-space:nowrap;pointer-events:none;overflow:hidden;"))
       (set! (.-cssText (.-style header-row))
             "display:flex;align-items:center;gap:8px;font-weight:600;min-width:0;flex:0 1 auto;")
       (set! (.-cssText (.-style header-text-node))
             "color:#d1d5db;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;")
        (set! (.-cssText (.-style market-status-node))
             "display:inline-block;width:8px;height:8px;border-radius:9999px;flex:0 0 auto;")
        (set! (.-backgroundColor (.-style market-status-node)) "#00c278")
       (set! (.-boxShadow (.-style market-status-node))
             "0 0 0 4px rgba(0,194,120,0.16), 0 0 12px rgba(0,194,120,0.28)")
       (.setAttribute market-status-node "data-role" "chart-market-status")
       (.setAttribute market-status-node "role" "img")
       (.setAttribute market-status-node "aria-label" "Market open")
       (.setAttribute market-status-node "title" "Market open")
       (.appendChild header-row header-text-node)
       (.appendChild header-row market-status-node)
       (set! (.-cssText (.-style values-row))
             "display:flex;align-items:center;gap:8px;flex:0 0 auto;")
       (set! (.-cssText (.-style delta-node)) "color:#9ca3af;font-weight:600;flex:0 0 auto;")
       (.appendChild values-row delta-node)
       (.appendChild legend header-row)
       (.appendChild legend values-row)
       (.appendChild container legend)
       (let [state (atom (build-legend-state legend-meta))
             format-price* (or format-price
                               (fn [price]
                                 (when (number? price)
                                   (fmt/format-trade-price-plain price))))
             format-delta* (or format-delta
                               (fn [delta]
                                 (when (number? delta)
                                   (let [formatted (fmt/format-trade-price-delta delta)]
                                     (if (>= delta 0) (str "+" formatted) formatted)))))
             format-pct* (or format-pct
                             (fn [pct]
                               (when (number? pct)
                                 (let [formatted (.toFixed pct 2)]
                                   (if (>= pct 0) (str "+" formatted "%") (str formatted "%"))))))
             render-legend! (fn [entry]
                              (let [{:keys [header-text market-open?]} @state
                                    session-label (if market-open? "Market open" "Market closed")
                                    session-color (if market-open? "#00c278" "#6b7280")
                                    metric-color (fn [color]
                                                   (set! (.-color (.-style open-pair-node)) color)
                                                   (set! (.-color (.-style high-pair-node)) color)
                                                   (set! (.-color (.-style low-pair-node)) color)
                                                   (set! (.-color (.-style close-pair-node)) color))]
                                (set! (.-textContent header-text-node) header-text)
                                (set! (.-backgroundColor (.-style market-status-node)) session-color)
                                (set! (.-boxShadow (.-style market-status-node))
                                      (if market-open?
                                        "0 0 0 4px rgba(0,194,120,0.16), 0 0 12px rgba(0,194,120,0.28)"
                                        "0 0 0 4px rgba(107,114,128,0.12), 0 0 10px rgba(107,114,128,0.18)"))
                                (.setAttribute market-status-node "aria-label" session-label)
                                (.setAttribute market-status-node "title" session-label)
                                (if (and entry (:candle entry))
                                  (let [c (:candle entry)
                                        baseline (or (:prev-close entry) (:open c))
                                        close (:close c)
                                        delta (when (and close baseline) (- close baseline))
                                        pct (when (and delta baseline (not= baseline 0)) (* 100 (/ delta baseline)))
                                        delta-color (cond
                                                      (nil? delta) "#9ca3af"
                                                      (>= delta 0) "#10b981"
                                                      :else "#ef4444")]
                                    (set! (.-textContent open-node) (or (format-price* (:open c)) "--"))
                                    (set! (.-textContent high-node) (or (format-price* (:high c)) "--"))
                                    (set! (.-textContent low-node) (or (format-price* (:low c)) "--"))
                                    (set! (.-textContent close-node) (or (format-price* (:close c)) "--"))
                                    (metric-color delta-color)
                                    (set! (.-textContent delta-node)
                                          (str (or (format-delta* delta) "--")
                                               " ("
                                               (or (format-pct* pct) "--")
                                               ")"))
                                    (set! (.-color (.-style delta-node)) delta-color))
                                  (do
                                    (set! (.-textContent open-node) "--")
                                    (set! (.-textContent high-node) "--")
                                    (set! (.-textContent low-node) "--")
                                    (set! (.-textContent close-node) "--")
                                    (metric-color "#9ca3af")
                                    (set! (.-textContent delta-node) "-- (--)")
                                    (set! (.-color (.-style delta-node)) "#9ca3af")))))
             update-legend (fn [param]
                             (let [{:keys [candle-lookup latest-entry]} @state
                                   lookup-key (when (and param (some? (.-time param)))
                                                (normalize-time-key (.-time param)))
                                   entry (when lookup-key
                                           (get candle-lookup lookup-key))]
                               (render-legend! (or entry latest-entry))))
             update! (fn [new-meta]
                       (reset! state (build-legend-state new-meta))
                       (update-legend nil))
             destroy! (fn []
                        (try
                          (.unsubscribeCrosshairMove ^js chart update-legend)
                          (catch :default _ nil))
                        (when (.-parentNode legend)
                          (.removeChild (.-parentNode legend) legend)))]
         (.subscribeCrosshairMove ^js chart update-legend)
         (update-legend nil)
         #js {:update update! :destroy destroy!})))))
