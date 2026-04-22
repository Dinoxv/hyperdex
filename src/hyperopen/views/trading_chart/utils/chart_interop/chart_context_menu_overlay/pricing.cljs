(ns hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.pricing
  (:require [hyperopen.domain.trading :as trading-domain]
            [hyperopen.utils.formatting :as fmt]
            [hyperopen.views.trading-chart.utils.chart-interop.chart-context-menu-overlay.support :as support]))

(defn- last-visible-price
  [candles]
  (let [last-candle (when (sequential? candles)
                      (last candles))]
    (or (support/parse-number (:close last-candle))
        (support/parse-number (:value last-candle))
        (support/parse-number (:price last-candle))
        (support/parse-number (:open last-candle)))))

(defn- y->price
  [chart-obj y]
  (let [main-series (some-> chart-obj (aget "mainSeries"))]
    (when (support/finite-number? y)
      (support/parse-number (support/invoke-method main-series "coordinateToPrice" y)))))

(defn- price-from-anchor
  [chart-obj anchor]
  (when-let [y (some-> anchor :y support/parse-number)]
    (y->price chart-obj y)))

(defn- normalize-price-decimals
  [value]
  (when-let [parsed (support/parse-number value)]
    (-> parsed
        js/Math.floor
        int
        (max 0))))

(defn- format-price-label
  [format-price price price-decimals]
  (let [normalized-decimals (normalize-price-decimals price-decimals)]
    (or (when (and (support/finite-number? price)
                   (some? normalized-decimals))
          (trading-domain/number->clean-string price normalized-decimals))
        (when (fn? format-price)
          (format-price price))
        (fmt/format-trade-price-plain price))))

(defn resolve-copy-price-data
  [chart-obj candles anchor format-price price-decimals]
  (let [price (or (price-from-anchor chart-obj anchor)
                  (last-visible-price candles))
        label (when (support/finite-number? price)
                (format-price-label format-price price price-decimals))]
    {:price price
     :label label
     :copy-enabled? (boolean (seq label))}))
