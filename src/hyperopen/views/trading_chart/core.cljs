(ns hyperopen.views.trading-chart.core
  (:require [hyperopen.views.trading-chart.utils.chart-interop :as ci]
            [hyperopen.views.trading-chart.utils.data-processing :as dp]))

;; Candlestick chart component
(defn candlestick-chart-canvas [candle-data]
  (let [mount! (fn [{:keys [:replicant/life-cycle :replicant/node]}]
                 (case life-cycle
                   :replicant.life-cycle/mount
                   (try
                     ;; Create chart
                     (let [chart (ci/create-candlestick-chart! node)
                           candlestick-series (ci/add-candlestick-series! chart)]
                       (ci/set-candlestick-data! candlestick-series candle-data)
                       (ci/fit-content! chart)
                       ;; Create legend element following TradingView docs
                       (ci/create-legend! node chart candlestick-series))
                     (catch :default e
                       (js/console.error "Error in candlestick chart:" e)))
                   :replicant.life-cycle/unmount
                   nil
                   nil))]
    [:div.w-full.h-96.bg-gray-800.relative
     {:replicant/key (str "chart-" (hash candle-data))
      :replicant/on-render mount!
      :style {:width "600px" :height "400px"}}]))

(defn trading-chart-view [state]
  (let [active-asset (:active-asset state)
        candles-map (:candles state)
        ;; Default to :1d timeframe for now
        tf :1d
        api-response (get-in candles-map [active-asset tf] {})
        ;; Check for error state
        has-error? (contains? api-response :error)
        ;; Handle both possible data structures: direct array or wrapped in :data
        raw-candles (if (vector? api-response)
                      api-response  ; Direct array
                      (get api-response :data []))  ; Wrapped in :data
        candle-data (dp/process-candle-data raw-candles)]
    [:div.w-full.max-w-6xl.mx-auto.p-4
     [:h1.text-2xl.mb-4 (str "Candlestick Chart - " (or active-asset "No Asset Selected"))]
     (if has-error?
       [:div.text-red-500.p-4 "Error fetching chart data."]
       (candlestick-chart-canvas candle-data))])) 