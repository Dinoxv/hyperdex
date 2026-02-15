(ns hyperopen.domain.trading.indicators.price
  (:require [hyperopen.domain.trading.indicators.math :as imath]
            [hyperopen.domain.trading.indicators.result :as result]))

(def ^:private price-indicator-definitions
  [{:id :average-price
    :name "Average Price"
    :short-name "OHLC4"
    :description "(Open + High + Low + Close) / 4"
    :supports-period? false
    :default-config {}
    :migrated-from :indicators}])

(defn get-price-indicators
  []
  price-indicator-definitions)

(def ^:private field-values imath/field-values)

(defn- calculate-average-price
  [data _params]
  (let [opens (field-values data :open)
        highs (field-values data :high)
        lows (field-values data :low)
        closes (field-values data :close)
        size (count data)
        values (mapv (fn [idx]
                       (/ (+ (nth opens idx)
                             (nth highs idx)
                             (nth lows idx)
                             (nth closes idx))
                          4))
                     (range size))]
    (result/indicator-result :average-price
                             :overlay
                             [(result/line-series :ohlc4 values)])))

(def ^:private price-calculators
  {:average-price calculate-average-price})

(defn calculate-price-indicator
  [indicator-type data params]
  (let [config (or params {})
        calculator (get price-calculators indicator-type)]
    (when calculator
      (calculator data config))))
