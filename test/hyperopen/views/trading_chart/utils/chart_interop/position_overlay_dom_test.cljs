(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlay-dom-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trading-chart.test-support.fake-dom :as fake-dom]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlay-dom :as overlay-dom]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlay-layout :as layout]))

(defn- find-inline-badge
  [root text-fragment]
  (fake-dom/find-dom-node root
                          (fn [node]
                            (let [style (.-style node)
                                  display (when style (aget style "display"))
                                  left (when style (aget style "left"))
                                  text (str/join " " (fake-dom/collect-text-content node))]
                              (and (= "inline-flex" display)
                                   (string? left)
                                   (str/includes? text text-fragment))))))

(defn- find-pnl-segment-line
  [root]
  (fake-dom/find-dom-node root
                          (fn [node]
                            (let [style (.-style node)
                                  border-top (when style (aget style "borderTop"))]
                              (and (string? border-top)
                                   (str/includes? border-top "1px dashed"))))))

(defn- find-pnl-price-chip
  [root]
  (fake-dom/find-dom-node root
                          (fn [node]
                            (= "true" (aget node "data-position-pnl-price-chip")))))

(defn- find-liquidation-price-chip
  [root]
  (fake-dom/find-dom-node root
                          (fn [node]
                            (= "true" (aget node "data-position-liq-price-chip")))))

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

(defn- first-text-node
  [node]
  (first (fake-dom/collect-text-nodes node)))

(defn- text-node-text
  [text-node]
  (or (some-> text-node .-data)
      (some-> text-node .-nodeValue)
      ""))

(defn- sample-overlay
  ([] (sample-overlay {}))
  ([overrides]
   (merge {:format-price (fn [price _raw] (str price))
           :format-size (fn [size] (str size))
           :side :short
           :entry-price 100
           :unrealized-pnl -2.5
           :abs-size 1.5
           :current-liquidation-price 100
           :liquidation-price 95}
          overrides)))

(defn- build-dom-fixture
  []
  (let [drag-events* (atom [])
        document (fake-dom/make-fake-document)
        container (fake-dom/make-fake-element "div")
        root (overlay-dom/ensure-overlay-root! nil container document)
        overlay-dom* (overlay-dom/ensure-overlay-dom! nil
                                                      document
                                                      root
                                                      (fn [source-node event]
                                                        (swap! drag-events* conj {:source-node source-node
                                                                                  :event event})))]
    {:document document
     :container container
     :root root
     :overlay-dom overlay-dom*
     :drag-events* drag-events*}))

(deftest position-overlay-dom-reuses-mounted-root-and-remounts-detached-root-test
  (let [document (fake-dom/make-fake-document)
        container-a (fake-dom/make-fake-element "div")
        container-b (fake-dom/make-fake-element "div")
        root (overlay-dom/ensure-overlay-root! nil container-a document)]
    (is (identical? root (overlay-dom/ensure-overlay-root! root container-a document)))
    (is (identical? root (.-firstChild container-a)))
    (let [remounted-root (overlay-dom/ensure-overlay-root! root container-b document)]
      (is (not (identical? root remounted-root)))
      (is (nil? (.-firstChild container-a)))
      (is (identical? remounted-root (.-firstChild container-b))))))

(deftest position-overlay-dom-patches-retained-row-nodes-in-place-test
  (let [{:keys [root overlay-dom]} (build-dom-fixture)
        overlay-a (sample-overlay)
        overlay-b (sample-overlay {:entry-price 105
                                   :unrealized-pnl 8.0
                                   :abs-size 3.0
                                   :liquidation-price 97.5})
        pnl-props-a (layout/pnl-row-props overlay-a 120 320)
        pnl-props-b (layout/pnl-row-props overlay-b 110 320)
        liq-props-a (layout/liquidation-row-props overlay-a 150 320)
        liq-props-b (layout/liquidation-row-props overlay-b 130 320)]
    (overlay-dom/apply-pnl-row! (:pnl overlay-dom) pnl-props-a)
    (overlay-dom/apply-liquidation-row! (:liquidation overlay-dom) liq-props-a)
    (let [pnl-chip-before (find-pnl-price-chip root)
          liq-chip-before (find-liquidation-price-chip root)
          pnl-line-before (find-pnl-segment-line root)]
      (overlay-dom/apply-pnl-row! (:pnl overlay-dom) pnl-props-b)
      (overlay-dom/apply-liquidation-row! (:liquidation overlay-dom) liq-props-b)
      (let [text (str/join " " (fake-dom/collect-text-content root))
            pnl-chip-after (find-pnl-price-chip root)
            liq-chip-after (find-liquidation-price-chip root)
            pnl-line-after (find-pnl-segment-line root)
            border-top (some-> pnl-line-after .-style (aget "borderTop"))]
        (is (identical? pnl-chip-before pnl-chip-after))
        (is (identical? liq-chip-before liq-chip-after))
        (is (identical? pnl-line-before pnl-line-after))
        (is (str/includes? text "PNL +$8.00 | 3"))
        (is (str/includes? text "Remove $7.50 Margin"))
        (is (str/includes? border-top "34, 201, 151"))))))

(deftest position-overlay-dom-retains-text-nodes-while-patching-text-test
  (let [{:keys [root overlay-dom]} (build-dom-fixture)
        overlay-a (sample-overlay)
        overlay-b (sample-overlay {:entry-price 105
                                   :unrealized-pnl 8.0
                                   :abs-size 3.0
                                   :liquidation-price 97.5})
        pnl-props-a (layout/pnl-row-props overlay-a 120 320)
        pnl-props-b (layout/pnl-row-props overlay-b 110 320)
        liq-props-a (layout/liquidation-row-props overlay-a 150 320)
        liq-props-b (layout/liquidation-row-props overlay-b 130 320)]
    (overlay-dom/apply-pnl-row! (:pnl overlay-dom) pnl-props-a)
    (overlay-dom/apply-liquidation-row! (:liquidation overlay-dom) liq-props-a)
    (let [pnl-badge-before (find-inline-badge root "PNL -$2.50 | 1.5")
          pnl-chip-before (find-pnl-price-chip root)
          liq-badge-before (find-inline-badge root "Liq. Price")
          liq-chip-before (find-liquidation-price-chip root)
          pnl-badge-text-node-before (first-text-node pnl-badge-before)
          pnl-chip-text-node-before (first-text-node pnl-chip-before)
          [liq-label-text-node-before
           liq-price-text-node-before
           liq-drag-note-text-node-before] (vec (fake-dom/collect-text-nodes liq-badge-before))
          liq-chip-text-node-before (first-text-node liq-chip-before)]
      (overlay-dom/apply-pnl-row! (:pnl overlay-dom) pnl-props-b)
      (overlay-dom/apply-liquidation-row! (:liquidation overlay-dom) liq-props-b)
      (let [pnl-badge-after (find-inline-badge root "PNL +$8.00 | 3")
            pnl-chip-after (find-pnl-price-chip root)
            liq-badge-after (find-inline-badge root "Liq. Price")
            liq-chip-after (find-liquidation-price-chip root)
            pnl-badge-text-node-after (first-text-node pnl-badge-after)
            pnl-chip-text-node-after (first-text-node pnl-chip-after)
            [liq-label-text-node-after
             liq-price-text-node-after
             liq-drag-note-text-node-after] (vec (fake-dom/collect-text-nodes liq-badge-after))
            liq-chip-text-node-after (first-text-node liq-chip-after)]
        (is (identical? pnl-badge-text-node-before pnl-badge-text-node-after))
        (is (identical? pnl-chip-text-node-before pnl-chip-text-node-after))
        (is (identical? liq-label-text-node-before liq-label-text-node-after))
        (is (identical? liq-price-text-node-before liq-price-text-node-after))
        (is (identical? liq-drag-note-text-node-before liq-drag-note-text-node-after))
        (is (identical? liq-chip-text-node-before liq-chip-text-node-after))
        (is (= "PNL +$8.00 | 3" (text-node-text pnl-badge-text-node-after)))
        (is (= "105" (text-node-text pnl-chip-text-node-after)))
        (is (= "Liq. Price" (text-node-text liq-label-text-node-after)))
        (is (= "$97.5" (text-node-text liq-price-text-node-after)))
        (is (= "Remove $7.50 Margin" (text-node-text liq-drag-note-text-node-after)))
        (is (= "97.5" (text-node-text liq-chip-text-node-after))))))

(deftest position-overlay-dom-hide-helpers-clear-display-and-text-test
  (let [{:keys [root overlay-dom]} (build-dom-fixture)
        overlay (sample-overlay)]
    (overlay-dom/apply-pnl-row! (:pnl overlay-dom) (layout/pnl-row-props overlay 120 320))
    (overlay-dom/apply-liquidation-row! (:liquidation overlay-dom)
                                        (layout/liquidation-row-props overlay 150 320))
    (overlay-dom/hide-pnl-row! (:pnl overlay-dom))
    (overlay-dom/hide-liquidation-row! (:liquidation overlay-dom))
    (let [pnl-row (get-in overlay-dom [:pnl :row])
          liq-row (get-in overlay-dom [:liquidation :row])]
      (is (= "none" (some-> pnl-row .-style (aget "display"))))
      (is (= "none" (some-> liq-row .-style (aget "display"))))
      (is (empty? (fake-dom/collect-text-content root))))))

(deftest position-overlay-dom-liquidation-drag-handlers-forward-source-node-test
  (let [{:keys [root drag-events*]} (build-dom-fixture)
        drag-hit (find-liquidation-drag-hit root)
        drag-handle (find-liquidation-drag-handle root)]
    (is (some? drag-hit))
    (is (some? drag-handle))
    (fake-dom/dispatch-dom-event-with-payload! drag-hit
                                               "pointerdown"
                                               #js {:clientX 60
                                                    :clientY 90})
    (fake-dom/dispatch-dom-event-with-payload! drag-handle
                                               "pointerdown"
                                               #js {:clientX 70
                                                    :clientY 100})
    (is (= 2 (count @drag-events*)))
    (is (identical? drag-hit (:source-node (first @drag-events*))))
    (is (identical? drag-handle (:source-node (second @drag-events*)))))))
