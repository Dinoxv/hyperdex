(ns hyperopen.views.trading-chart.utils.chart-interop.position-overlay-layout-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.trading-chart.test-support.fake-dom :as fake-dom]
            [hyperopen.views.trading-chart.utils.chart-interop.position-overlay-layout :as layout]))

(defn- parse-px
  [value]
  (when (string? value)
    (let [num (js/parseFloat value)]
      (when-not (js/isNaN num)
        num))))

(deftest pnl-row-props-keep-badge-inside-readable-zone-test
  (let [props (layout/pnl-row-props {:format-price (fn [price _raw] (str price))
                                     :format-size (fn [size] (str size))
                                     :entry-price 100
                                     :unrealized-pnl -3.6
                                     :abs-size 2}
                                    120
                                    320)
        badge-left (some-> props :badge-style (get "left") parse-px)]
    (is (= "100" (:chip-text props)))
    (is (number? badge-left))
    (is (<= 90 badge-left 145))))

(deftest pnl-row-props-color-follows-pnl-sign-not-position-side-test
  (doseq [{:keys [label overlay expected-color]}
          [{:label "positive short pnl uses profit tone"
            :overlay {:format-price (fn [price _raw] (str price))
                      :format-size (fn [size] (str size))
                      :entry-price 100
                      :unrealized-pnl 3.4
                      :abs-size 1
                      :side :short}
            :expected-color "34, 201, 151"}
           {:label "negative long pnl uses loss tone"
            :overlay {:format-price (fn [price _raw] (str price))
                      :format-size (fn [size] (str size))
                      :entry-price 100
                      :unrealized-pnl -4.1
                      :abs-size 1
                      :side :long}
            :expected-color "227, 95, 120"}]]
    (testing label
      (let [props (layout/pnl-row-props overlay 120 320)
            border-top (get-in props [:line-style "borderTop"])
            chip-bg (get-in props [:chip-style "background"])]
        (is (string? border-top))
        (is (string? chip-bg))
        (is (.includes border-top expected-color))
        (is (.includes chip-bg expected-color))))))

(deftest liquidation-drag-suggestion-preserves-add-remove-and-threshold-rules-test
  (let [overlay {:side :long
                 :abs-size 2}]
    (is (= {:mode :add
            :amount 10
            :current-liquidation-price 100
            :target-liquidation-price 95}
           (layout/liquidation-drag-suggestion overlay 100 95)))
    (is (= {:mode :remove
            :amount 10
            :current-liquidation-price 100
            :target-liquidation-price 105}
           (layout/liquidation-drag-suggestion overlay 100 105)))
    (is (nil? (layout/liquidation-drag-suggestion overlay 100 99.99999975)))))

(deftest liquidation-row-props-include-preview-label-and-chip-formatting-test
  (let [base-overlay {:format-price (fn [price _raw] (str price))
                      :current-liquidation-price 100
                      :liquidation-price 95
                      :abs-size 2
                      :side :long}
        with-preview (layout/liquidation-row-props base-overlay 95 320)
        without-preview (layout/liquidation-row-props (assoc base-overlay :current-liquidation-price 95)
                                                      95
                                                      320)]
    (is (= "Liq. Price" (:label-text with-preview)))
    (is (= "$95" (:price-text with-preview)))
    (is (= "95" (:chip-text with-preview)))
    (is (= "Add $10.00 Margin" (:drag-note-text with-preview)))
    (is (= "inline" (get-in with-preview [:drag-note-style "display"])))
    (is (nil? (:drag-note-text without-preview)))
    (is (= "none" (get-in without-preview [:drag-note-style "display"])))))

(deftest event-anchor-prefers-bounding-rect-and-falls-back-to-event-coordinates-test
  (let [source-node (fake-dom/make-fake-element "div")
        overlay {:window #js {:innerWidth 1440
                              :innerHeight 900}}]
    (aset source-node
          "getBoundingClientRect"
          (fn []
            #js {:left 10
                 :top 20
                 :width 30
                 :height 40}))
    (is (= {:left 10
            :right 40
            :top 20
            :bottom 60
            :width 30
            :height 40
            :viewport-width 1440
            :viewport-height 900}
           (layout/event-anchor overlay
                                source-node
                                #js {:clientX 55
                                     :clientY 75})))
    (is (= {:left 25
            :right 25
            :top 35
            :bottom 35
            :width 0
            :height 0
            :viewport-width 1280
            :viewport-height 800}
           (layout/event-anchor {}
                                #js {}
                                #js {:clientX 25
                                     :clientY 35})))))

(deftest visible-overlay-y-allows-small-buffer-around-pane-test
  (is (true? (layout/visible-overlay-y? 200 0)))
  (is (true? (layout/visible-overlay-y? 200 -10)))
  (is (true? (layout/visible-overlay-y? 200 220)))
  (is (false? (layout/visible-overlay-y? 200 -31)))
  (is (false? (layout/visible-overlay-y? 200 231))))
