(ns hyperopen.views.trading-chart.core
  (:require [hyperopen.views.trading-chart.utils.chart-interop :as ci]
            [hyperopen.views.trading-chart.utils.data-processing :as dp]
            [hyperopen.views.trading-chart.utils.indicators :as indicators]
            [hyperopen.views.trading-chart.timeframe-dropdown :refer [timeframe-dropdown]]
            [hyperopen.views.trading-chart.chart-type-dropdown :refer [chart-type-dropdown]]
            [hyperopen.views.trading-chart.indicators-dropdown :refer [indicators-dropdown]]))

;; Main timeframes for quick access buttons
(def main-timeframes [:5m :1h :1d])

;; Top menu component with timeframe selection and bars indicator
(defn chart-top-menu [state]
  (let [timeframes-dropdown-visible (get-in state [:chart-options :timeframes-dropdown-visible])
        selected-timeframe (get-in state [:chart-options :selected-timeframe] :1d)
        chart-type-dropdown-visible (get-in state [:chart-options :chart-type-dropdown-visible])
        selected-chart-type (get-in state [:chart-options :selected-chart-type] :candlestick)
        indicators-dropdown-visible (get-in state [:chart-options :indicators-dropdown-visible])
        active-indicators (get-in state [:chart-options :active-indicators] {})]
    [:div.flex.items-center.border-b.border-gray-700.px-4.py-2.w-full.space-x-4
     {:style {:background-color "rgb(30, 41, 55)"}}
     ;; Left side - Favorite timeframes + dropdown
     [:div.flex.items-center.space-x-1
      ;; Main timeframe buttons
      (for [key main-timeframes]
        [:button.relative.px-3.py-1.text-sm.font-medium.rounded.transition-colors
         {:key key
          :class (if (= selected-timeframe key)
                   ["text-white" "bg-blue-600"]
                   ["text-gray-300" "hover:text-white" "hover:bg-gray-700"])
          :on {:click [[:actions/select-chart-timeframe key]]}}
         (name key)])
      ;; Additional timeframe button visible only when selected timeframe is not one of the main 3
      (when-not (contains? (set main-timeframes) selected-timeframe)
        [:button.relative.px-3.py-1.text-sm.font-medium.rounded.transition-colors
         {:class ["text-white" "bg-blue-600"]
          :on {:click [[:actions/toggle-timeframes-dropdown]]}}
         (name selected-timeframe)])

      ;; Dropdown for additional timeframes
      (timeframe-dropdown {:selected-timeframe selected-timeframe
                          :timeframes-dropdown-visible timeframes-dropdown-visible})]
     
     ;; Vertical divider
     [:div.w-px.h-6.bg-gray-700]
   
     ;; Chart type and indicators section
     [:div.flex.items-center.space-x-2
      ;; Chart type dropdown
      (chart-type-dropdown {:selected-chart-type selected-chart-type
                           :chart-type-dropdown-visible chart-type-dropdown-visible})
      ;; Indicators dropdown
      [:div.relative
       [:button.flex.items-center.space-x-1.px-3.py-1.text-sm.font-medium.rounded.transition-colors
        {:class (if (seq active-indicators)
                  ["text-white" "bg-blue-600"]
                  ["text-gray-300" "hover:text-white" "hover:bg-gray-700"])
         :on {:click [[:actions/toggle-indicators-dropdown]]}}
        [:span "📈"]
        [:span (str "fx Indicators" 
                   (when (seq active-indicators) 
                     (str " (" (count active-indicators) ")")))]
        [:span.inline-block.transition-transform.duration-200.ease-in-out
         {:class (if indicators-dropdown-visible "rotate-180" "rotate-0")}
         "▼"]]
       (indicators-dropdown {:indicators-dropdown-visible indicators-dropdown-visible
                            :active-indicators active-indicators})]]]))

;; Generic chart component that supports all chart types with volume
(defn chart-canvas [candle-data chart-type active-indicators]
  (let [;; Calculate indicators data
        indicators-data (map (fn [[indicator-type config]]
                              {:type indicator-type
                               :config config
                               :data (indicators/calculate-indicator indicator-type candle-data config)})
                            active-indicators)
        mount! (fn [{:keys [:replicant/life-cycle :replicant/node]}]
                 (case life-cycle
                   :replicant.life-cycle/mount
                   (try
                     ;; Create chart with indicators support
                     (let [chart-obj (if (seq indicators-data)
                                       (ci/create-chart-with-indicators! node chart-type candle-data indicators-data)
                                       (ci/create-chart-with-volume-and-series! node chart-type candle-data))
                           chart (.-chart chart-obj)
                           main-series (.-mainSeries chart-obj)
                           volume-series (.-volumeSeries chart-obj)]
                       ;; Create legend for main series only
                       (ci/create-legend! node chart main-series chart-type))
                     (catch :default e
                       (js/console.error "Error in chart:" e)))
                   :replicant.life-cycle/unmount
                   nil
                   nil))]
    [:div {:class ["w-full" "relative" "flex-1" "h-full" "min-h-[480px]"]
           :replicant/key (str "chart-" chart-type "-" (hash candle-data) "-" (hash active-indicators))
           :replicant/on-render mount!
           :style {:background-color "rgb(30, 41, 55)"}}]))

(defn trading-chart-view [state]
  (let [active-asset (:active-asset state)
        candles-map (:candles state)
        ;; Use selected timeframe from state
        selected-timeframe (get-in state [:chart-options :selected-timeframe] :1d)
        selected-chart-type (get-in state [:chart-options :selected-chart-type] :candlestick)
        api-response (get-in candles-map [active-asset selected-timeframe] {})
        ;; Check for error state
        has-error? (contains? api-response :error)
        ;; Handle both possible data structures: direct array or wrapped in :data
        raw-candles (if (vector? api-response)
                      api-response  ; Direct array
                      (get api-response :data []))  ; Wrapped in :data
        candle-data (dp/process-candle-data raw-candles)]
    [:div {:class ["w-full" "h-full"]}
     ;; Chart container with consistent width for both menu and chart
     [:div {:class ["w-full" "h-full" "flex" "flex-col"]}
      ;; Add the top menu above the chart
      (chart-top-menu state)
      (if has-error?
        [:div {:class ["text-red-500" "p-4" "flex-1"]} "Error fetching chart data."]
        (chart-canvas candle-data selected-chart-type (get-in state [:chart-options :active-indicators] {})))]]))
