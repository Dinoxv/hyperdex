(ns hyperopen.views.trading-chart.utils.indicators
  (:require ["indicatorts" :refer [sma]]))

(defn calculate-sma
  "Calculate Simple Moving Average for given data and period - follows TradingView pattern"
  [data period]
  (when (and data (> (count data) 0))
    (let [result (for [i (range (count data))]
                   (if (< i period)
                     ;; Provide whitespace data points until the MA can be calculated
                     {:time (:time (nth data i))}
                     ;; Calculate the moving average for this point
                     (let [sum (reduce + (for [j (range period)]
                                          (:close (nth data (- i j)))))
                           ma-value (/ sum period)]
                       {:time (:time (nth data i)) :value ma-value})))]
      result)))

(defn get-available-indicators
  "Return list of available indicators"
  []
  [{:id :sma
    :name "Moving Average"
    :short-name "MA"
    :description "Moving average"
    :default-period 20
    :min-period 2
    :max-period 200}])

(defn calculate-indicator
  "Calculate indicator based on type and parameters"
  [indicator-type data params]
  (case indicator-type
    :sma (calculate-sma data (:period params 20))
    nil)) 
