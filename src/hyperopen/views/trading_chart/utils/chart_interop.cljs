(ns hyperopen.views.trading-chart.utils.chart-interop
  (:require ["lightweight-charts" :refer [createChart CandlestickSeries]]))

;; Candlestick chart implementation mirroring the JavaScript example exactly
(defn create-candlestick-chart! [container]
  "Create a chart with candlestick options"
  (let [chartOptions #js {:layout #js {:textColor "black" 
                                       :background #js {:type "solid" 
                                                       :color "white"}}}]
    (let [chart (createChart container chartOptions)]
      (js/console.log "candlestick chart created, available methods:" (js/Object.keys chart))
      chart)))

(defn add-candlestick-series! [chart]
  "Add a candlestick series to the chart"
  (let [seriesOptions #js {:upColor "#26a69a"
                           :downColor "#ef5350"
                           :borderVisible false
                           :wickUpColor "#26a69a"
                           :wickDownColor "#ef5350"}
        series (.addSeries ^js chart CandlestickSeries seriesOptions)]
    (js/console.log "candlestick series created, available methods:" (js/Object.keys series))
    series))

(defn set-candlestick-data! [series]
  "Set candlestick data exactly like the JavaScript example"
  (let [data #js [#js {:open 10 :high 10.63 :low 9.49 :close 9.55 :time 1642427876}
                  #js {:open 9.55 :high 10.30 :low 9.42 :close 9.94 :time 1642514276}
                  #js {:open 9.94 :high 10.17 :low 9.92 :close 9.78 :time 1642600676}
                  #js {:open 9.78 :high 10.59 :low 9.18 :close 9.51 :time 1642687076}
                  #js {:open 9.51 :high 10.46 :low 9.10 :close 10.17 :time 1642773476}
                  #js {:open 10.17 :high 10.96 :low 10.16 :close 10.47 :time 1642859876}
                  #js {:open 10.47 :high 11.39 :low 10.40 :close 10.81 :time 1642946276}
                  #js {:open 10.81 :high 11.60 :low 10.30 :close 10.75 :time 1643032676}
                  #js {:open 10.75 :high 11.60 :low 10.49 :close 10.93 :time 1643119076}
                  #js {:open 10.93 :high 11.53 :low 10.76 :close 10.96 :time 1643205476}]]
    (js/console.log "setting candlestick data:" data)
    (.setData series data)))

(defn fit-content! [chart]
  "Fit content to the chart viewport"
  (.fitContent ^js (.timeScale ^js chart))) 