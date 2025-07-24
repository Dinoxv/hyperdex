(ns hyperopen.views.trading-chart.utils.chart-interop
  (:require ["lightweight-charts" :refer [createChart CandlestickSeries]]))

;; Candlestick chart implementation
(defn create-candlestick-chart! [container]
  "Create a chart with candlestick options"
  (let [chartOptions #js {:layout #js {:textColor "#e5e7eb" 
                                       :background #js {:type "solid" 
                                                       :color "#1f2937"}}
                          :grid #js {:vertLines #js {:color "#374151"}
                                    :horzLines #js {:color "#374151"}}
                          :rightPriceScale #js {:borderColor "#374151"}
                          :timeScale #js {:borderColor "#374151"}}]
    (let [chart (createChart container chartOptions)]
      chart)))

(defn add-candlestick-series! [chart]
  "Add a candlestick series to the chart"
  (let [seriesOptions #js {:upColor "#26a69a"
                           :downColor "#ef5350"
                           :borderVisible false
                           :wickUpColor "#26a69a"
                           :wickDownColor "#ef5350"}
        series (.addSeries ^js chart CandlestickSeries seriesOptions)]
    series))

(defn set-candlestick-data! [series data]
  "Set candlestick data from argument (vector of maps in JS format)"
  (.setData series (clj->js data)))

(defn fit-content! [chart]
  "Fit content to the chart viewport"
  (.fitContent ^js (.timeScale ^js chart))) 

(defn create-legend! [container chart candlestick-series]
  "Create legend element following TradingView documentation pattern"
  ;; Ensure container has relative positioning for absolute legend positioning
  (let [container-style (.-style container)]
    (when (or (not (.-position container-style)) 
              (= (.-position container-style) "static"))
      (set! (.-position container-style) "relative")))
  
  ;; Create legend div element
  (let [legend (js/document.createElement "div")]
    ;; Fully transparent legend styling (no background, no box-shadow, no border)
    (set! (.-style legend) "position: absolute; left: 12px; top: 12px; z-index: 100; font-size: 13px; font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.4; font-weight: 500; color: #ffffff; padding: 8px 12px; border-radius: 6px; box-shadow: none;")
    
    ;; Set initial content
    (set! (.-innerHTML legend) "")
    
    ;; Append to container
    (.appendChild container legend)
    
    ;; Format price helper
    (let [format-price (fn [price] (.toFixed price 2))
          
          ;; Update legend content
          update-legend (fn [param]
                         (if (and param (.-time param))
                           ;; Valid crosshair point - show OHLC
                           (let [data-map (.-seriesData param)
                                 bar (.get data-map candlestick-series)]
                             (if bar
                               (let [o (.-open bar)
                                     h (.-high bar)
                                     l (.-low bar)
                                     c (.-close bar)]
                                 (set! (.-innerHTML legend) 
                                       (str "<span style='color: #888; font-weight: 400;'>O</span> " (format-price o) 
                                            " <span style='color: #888; font-weight: 400;'>H</span> " (format-price h) 
                                            " <span style='color: #888; font-weight: 400;'>L</span> " (format-price l) 
                                            " <span style='color: #888; font-weight: 400;'>C</span> " (format-price c))))
                               (set! (.-innerHTML legend) "No data")))
                           ;; No crosshair point - show hint
                           (set! (.-innerHTML legend) "")))]
      
      ;; Subscribe to crosshair move events
      (.subscribeCrosshairMove ^js chart update-legend)
      
      ;; Initialize
      (update-legend nil)))) 