(ns hyperopen.views.trading-chart.derived-cache
  (:require [hyperopen.views.trading-chart.utils.data-processing :as data-processing]
            [hyperopen.views.trading-chart.utils.indicators :as indicators]))

(defonce ^:private candle-data-cache (atom nil))
(defonce ^:private indicator-output-cache (atom nil))

(def ^:dynamic *process-candle-data* data-processing/process-candle-data)
(def ^:dynamic *calculate-indicator* indicators/calculate-indicator)

(defn- sorted-active-indicator-configs
  [active-indicators]
  (sort-by (comp name key) active-indicators))

(defn- flatten-indicator-series
  [indicators-data]
  (vec (mapcat :series indicators-data)))

(defn- flatten-indicator-markers
  [indicators-data]
  (vec (mapcat :markers indicators-data)))

(defn memoized-candle-data
  [raw-candles selected-timeframe]
  (let [cache @candle-data-cache
        cache-hit? (and (map? cache)
                        (identical? raw-candles (:raw-candles cache))
                        (= selected-timeframe (:selected-timeframe cache)))]
    (if cache-hit?
      (:result cache)
      (let [result (*process-candle-data* raw-candles)]
        (reset! candle-data-cache {:raw-candles raw-candles
                                   :selected-timeframe selected-timeframe
                                   :result result})
        result))))

(defn memoized-indicator-outputs
  [candle-data selected-timeframe active-indicators]
  (let [active-indicators* (or active-indicators {})
        cache @indicator-output-cache
        cache-hit? (and (map? cache)
                        (identical? candle-data (:candle-data cache))
                        (= selected-timeframe (:selected-timeframe cache))
                        (= active-indicators* (:active-indicators cache)))]
    (if cache-hit?
      (:result cache)
      (let [indicators-data (->> (sorted-active-indicator-configs active-indicators*)
                                 (keep (fn [[indicator-type config]]
                                         (*calculate-indicator* indicator-type candle-data config)))
                                 vec)
            result {:indicators-data indicators-data
                    :indicator-series (flatten-indicator-series indicators-data)
                    :indicator-markers (flatten-indicator-markers indicators-data)}]
        (reset! indicator-output-cache {:candle-data candle-data
                                        :selected-timeframe selected-timeframe
                                        :active-indicators active-indicators*
                                        :result result})
        result))))

(defn reset-derived-cache!
  []
  (reset! candle-data-cache nil)
  (reset! indicator-output-cache nil))
