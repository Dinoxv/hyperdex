(ns hyperopen.domain.trading.indicators.registry
  (:require [hyperopen.domain.trading.indicators.flow :as flow]
            [hyperopen.domain.trading.indicators.oscillators :as oscillators]
            [hyperopen.domain.trading.indicators.price :as price]
            [hyperopen.domain.trading.indicators.trend :as trend]
            [hyperopen.domain.trading.indicators.volatility :as volatility]))

(defn get-domain-indicators
  []
  (vec (concat (trend/get-trend-indicators)
               (oscillators/get-oscillator-indicators)
               (volatility/get-volatility-indicators)
               (flow/get-flow-indicators)
               (price/get-price-indicators))))

(defn calculate-domain-indicator
  [indicator-type data params]
  (let [config (or params {})]
    (or (trend/calculate-trend-indicator indicator-type data config)
        (oscillators/calculate-oscillator-indicator indicator-type data config)
        (volatility/calculate-volatility-indicator indicator-type data config)
        (flow/calculate-flow-indicator indicator-type data config)
        (price/calculate-price-indicator indicator-type data config))))
