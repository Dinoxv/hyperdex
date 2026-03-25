(ns hyperopen.views.portfolio.chart-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.chart.d3.model :as chart-d3-model]
            [hyperopen.views.chart.renderer :as chart-renderer]
            [hyperopen.views.portfolio.chart-view :as chart-view]))

(def ^:private returns-chart
  {:chart {:selected-tab :returns
           :axis-kind :percent
           :tabs [{:value :account-value :label "Account Value"}
                  {:value :pnl :label "PNL"}
                  {:value :returns :label "Returns"}]
           :points [{:time-ms 1 :value 0.1 :x-ratio 0 :y-ratio 1}
                    {:time-ms 2 :value 0.2 :x-ratio 1 :y-ratio 0}]
           :series [{:id :strategy
                     :label "Portfolio"
                     :stroke "#f5f7f8"
                     :has-data? true
                     :path "M 0 100 L 100 0"}
                    {:id :btc-usdc
                     :coin "BTC-USDC"
                     :label "BTC-USDC (PERP)"
                     :stroke "#f7931a"
                     :has-data? true
                     :path "M 0 60 L 100 40"}]
           :y-ticks [{:value 5 :y-ratio 0}
                     {:value 0 :y-ratio 0.5}
                     {:value -5 :y-ratio 1}]
           :hover {:active? false}}
   :selectors {:summary-time-range {:value :month}
               :returns-benchmark {:coin-search "BT"
                                   :suggestions-open? true
                                   :candidates [{:value "BTC-USDC"
                                                 :label "BTC-USDC (PERP)"}]
                                   :top-coin "BTC-USDC"
                                   :selected-options [{:value "BTC-USDC"
                                                       :label "BTC-USDC (PERP)"}]}}})

(deftest chart-card-renders-returns-benchmark-controls-and-d3-host-test
  (with-redefs [chart-renderer/d3-performance-chart? (constantly true)]
    (let [view (chart-view/chart-card returns-chart)
          selector (hiccup/find-by-data-role view "portfolio-returns-benchmark-selector")
          suggestion-row (hiccup/find-by-data-role view "portfolio-returns-benchmark-suggestion-BTC-USDC")
          chip-rail (hiccup/find-by-data-role view "portfolio-returns-benchmark-chip-rail")
          chip (hiccup/find-by-data-role view "portfolio-returns-benchmark-chip-BTC-USDC")
          legend (hiccup/find-by-data-role view "portfolio-chart-legend")
          host (hiccup/find-by-data-role view "portfolio-chart-d3-host")
          chip-text (set (hiccup/collect-strings chip))]
      (is (some? selector))
      (is (some? suggestion-row))
      (is (= [[:actions/select-portfolio-returns-benchmark "BTC-USDC"]]
             (get-in suggestion-row [1 :on :mousedown])))
      (is (some? chip-rail))
      (is (contains? chip-text "BTC"))
      (is (not (contains? chip-text "BTC-USDC (PERP)")))
      (is (= "rgba(247, 147, 26, 0.58)"
             (get-in chip [1 :style :border-color])))
      (is (some? legend))
      (is (some? host))
      (is (fn? (get-in host [1 :replicant/on-render]))))))

(deftest chart-card-renders-pointer-hover-tooltip-and_benchmark_rows_test
  (with-redefs [chart-renderer/d3-performance-chart? (constantly false)
                chart-d3-model/tooltip-center-top-pct (constantly 40)]
    (let [view (chart-view/chart-card
                {:chart {:selected-tab :returns
                         :axis-kind :percent
                         :tabs [{:value :returns :label "Returns"}]
                         :points [{:time-ms 1 :value 0.1 :x-ratio 0 :y-ratio 1}
                                  {:time-ms 2 :value 0.2 :x-ratio 1 :y-ratio 0}]
                         :series [{:id :strategy
                                   :label "Portfolio"
                                   :stroke "#f5f7f8"
                                   :has-data? true
                                   :path "M 0 100 L 100 0"}
                                  {:id :spy
                                   :coin "SPY"
                                   :label "SPY (SPOT)"
                                   :stroke "#f2cf66"
                                   :has-data? true
                                   :path "M 0 60 L 100 20"}]
                         :y-ticks [{:value 5 :y-ratio 0}
                                   {:value 0 :y-ratio 0.5}
                                   {:value -5 :y-ratio 1}]
                         :hover {:active? true
                                 :point {:x-ratio 0.8}}
                         :hover-tooltip {:timestamp "2024-01-02 03:04"
                                         :metric-label "Returns"
                                         :metric-value "+12.30%"
                                         :value-classes ["text-trading-text"]
                                         :benchmark-values [{:coin "SPY"
                                                             :label "SPY (SPOT)"
                                                             :value "+2.00%"
                                                             :stroke "#f2cf66"}]}}
                 :selectors {:summary-time-range {:value :month}
                             :returns-benchmark {:selected-options [{:value "SPY"
                                                                     :label "SPY (SPOT)"}]}}})
          plot-area (hiccup/find-by-data-role view "portfolio-chart-plot-area")
          hover-line (hiccup/find-by-data-role view "portfolio-chart-hover-line")
          hover-tooltip (hiccup/find-by-data-role view "portfolio-chart-hover-tooltip")
          benchmark-row (hiccup/find-by-data-role view "portfolio-chart-hover-tooltip-benchmark-row-SPY")
          benchmark-value (hiccup/find-by-data-role view "portfolio-chart-hover-tooltip-benchmark-value-SPY")]
      (is (= [[:actions/set-portfolio-chart-hover [:event/clientX] [:event.currentTarget/bounds] 2]]
             (get-in plot-area [1 :on :mousemove])))
      (is (some? hover-line))
      (is (= "80%" (get-in hover-line [1 :style :left])))
      (is (some? hover-tooltip))
      (is (= "40%" (get-in hover-tooltip [1 :style :top])))
      (is (= "translate(calc(-100% - 8px), -50%)"
             (get-in hover-tooltip [1 :style :transform])))
      (is (contains? (set (hiccup/collect-strings hover-tooltip)) "Returns"))
      (is (contains? (set (hiccup/collect-strings hover-tooltip)) "+12.30%"))
      (is (= "SPY (SPOT)" (first (hiccup/collect-strings benchmark-row))))
      (is (= "#f2cf66" (get-in benchmark-value [1 :style :color]))))))
