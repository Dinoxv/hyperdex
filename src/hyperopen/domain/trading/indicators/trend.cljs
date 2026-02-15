(ns hyperopen.domain.trading.indicators.trend
  (:require [hyperopen.domain.trading.indicators.catalog.trend :as catalog]
            [hyperopen.domain.trading.indicators.family-runtime :as family-runtime]
            [hyperopen.domain.trading.indicators.math-adapter :as math-adapter]
            [hyperopen.domain.trading.indicators.math :as imath]
            [hyperopen.domain.trading.indicators.trend.moving-averages :as moving-averages]
            [hyperopen.domain.trading.indicators.trend.regression :as regression]
            [hyperopen.domain.trading.indicators.result :as result]))

(def ^:private trend-indicator-definitions catalog/trend-indicator-definitions)

(declare trend-family)

(defn get-trend-indicators
  []
  (family-runtime/indicators trend-family))

(def ^:private finite-number? imath/finite-number?)
(def ^:private parse-period imath/parse-period)
(def ^:private parse-number imath/parse-number)
(def ^:private field-values imath/field-values)
(def ^:private normalize-values imath/normalize-values)

(defn- window-for-index
  [values idx period]
  (imath/window-for-index values idx period :lagged))

(defn- sma-values
  [values period]
  (imath/sma-values values period :lagged))

(defn- sma-aligned-values
  [values period]
  (imath/sma-values values period :aligned))

(defn- rma-values
  [values period]
  (imath/rma-values values period :lagged))

(defn calculate-sma-values
  [data period]
  (let [length (parse-period period 20 2 1000)
        closes (field-values data :close)]
    (sma-values closes length)))

(defn- fit-series-length
  [values size]
  (let [series (vec values)
        current-size (count series)]
    (cond
      (= current-size size) series
      (> current-size size) (subvec series 0 size)
      :else (into series (repeat (- size current-size) nil)))))

(defn- last-index-of
  [values target]
  (loop [idx (dec (count values))]
    (cond
      (< idx 0) nil
      (= (nth values idx) target) idx
      :else (recur (dec idx)))))

(defn- calculate-aroon
  [data params]
  (let [period (parse-period (:period params) 14 2 200)
        highs (field-values data :high)
        lows (field-values data :low)
        size (count data)
        [up-values down-values]
        (loop [idx 0
               up-result []
               down-result []]
          (if (= idx size)
            [up-result down-result]
            (if (< idx period)
              (recur (inc idx)
                     (conj up-result nil)
                     (conj down-result nil))
              (let [high-window (window-for-index highs idx period)
                    low-window (window-for-index lows idx period)
                    max-high (apply max high-window)
                    min-low (apply min low-window)
                    high-index (or (last-index-of high-window max-high) 0)
                    low-index (or (last-index-of low-window min-low) 0)
                    bars-since-high (- (dec period) high-index)
                    bars-since-low (- (dec period) low-index)
                    up (* 100 (/ (- period bars-since-high) period))
                    down (* 100 (/ (- period bars-since-low) period))]
                (recur (inc idx)
                       (conj up-result up)
                       (conj down-result down))))))]
    (result/indicator-result :aroon
                             :separate
                             [(result/line-series :aroon-up up-values)
                              (result/line-series :aroon-down down-values)])))

(defn- true-range-values
  [highs lows closes]
  (let [size (count highs)]
    (mapv (fn [idx]
            (let [high (nth highs idx)
                  low (nth lows idx)
                  prev-close (if (zero? idx) (nth closes idx) (nth closes (dec idx)))]
              (max (- high low)
                   (js/Math.abs (- high prev-close))
                   (js/Math.abs (- low prev-close)))))
          (range size))))

(defn- plus-minus-di-values
  [data period]
  (let [high-values (field-values data :high)
        low-values (field-values data :low)
        close-values (field-values data :close)
        size (count high-values)
        plus-dm (mapv (fn [idx]
                        (if (zero? idx)
                          0
                          (let [up-move (- (nth high-values idx) (nth high-values (dec idx)))
                                down-move (- (nth low-values (dec idx)) (nth low-values idx))]
                            (if (and (> up-move down-move) (> up-move 0))
                              up-move
                              0))))
                      (range size))
        minus-dm (mapv (fn [idx]
                         (if (zero? idx)
                           0
                           (let [up-move (- (nth high-values idx) (nth high-values (dec idx)))
                                 down-move (- (nth low-values (dec idx)) (nth low-values idx))]
                             (if (and (> down-move up-move) (> down-move 0))
                               down-move
                               0))))
                       (range size))
        tr-values (true-range-values high-values low-values close-values)
        atr-values (rma-values tr-values period)
        plus-rma (rma-values plus-dm period)
        minus-rma (rma-values minus-dm period)
        plus-di (mapv (fn [idx]
                        (let [atr (nth atr-values idx)
                              value (nth plus-rma idx)]
                          (when (and (finite-number? atr)
                                     (finite-number? value)
                                     (pos? atr))
                            (* 100 (/ value atr)))))
                      (range size))
        minus-di (mapv (fn [idx]
                         (let [atr (nth atr-values idx)
                               value (nth minus-rma idx)]
                           (when (and (finite-number? atr)
                                      (finite-number? value)
                                      (pos? atr))
                             (* 100 (/ value atr)))))
                       (range size))]
    {:plus-di plus-di
     :minus-di minus-di}))

(defn- calculate-adx
  [data params]
  (let [period (parse-period (:period params) 14 2 200)
        smoothing (parse-period (:smoothing params) 14 2 200)
        highs (field-values data :high)
        lows (field-values data :low)
        closes (field-values data :close)
        size (count data)
        plus-dm (mapv (fn [idx]
                        (if (zero? idx)
                          0
                          (let [up-move (- (nth highs idx) (nth highs (dec idx)))
                                down-move (- (nth lows (dec idx)) (nth lows idx))]
                            (if (and (> up-move down-move) (> up-move 0))
                              up-move
                              0))))
                      (range size))
        minus-dm (mapv (fn [idx]
                         (if (zero? idx)
                           0
                           (let [up-move (- (nth highs idx) (nth highs (dec idx)))
                                 down-move (- (nth lows (dec idx)) (nth lows idx))]
                             (if (and (> down-move up-move) (> down-move 0))
                               down-move
                               0))))
                       (range size))
        tr-values (true-range-values highs lows closes)
        atr-values (rma-values tr-values period)
        plus-rma (rma-values plus-dm period)
        minus-rma (rma-values minus-dm period)
        plus-di (mapv (fn [idx]
                        (let [atr (nth atr-values idx)
                              value (nth plus-rma idx)]
                          (when (and (finite-number? atr)
                                     (finite-number? value)
                                     (pos? atr))
                            (* 100 (/ value atr)))))
                      (range size))
        minus-di (mapv (fn [idx]
                         (let [atr (nth atr-values idx)
                               value (nth minus-rma idx)]
                           (when (and (finite-number? atr)
                                      (finite-number? value)
                                      (pos? atr))
                             (* 100 (/ value atr)))))
                       (range size))
        dx-values (mapv (fn [idx]
                          (let [plus (nth plus-di idx)
                                minus (nth minus-di idx)
                                total (+ (or plus 0) (or minus 0))]
                            (when (and (finite-number? plus)
                                       (finite-number? minus)
                                       (pos? total))
                              (* 100 (/ (js/Math.abs (- plus minus)) total)))))
                        (range size))
        adx-raw (rma-values (mapv #(or % 0) dx-values) smoothing)
        warmup (+ period smoothing)
        adx-values (mapv (fn [idx value]
                           (when (and (>= idx warmup)
                                      (finite-number? value))
                             value))
                         (range size)
                         adx-raw)]
    (result/indicator-result :adx
                             :separate
                             [(result/line-series :adx adx-values)])))

(defn- calculate-directional-movement
  [data params]
  (let [period (parse-period (:period params) 14 2 200)
        {:keys [plus-di minus-di]} (plus-minus-di-values data period)]
    (result/indicator-result :directional-movement
                             :separate
                             [(result/line-series :plus-di plus-di)
                              (result/line-series :minus-di minus-di)])))

(defn- calculate-envelopes
  [data params]
  (let [period (parse-period (:period params) 20 2 400)
        percent (parse-number (:percent params) 0.025)
        close-values (field-values data :close)
        basis (sma-aligned-values close-values period)
        upper (mapv (fn [value]
                      (when (finite-number? value)
                        (* value (+ 1 percent))))
                    basis)
        lower (mapv (fn [value]
                      (when (finite-number? value)
                        (* value (- 1 percent))))
                    basis)]
    (result/indicator-result :envelopes
                             :overlay
                             [(result/line-series :upper upper)
                              (result/line-series :basis basis)
                              (result/line-series :lower lower)])))

(defn- calculate-ichimoku-cloud
  [data params]
  (let [short (parse-period (:short params) 9 2 200)
        medium (parse-period (:medium params) 26 2 300)
        long (parse-period (:long params) 52 2 400)
        close-shift (parse-period (:close params) 26 1 300)
        size (count data)
        result (math-adapter/ichimoku-cloud (field-values data :high)
                                            (field-values data :low)
                                            (field-values data :close)
                                            {:short short
                                             :medium medium
                                             :long long
                                             :close close-shift})
        tenkan (fit-series-length (normalize-values (:tenkan result) {:zero-as-nil? true}) size)
        kijun (fit-series-length (normalize-values (:kijun result) {:zero-as-nil? true}) size)
        ssa (fit-series-length (normalize-values (:ssa result) {:zero-as-nil? true}) size)
        ssb (fit-series-length (normalize-values (:ssb result) {:zero-as-nil? true}) size)
        lagging-span (fit-series-length (normalize-values (:laggingSpan result) {:zero-as-nil? true}) size)]
    (result/indicator-result :ichimoku-cloud
                             :overlay
                             [(result/line-series :tenkan tenkan)
                              (result/line-series :kijun kijun)
                              (result/line-series :ssa ssa)
                              (result/line-series :ssb ssb)
                              (result/line-series :lagging lagging-span)])))

(defn- calculate-parabolic-sar
  [data params]
  (let [step (parse-number (:step params) 0.02)
        max-value (parse-number (:max params) 0.2)
        result (math-adapter/parabolic-sar (field-values data :high)
                                           (field-values data :low)
                                           (field-values data :close)
                                           {:step step :max-value max-value})
        values (normalize-values (:psarResult result))]
    (result/indicator-result :parabolic-sar
                             :overlay
                             [(result/line-series :psar values)])))

(defn- calculate-supertrend
  [data params]
  (let [period (parse-period (:period params) 10 2 200)
        multiplier (parse-number (:multiplier params) 3)
        high-values (field-values data :high)
        low-values (field-values data :low)
        close-values (field-values data :close)
        tr-values (true-range-values high-values low-values close-values)
        atr-values (rma-values tr-values period)
        size (count close-values)
        hl2 (mapv (fn [idx]
                    (/ (+ (nth high-values idx)
                          (nth low-values idx))
                       2))
                  (range size))
        basic-upper (mapv (fn [idx]
                            (let [mid (nth hl2 idx)
                                  atr (nth atr-values idx)]
                              (when (and (finite-number? mid)
                                         (finite-number? atr))
                                (+ mid (* multiplier atr)))))
                          (range size))
        basic-lower (mapv (fn [idx]
                            (let [mid (nth hl2 idx)
                                  atr (nth atr-values idx)]
                              (when (and (finite-number? mid)
                                         (finite-number? atr))
                                (- mid (* multiplier atr)))))
                          (range size))
        [final-upper final-lower supertrend trend-up]
        (loop [idx 0
               prev-final-upper nil
               prev-final-lower nil
               prev-supertrend nil
               upper-result []
               lower-result []
               supertrend-result []
               trend-result []]
          (if (= idx size)
            [upper-result lower-result supertrend-result trend-result]
            (let [current-upper (nth basic-upper idx)
                  current-lower (nth basic-lower idx)
                  prev-close (when (pos? idx) (nth close-values (dec idx)))
                  final-up (if (or (nil? prev-final-upper)
                                   (nil? current-upper)
                                   (nil? prev-close)
                                   (< current-upper prev-final-upper)
                                   (> prev-close prev-final-upper))
                             current-upper
                             prev-final-upper)
                  final-low (if (or (nil? prev-final-lower)
                                    (nil? current-lower)
                                    (nil? prev-close)
                                    (> current-lower prev-final-lower)
                                    (< prev-close prev-final-lower))
                              current-lower
                              prev-final-lower)
                  next-supertrend (cond
                                    (nil? prev-supertrend) final-up
                                    (= prev-supertrend prev-final-upper)
                                    (if (<= (nth close-values idx) final-up)
                                      final-up
                                      final-low)
                                    :else
                                    (if (>= (nth close-values idx) final-low)
                                      final-low
                                      final-up))
                  next-trend-up (when (finite-number? next-supertrend)
                                  (<= next-supertrend (nth close-values idx)))]
              (recur (inc idx)
                     final-up
                     final-low
                     next-supertrend
                     (conj upper-result final-up)
                     (conj lower-result final-low)
                     (conj supertrend-result next-supertrend)
                     (conj trend-result next-trend-up)))))
        up-line (mapv (fn [idx]
                        (when (true? (nth trend-up idx))
                          (nth supertrend idx)))
                      (range size))
        down-line (mapv (fn [idx]
                          (when (false? (nth trend-up idx))
                            (nth supertrend idx)))
                        (range size))]
    (result/indicator-result :supertrend
                             :overlay
                             [(result/line-series :up up-line)
                              (result/line-series :down down-line)])))

(defn- calculate-vortex-indicator
  [data params]
  (let [period (parse-period (:period params) 14 2 200)
        result (math-adapter/vortex (field-values data :high)
                                    (field-values data :low)
                                    (field-values data :close)
                                    {:period period})
        plus-values (normalize-values (:plus result))
        minus-values (normalize-values (:minus result))]
    (result/indicator-result :vortex-indicator
                             :separate
                             [(result/line-series :plus plus-values)
                              (result/line-series :minus minus-values)])))

(defn- calculate-vwap
  [data params]
  (let [period (parse-period (:period params) 20 2 400)
        values (normalize-values
                (math-adapter/vwap (field-values data :close)
                                   (field-values data :volume)
                                   {:period period}))]
    (result/indicator-result :vwap
                             :overlay
                             [(result/line-series :vwap values)])))

(defn- calculate-vwma
  [data params]
  (let [period (parse-period (:period params) 20 2 400)
        values (normalize-values
                (math-adapter/vwma (field-values data :close)
                                   (field-values data :volume)
                                   {:period period}))]
    (result/indicator-result :vwma
                             :overlay
                             [(result/line-series :vwma values)])))

(def ^:private trend-calculators
  {:alma moving-averages/calculate-alma
   :aroon calculate-aroon
   :adx calculate-adx
   :double-ema moving-averages/calculate-double-ema
   :directional-movement calculate-directional-movement
   :ema-cross moving-averages/calculate-ema-cross
   :envelopes calculate-envelopes
   :guppy-multiple-moving-average moving-averages/calculate-guppy-multiple-moving-average
   :hull-moving-average moving-averages/calculate-hull-moving-average
   :ichimoku-cloud calculate-ichimoku-cloud
   :least-squares-moving-average regression/calculate-least-squares-moving-average
   :linear-regression-curve regression/calculate-linear-regression-curve
   :linear-regression-slope regression/calculate-linear-regression-slope
   :ma-cross moving-averages/calculate-ma-cross
   :ma-with-ema-cross moving-averages/calculate-ma-with-ema-cross
   :mcginley-dynamic moving-averages/calculate-mcginley-dynamic
   :moving-average-adaptive moving-averages/calculate-moving-average-adaptive
   :moving-average-double moving-averages/calculate-moving-average-double
   :moving-average-exponential moving-averages/calculate-moving-average-exponential
   :moving-average-hamming moving-averages/calculate-moving-average-hamming
   :moving-average-multiple moving-averages/calculate-moving-average-multiple
   :moving-average-triple moving-averages/calculate-moving-average-triple
   :moving-average-weighted moving-averages/calculate-moving-average-weighted
   :parabolic-sar calculate-parabolic-sar
   :sma (fn [data params]
          (result/indicator-result :sma
                                   :overlay
                                   [(result/line-series :sma
                                                        (calculate-sma-values data (:period params 20)))]))
   :smoothed-moving-average moving-averages/calculate-smoothed-moving-average
   :supertrend calculate-supertrend
   :triple-ema moving-averages/calculate-triple-ema
   :vortex-indicator calculate-vortex-indicator
   :vwap calculate-vwap
   :vwma calculate-vwma
   :williams-alligator moving-averages/calculate-williams-alligator})

(def ^:private trend-family
  (family-runtime/build-family :trend
                               trend-indicator-definitions
                               trend-calculators))

(defn supported-trend-indicator-ids
  []
  (family-runtime/supported-indicator-ids trend-family))

(defn calculate-trend-indicator
  [indicator-type data params]
  (family-runtime/calculate trend-family indicator-type data params))
