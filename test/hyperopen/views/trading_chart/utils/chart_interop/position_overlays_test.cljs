(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlays-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trading-chart.test-support.fake-dom :as fake-dom]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlays :as position-overlays]))

(defn- find-liquidation-drag-handle
  [root]
  (fake-dom/find-dom-node root
                          (fn [node]
                            (= "true" (aget node "data-position-liq-drag-handle")))))

(defn- find-liquidation-drag-hit
  [root]
  (fake-dom/find-dom-node root
                          (fn [node]
                            (= "true" (aget node "data-position-liq-drag-hit")))))

(defn- emit-overlay-repaint!
  [{:keys [subscription-callbacks*]}]
  (when-let [callback (or (get @subscription-callbacks* :size-change)
                          (get @subscription-callbacks* :data-changed)
                          (get @subscription-callbacks* :visible-time-range)
                          (get @subscription-callbacks* :visible-logical-range))]
    (callback nil)))

(defn- build-chart-fixture
  [{:keys [width price-to-y time-to-x y-to-price]
    :or {width 320
         price-to-y (fn [price] (- 220 price))
         y-to-price (fn [y] (- 220 y))
         time-to-x (fn [time]
                     (case time
                       1700000000 48
                       1700003600 228
                       nil))}}]
  (let [subscription-callbacks* (atom {})
        subscribe-fn (fn [k]
                       (fn [callback]
                         (swap! subscription-callbacks* assoc k callback)))
        unsubscribe-calls* (atom 0)
        unsubscribe-fn (fn [k]
                         (fn [callback]
                           (swap! unsubscribe-calls* inc)
                           (swap! subscription-callbacks*
                                  (fn [callbacks]
                                    (if (identical? callback (get callbacks k))
                                      (dissoc callbacks k)
                                      callbacks)))))
        width* (atom width)
        time-scale #js {:subscribeVisibleTimeRangeChange (subscribe-fn :visible-time-range)
                        :unsubscribeVisibleTimeRangeChange (unsubscribe-fn :visible-time-range)
                        :subscribeVisibleLogicalRangeChange (subscribe-fn :visible-logical-range)
                        :unsubscribeVisibleLogicalRangeChange (unsubscribe-fn :visible-logical-range)
                        :subscribeSizeChange (subscribe-fn :size-change)
                        :unsubscribeSizeChange (unsubscribe-fn :size-change)
                        :timeToCoordinate time-to-x}
        main-series #js {:priceToCoordinate price-to-y
                         :coordinateToPrice y-to-price
                         :subscribeDataChanged (subscribe-fn :data-changed)
                         :unsubscribeDataChanged (unsubscribe-fn :data-changed)}
        chart #js {:timeScale (fn [] time-scale)
                   :paneSize (fn [_pane-index]
                               #js {:width @width*})}
        chart-obj #js {:chart chart
                       :mainSeries main-series}
        document (fake-dom/make-fake-document)
        container (fake-dom/make-fake-element "div")
        window-target (fake-dom/make-fake-element "window")]
    (set! (.-clientWidth container) width)
    (set! (.-clientHeight container) 240)
    {:chart-obj chart-obj
     :document document
     :container container
     :chart chart
     :main-series main-series
     :subscription-callbacks* subscription-callbacks*
     :window-target window-target
     :width* width*
     :unsubscribe-calls* unsubscribe-calls*}))

(deftest position-overlays-render-and-clear-runtime-lifecycle-test
  (let [{:keys [chart-obj document container unsubscribe-calls*]}
        (build-chart-fixture {})
        overlay {:side :short
                 :entry-price 100
                 :unrealized-pnl -12.4
                 :abs-size 1.25
                 :liquidation-price 130
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}]
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document
      :format-price (fn [price _raw] (str price))
      :format-size (fn [size] (str size))})
    (is (= 1 (alength (.-children container))))
    (position-overlays/clear-position-overlays! chart-obj)
    (is (= 0 (alength (.-children container))))
    (is (= 4 @unsubscribe-calls*))))

(deftest position-overlays-sync-skips-rerender-for-identical-overlay-reference-test
  (let [price-to-coordinate-calls* (atom 0)
        {:keys [chart-obj document container]}
        (build-chart-fixture {:price-to-y (fn [price]
                                            (swap! price-to-coordinate-calls* inc)
                                            (- 220 price))})
        overlay {:side :long
                 :entry-price 101
                 :unrealized-pnl 5.5
                 :abs-size 2
                 :liquidation-price 80
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}
        format-price (fn [price _raw] (str price))
        format-size (fn [size] (str size))]
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document
      :format-price format-price
      :format-size format-size})
    (is (= 2 @price-to-coordinate-calls*))
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document
      :format-price format-price
      :format-size format-size})
    (is (= 2 @price-to-coordinate-calls*))
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     (assoc overlay :unrealized-pnl 8.0)
     {:document document
      :format-price format-price
      :format-size format-size})
    (is (= 4 @price-to-coordinate-calls*))
    (position-overlays/clear-position-overlays! chart-obj)))

(deftest position-overlays-coalesces-subscription-repaints-per-frame-test
  (let [{:keys [chart-obj document container subscription-callbacks*]}
        (build-chart-fixture {})
        overlay {:side :short
                 :entry-price 100
                 :unrealized-pnl -2.5
                 :abs-size 1.5
                 :liquidation-price 130
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}
        render-calls* (atom 0)
        next-frame-id* (atom 0)
        scheduled-frame* (atom nil)]
    (with-redefs [position-overlays/render-overlays! (fn [_]
                                                       (swap! render-calls* inc))
                  position-overlays/*schedule-overlay-repaint-frame!* (fn [callback]
                                                                        (let [frame-id (swap! next-frame-id* inc)
                                                                              wrapped (fn []
                                                                                        (reset! scheduled-frame* nil)
                                                                                        (callback))]
                                                                          (reset! scheduled-frame* {:id frame-id
                                                                                                    :callback wrapped})
                                                                          frame-id))
                  position-overlays/*cancel-overlay-repaint-frame!* (fn [frame-id]
                                                                      (when (= frame-id (:id @scheduled-frame*))
                                                                        (reset! scheduled-frame* nil)))]
      (position-overlays/sync-position-overlays!
       chart-obj
       container
       overlay
       {:document document
        :format-price (fn [price _raw]
                        (str price))
        :format-size (fn [size]
                       (str size))})
      (is (= 1 @render-calls*))
      ((get @subscription-callbacks* :visible-time-range) nil)
      ((get @subscription-callbacks* :size-change) nil)
      ((get @subscription-callbacks* :data-changed) nil)
      (is (= 1 (:id @scheduled-frame*)))
      (is (= 1 @render-calls*))
      ((:callback @scheduled-frame*))
      (is (= 2 @render-calls*))
      (is (nil? @scheduled-frame*))
      (position-overlays/clear-position-overlays! chart-obj))))

(deftest position-overlays-liquidation-drag-emits-live-preview-suggestion-on-move-test
  (let [preview-calls* (atom [])
        {:keys [chart-obj document container window-target]}
        (build-chart-fixture {})
        overlay {:side :long
                 :entry-price 100
                 :unrealized-pnl 2.0
                 :abs-size 2
                 :liquidation-price 100
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}]
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document
      :window window-target
      :on-liquidation-drag-preview (fn [payload]
                                     (swap! preview-calls* conj payload))})
    (let [overlay-root (aget (.-children container) 0)
          drag-hit (find-liquidation-drag-hit overlay-root)]
      (is (some? drag-hit))
      (fake-dom/dispatch-dom-event-with-payload! drag-hit
                                                 "pointerdown"
                                                 #js {:clientX 64
                                                      :clientY 120})
      (fake-dom/dispatch-dom-event-with-payload! window-target
                                                 "pointermove"
                                                 #js {:clientX 64
                                                      :clientY 125}))
    (let [payload (first @preview-calls*)]
      (is (= :add (:mode payload)))
      (is (= 10 (:amount payload)))
      (is (= 100 (:current-liquidation-price payload)))
      (is (= 95 (:target-liquidation-price payload)))
      (is (map? (:anchor payload))))
    (position-overlays/clear-position-overlays! chart-obj)))

(deftest position-overlays-liquidation-drag-emits-margin-confirmation-suggestion-test
  (let [confirm-calls* (atom [])
        {:keys [chart-obj document container window-target]}
        (build-chart-fixture {})
        overlay {:side :long
                 :entry-price 100
                 :unrealized-pnl 2.0
                 :abs-size 2
                 :liquidation-price 100
                 :entry-time 1700000000
                 :entry-time-ms 1700000000000
                 :latest-time 1700003600}]
    (position-overlays/sync-position-overlays!
     chart-obj
     container
     overlay
     {:document document
      :window window-target
      :on-liquidation-drag-confirm (fn [payload]
                                     (swap! confirm-calls* conj payload))})
    (let [overlay-root (aget (.-children container) 0)
          drag-handle (find-liquidation-drag-handle overlay-root)
          drag-hit (find-liquidation-drag-hit overlay-root)]
      (is (some? drag-handle))
      (is (some? drag-hit))
      (fake-dom/dispatch-dom-event-with-payload! drag-hit
                                                 "pointerdown"
                                                 #js {:clientX 64
                                                      :clientY 120})
      (fake-dom/dispatch-dom-event-with-payload! window-target
                                                 "pointermove"
                                                 #js {:clientX 64
                                                      :clientY 125})
      (fake-dom/dispatch-dom-event-with-payload! window-target
                                                 "pointerup"
                                                 #js {:clientX 64
                                                      :clientY 125}))
    (let [payload (first @confirm-calls*)]
      (is (= :add (:mode payload)))
      (is (= 10 (:amount payload)))
      (is (= 100 (:current-liquidation-price payload)))
      (is (= 95 (:target-liquidation-price payload)))
      (is (map? (:anchor payload))))
    (position-overlays/clear-position-overlays! chart-obj)))
