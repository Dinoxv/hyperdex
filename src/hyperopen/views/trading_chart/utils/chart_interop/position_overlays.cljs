(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlays
  (:require [hyperopen.views.account-info.shared :as account-shared]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlay-dom :as overlay-dom]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlay-layout :as layout]))

(defonce ^:private position-overlays-sidecar (js/WeakMap.))

(declare begin-liquidation-drag!)
(declare render-overlays!)

(def ^:dynamic *schedule-overlay-repaint-frame!*
  (fn [callback]
    (if-let [raf (some-> js/globalThis (aget "requestAnimationFrame"))]
      (raf callback)
      (do
        (callback)
        nil))))

(def ^:dynamic *cancel-overlay-repaint-frame!*
  (fn [frame-id]
    (when-let [cancel-frame (and frame-id
                                 (some-> js/globalThis (aget "cancelAnimationFrame")))]
      (cancel-frame frame-id))))

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

(defn- invoke-method
  [target method-name & args]
  (let [method (when target
                 (aget target method-name))]
    (when (fn? method)
      (.apply method target (to-array args)))))

(defn- resolve-document
  [document]
  (or document
      (some-> js/globalThis .-document)))

(defn- begin-current-liquidation-drag!
  [chart-obj source-node event]
  (when event
    (.preventDefault event)
    (.stopPropagation event))
  (let [{:keys [drag rendered-overlay overlay]} (overlay-state chart-obj)
        overlay-for-drag (or rendered-overlay overlay)]
    (when (and (not drag)
               (map? overlay-for-drag))
      (begin-liquidation-drag! chart-obj
                               overlay-for-drag
                               source-node
                               event))))

(defn- cancel-scheduled-repaint!
  [chart-obj]
  (let [{:keys [repaint-frame-id] :as state} (overlay-state chart-obj)]
    (when repaint-frame-id
      (*cancel-overlay-repaint-frame!* repaint-frame-id)
      (set-overlay-state! chart-obj (dissoc state :repaint-frame-id)))))

(defn- schedule-overlay-repaint!
  [chart-obj]
  (let [{:keys [repaint-frame-id]} (overlay-state chart-obj)]
    (when-not repaint-frame-id
      (let [frame-id* (volatile! nil)
            callback (fn []
                       (let [frame-id @frame-id*
                             {:keys [repaint-frame-id] :as state} (overlay-state chart-obj)]
                         (when (or (nil? frame-id)
                                   (= frame-id repaint-frame-id))
                           (when frame-id
                             (set-overlay-state! chart-obj (dissoc state :repaint-frame-id)))
                           (render-overlays! chart-obj))))
            frame-id (*schedule-overlay-repaint-frame!* callback)]
        (vreset! frame-id* frame-id)
        (when frame-id
          (set-overlay-state! chart-obj
                              (assoc (overlay-state chart-obj) :repaint-frame-id frame-id)))))))

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
                  (schedule-overlay-repaint! chart-obj))]
    (invoke-method time-scale "subscribeVisibleTimeRangeChange" repaint)
    (invoke-method time-scale "subscribeVisibleLogicalRangeChange" repaint)
    (invoke-method time-scale "subscribeSizeChange" repaint)
    (invoke-method main-series "subscribeDataChanged" repaint)
    {:chart chart
     :main-series main-series
     :time-scale time-scale
     :repaint repaint}))

(defn- remove-event-listener!
  [target event-name handler]
  (when (and target
             (fn? (aget target "removeEventListener"))
             (fn? handler))
    (.removeEventListener target event-name handler)))

(defn- add-event-listener!
  [target event-name handler]
  (when (and target
             (fn? (aget target "addEventListener"))
             (fn? handler))
    (.addEventListener target event-name handler)))

(defn- teardown-drag-listeners!
  [state]
  (let [{:keys [target on-move on-up on-cancel]} (:drag-listeners state)]
    (remove-event-listener! target "pointermove" on-move)
    (remove-event-listener! target "pointerup" on-up)
    (remove-event-listener! target "pointercancel" on-cancel)
    (remove-event-listener! target "mousemove" on-move)
    (remove-event-listener! target "mouseup" on-up)
    (remove-event-listener! target "touchmove" on-move)
    (remove-event-listener! target "touchend" on-up)
    (remove-event-listener! target "touchcancel" on-cancel))
  (dissoc state :drag-listeners))

(defn- event-root-y
  [root event]
  (let [client-y (when event
                   (layout/parse-number (aget event "clientY")))
        rect (try
               (when-let [method (some-> root (aget "getBoundingClientRect"))]
                 (when (fn? method)
                   (.call method root)))
               (catch :default _ nil))
        top (some-> rect (aget "top") layout/parse-number)]
    (when (layout/finite-number? client-y)
      (if (layout/finite-number? top)
        (- client-y top)
        client-y))))

(defn- y->liq-price
  [main-series y]
  (when (layout/finite-number? y)
    (let [price (layout/parse-number (invoke-method main-series "coordinateToPrice" y))]
      (when (and (layout/finite-number? price)
                 (pos? price))
        price))))

(defn- liq-price-from-event
  [state event]
  (let [root (:root state)
        main-series (:main-series state)
        y (event-root-y root event)]
    (y->liq-price main-series y)))

(defn- update-liquidation-drag-preview!
  [chart-obj event]
  (let [state (overlay-state chart-obj)]
    (when (and (map? (:drag state))
               (:root state)
               (:main-series state))
      (when-let [preview-price (liq-price-from-event state event)]
        (let [state* (assoc-in state [:drag :preview-liquidation-price] preview-price)
              drag (:drag state*)
              start-price (layout/parse-number (:start-liquidation-price drag))
              overlay-for-preview (:overlay-for-confirm drag)
              source-node (:source-node drag)
              on-liquidation-drag-preview (:on-liquidation-drag-preview state*)]
          (set-overlay-state! chart-obj state*)
          (render-overlays! chart-obj)
          (when (and (layout/finite-number? start-price)
                     (layout/finite-number? preview-price)
                     (fn? on-liquidation-drag-preview)
                     (map? overlay-for-preview))
            (when-let [suggestion (layout/liquidation-drag-suggestion overlay-for-preview
                                                                      start-price
                                                                      preview-price)]
              (on-liquidation-drag-preview
               (assoc suggestion
                      :anchor (layout/event-anchor (:overlay state*)
                                                   source-node
                                                   event))))))))))

(defn- finalize-liquidation-drag!
  [chart-obj event canceled?]
  (let [state (overlay-state chart-obj)
        drag (:drag state)
        start-price (layout/parse-number (:start-liquidation-price drag))
        preview-price (or (liq-price-from-event state event)
                          (layout/parse-number (:preview-liquidation-price drag))
                          start-price)
        on-liquidation-drag-confirm (:on-liquidation-drag-confirm state)
        overlay-for-confirm (:overlay-for-confirm drag)
        source-node (:source-node drag)
        next-state (-> state
                       teardown-drag-listeners!
                       (dissoc :drag))]
    (set-overlay-state! chart-obj next-state)
    (render-overlays! chart-obj)
    (when (and (not canceled?)
               (layout/finite-number? start-price)
               (layout/finite-number? preview-price)
               (fn? on-liquidation-drag-confirm)
               (map? overlay-for-confirm))
      (when-let [suggestion (layout/liquidation-drag-suggestion overlay-for-confirm
                                                                start-price
                                                                preview-price)]
        (on-liquidation-drag-confirm
         (assoc suggestion
                :anchor (layout/event-anchor (:overlay next-state)
                                             source-node
                                             event)))))))

(defn begin-liquidation-drag!
  [chart-obj overlay-for-confirm source-node event]
  (let [state (overlay-state chart-obj)
        start-price (layout/parse-number (:liquidation-price overlay-for-confirm))
        target (or (:window state)
                   (some-> (:document state) .-defaultView)
                   (:document state)
                   js/globalThis)]
    (when (and (layout/finite-number? start-price)
               (pos? start-price)
               (map? overlay-for-confirm))
      (let [on-move (fn [drag-event]
                      (when drag-event
                        (.preventDefault drag-event))
                      (update-liquidation-drag-preview! chart-obj drag-event))
            on-up (fn [drag-event]
                    (when drag-event
                      (.preventDefault drag-event)
                      (.stopPropagation drag-event))
                    (finalize-liquidation-drag! chart-obj drag-event false))
            on-cancel (fn [drag-event]
                        (when drag-event
                          (.preventDefault drag-event))
                        (finalize-liquidation-drag! chart-obj drag-event true))
            state* (-> state
                       teardown-drag-listeners!
                       (assoc :drag {:source-node source-node
                                     :overlay-for-confirm overlay-for-confirm
                                     :start-liquidation-price start-price
                                     :preview-liquidation-price start-price}
                              :drag-listeners {:target target
                                               :on-move on-move
                                               :on-up on-up
                                               :on-cancel on-cancel}))]
        (add-event-listener! target "pointermove" on-move)
        (add-event-listener! target "pointerup" on-up)
        (add-event-listener! target "pointercancel" on-cancel)
        (add-event-listener! target "mousemove" on-move)
        (add-event-listener! target "mouseup" on-up)
        (add-event-listener! target "touchmove" on-move)
        (add-event-listener! target "touchend" on-up)
        (add-event-listener! target "touchcancel" on-cancel)
        (set-overlay-state! chart-obj state*)
        (render-overlays! chart-obj)))))

(defn render-overlays!
  [chart-obj]
  (let [state (overlay-state chart-obj)
        {:keys [root chart main-series overlay drag]} state
        document (:document overlay)
        format-price (:format-price overlay)
        format-size (:format-size overlay)]
    (when root
      (if (and (map? overlay)
               main-series
               document)
        (let [overlay-dom (overlay-dom/ensure-overlay-dom!
                           (:overlay-dom state)
                           document
                           root
                           (fn [source-node event]
                             (begin-current-liquidation-drag! chart-obj source-node event)))
              state* (assoc state :overlay-dom overlay-dom)
              entry-price (layout/parse-number (:entry-price overlay))
              entry-y (when (and (layout/finite-number? entry-price)
                                 (pos? entry-price))
                        (invoke-method main-series "priceToCoordinate" entry-price))
              base-liq-price (layout/parse-number (:liquidation-price overlay))
              drag-preview-price (some-> drag :preview-liquidation-price layout/parse-number)
              current-liq-price (or (some-> drag :start-liquidation-price layout/parse-number)
                                    (some-> overlay :current-liquidation-price layout/parse-number)
                                    base-liq-price)
              liq-price (if (layout/finite-number? drag-preview-price)
                          drag-preview-price
                          base-liq-price)
              liq-y (when (and (layout/finite-number? liq-price)
                               (pos? liq-price))
                      (invoke-method main-series "priceToCoordinate" liq-price))
              pane-size (invoke-method chart "paneSize" 0)
              pane-width (some-> pane-size (aget "width"))
              width (layout/non-negative-number pane-width
                                                (layout/non-negative-number (.-clientWidth root) 0))
              height (or (.-clientHeight root) 0)
              overlay* (assoc overlay
                              :document document
                              :format-price format-price
                              :format-size format-size
                              :current-liquidation-price current-liq-price)]
          (set-overlay-state! chart-obj (assoc state* :rendered-overlay overlay*))
          (if (layout/visible-overlay-y? height entry-y)
            (overlay-dom/apply-pnl-row! (:pnl overlay-dom)
                                        (layout/pnl-row-props overlay* entry-y width))
            (overlay-dom/hide-pnl-row! (:pnl overlay-dom)))
          (if (layout/visible-overlay-y? height liq-y)
            (overlay-dom/apply-liquidation-row! (:liquidation overlay-dom)
                                                (layout/liquidation-row-props overlay* liq-y width))
            (overlay-dom/hide-liquidation-row! (:liquidation overlay-dom))))
        (when-let [overlay-dom (:overlay-dom state)]
          (overlay-dom/hide-pnl-row! (:pnl overlay-dom))
          (overlay-dom/hide-liquidation-row! (:liquidation overlay-dom))
          (set-overlay-state! chart-obj (dissoc state :rendered-overlay)))))))

(defn clear-position-overlays!
  [chart-obj]
  (when chart-obj
    (let [{:keys [root subscription] :as state} (overlay-state chart-obj)]
      (cancel-scheduled-repaint! chart-obj)
      (teardown-subscription! subscription)
      (teardown-drag-listeners! state)
      (when root
        (overlay-dom/clear-overlay-root! root)
        (when-let [parent (.-parentNode root)]
          (.removeChild parent root)))
      (.delete position-overlays-sidecar chart-obj))))

(defn sync-position-overlays!
  ([chart-obj container overlay]
   (sync-position-overlays! chart-obj container overlay {}))
  ([chart-obj container overlay {:keys [document window format-price format-size on-liquidation-drag-preview on-liquidation-drag-confirm]
                                 :or {format-price account-shared/format-trade-price
                                      format-size account-shared/format-currency}}]
   (if-not (and chart-obj container)
     (clear-position-overlays! chart-obj)
     (let [chart (.-chart ^js chart-obj)
           main-series (.-mainSeries ^js chart-obj)
           document* (resolve-document document)
           window* (or window
                       (some-> document* .-defaultView)
                       (some-> js/globalThis .-window)
                       js/globalThis)
           overlay-ref (when (map? overlay) overlay)]
       (if (and chart main-series document* overlay-ref)
         (let [state (overlay-state chart-obj)
               root (overlay-dom/ensure-overlay-root! (:root state) container document*)
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
                    (identical? window* (:window state))
                    (identical? format-price (:format-price state))
                    (identical? format-size (:format-size state))
                    (identical? on-liquidation-drag-preview (:on-liquidation-drag-preview state))
                    (identical? on-liquidation-drag-confirm (:on-liquidation-drag-confirm state))
                    (identical? overlay-ref (:overlay-ref state)))]
           (if unchanged-inputs?
             state
             (do
               (cancel-scheduled-repaint! chart-obj)
               (set-overlay-state!
                chart-obj
                (cond-> (assoc state
                               :root root
                               :chart chart
                               :main-series main-series
                               :overlay-ref overlay-ref
                               :overlay (assoc overlay-ref
                                               :document document*
                                               :window window*
                                               :format-price format-price
                                               :format-size format-size)
                               :document document*
                               :window window*
                               :format-price format-price
                               :format-size format-size
                               :on-liquidation-drag-preview on-liquidation-drag-preview
                               :on-liquidation-drag-confirm on-liquidation-drag-confirm
                               :subscription next-subscription)
                  (not (identical? document* (get-in state [:overlay-dom :document])))
                  (dissoc :overlay-dom :rendered-overlay)))
               (render-overlays! chart-obj))))
         (clear-position-overlays! chart-obj))))))
