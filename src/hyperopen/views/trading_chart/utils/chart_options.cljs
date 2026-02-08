(ns hyperopen.views.trading-chart.utils.chart-options)

(def default-right-offset-bars 4)

(defn- common-chart-options []
  {:layout {:textColor "#e5e7eb"
            :background {:type "solid"
                         :color "rgb(15, 26, 31)"}}
   :grid {:vertLines {:color "#374151"}
          :horzLines {:color "#374151"}}
   :rightPriceScale {:borderColor "#374151"}
   :timeScale {:borderColor "#374151"
               :rightOffset default-right-offset-bars}})

(defn base-chart-options []
  (assoc (common-chart-options) :autoSize true))

(defn fixed-height-chart-options [height]
  (assoc (common-chart-options) :height height))
