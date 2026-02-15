(ns hyperopen.domain.trading.indicators.flow
  (:require [hyperopen.domain.trading.indicators.math :as imath]
            [hyperopen.domain.trading.indicators.result :as result]))

(def ^:private flow-indicator-definitions
  [{:id :accumulation-distribution
    :name "Accumulation/Distribution"
    :short-name "A/D"
    :description "Cumulative money flow volume line"
    :supports-period? false
    :default-config {}
    :migrated-from :indicators}
   {:id :accumulative-swing-index
    :name "Accumulative Swing Index"
    :short-name "ASI"
    :description "Wilder swing index accumulated over time"
    :supports-period? false
    :default-config {}
    :migrated-from :indicators}
   {:id :volume
    :name "Volume"
    :short-name "VOL"
    :description "Raw traded volume"
    :supports-period? false
    :default-config {}
    :migrated-from :wave3}])

(defn get-flow-indicators
  []
  flow-indicator-definitions)

(def ^:private field-values imath/field-values)

(defn- calculate-accumulation-distribution
  [data _params]
  (let [highs (field-values data :high)
        lows (field-values data :low)
        closes (field-values data :close)
        volumes (field-values data :volume)
        size (count data)
        values (loop [idx 0
                      running 0
                      result []]
                 (if (= idx size)
                   result
                   (let [high (nth highs idx)
                         low (nth lows idx)
                         close (nth closes idx)
                         volume (nth volumes idx)
                         range-value (- high low)
                         multiplier (if (zero? range-value)
                                      0
                                      (/ (- (- close low)
                                            (- high close))
                                         range-value))
                         next-value (+ running (* multiplier volume))]
                     (recur (inc idx) next-value (conj result next-value)))))]
    (result/indicator-result :accumulation-distribution
                             :separate
                             [(result/line-series :adl values)])))

(defn- calculate-accumulative-swing-index
  [data _params]
  (let [opens (field-values data :open)
        highs (field-values data :high)
        lows (field-values data :low)
        closes (field-values data :close)
        size (count data)
        values (loop [idx 0
                      running 0
                      result []]
                 (if (= idx size)
                   result
                   (if (zero? idx)
                     (recur (inc idx) 0 (conj result 0))
                     (let [open (nth opens idx)
                           high (nth highs idx)
                           low (nth lows idx)
                           close (nth closes idx)
                           prev-open (nth opens (dec idx))
                           prev-close (nth closes (dec idx))
                           distance-a (js/Math.abs (- high prev-close))
                           distance-b (js/Math.abs (- low prev-close))
                           distance-c (js/Math.abs (- high low))
                           distance-d (js/Math.abs (- prev-close prev-open))
                           range-value (cond
                                         (and (> distance-a distance-b)
                                              (> distance-a distance-c))
                                         (+ (- distance-a (/ distance-b 2))
                                            (/ distance-d 4))

                                         (and (> distance-b distance-a)
                                              (> distance-b distance-c))
                                         (+ (- distance-b (/ distance-a 2))
                                            (/ distance-d 4))

                                         :else
                                         (+ distance-c (/ distance-d 4)))
                           k (max distance-a distance-b)
                           t (max distance-a distance-b distance-c)
                           swing-index (if (or (zero? range-value) (zero? t))
                                         0
                                         (* 50
                                            (/ (+ (- close prev-close)
                                                  (/ (- close open) 2)
                                                  (/ (- prev-close prev-open) 4))
                                               range-value)
                                            (/ k t)))
                           next-value (+ running swing-index)]
                       (recur (inc idx) next-value (conj result next-value))))))]
    (result/indicator-result :accumulative-swing-index
                             :separate
                             [(result/line-series :asi values)])))

(defn- calculate-volume
  [data _params]
  (let [values (field-values data :volume)]
    (result/indicator-result :volume
                             :separate
                             [(result/histogram-series :volume values)])))

(def ^:private flow-calculators
  {:accumulation-distribution calculate-accumulation-distribution
   :accumulative-swing-index calculate-accumulative-swing-index
   :volume calculate-volume})

(defn calculate-flow-indicator
  [indicator-type data params]
  (let [config (or params {})
        calculator (get flow-calculators indicator-type)]
    (when calculator
      (calculator data config))))
