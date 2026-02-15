(ns hyperopen.domain.trading.indicators.trend
  (:require [hyperopen.domain.trading.indicators.math :as imath]
            [hyperopen.domain.trading.indicators.result :as result]))

(def ^:private trend-indicator-definitions
  [{:id :alma
    :name "Arnaud Legoux Moving Average"
    :short-name "ALMA"
    :description "Gaussian-weighted moving average"
    :supports-period? true
    :default-period 9
    :min-period 2
    :max-period 200
    :default-config {:period 9
                     :offset 0.85
                     :sigma 6}}
   {:id :aroon
    :name "Aroon"
    :short-name "Aroon"
    :description "Aroon Up and Aroon Down lines"
    :supports-period? true
    :default-period 14
    :min-period 2
    :max-period 200
    :default-config {:period 14}}
   {:id :adx
    :name "Average Directional Index"
    :short-name "ADX"
    :description "Trend strength from directional movement"
    :supports-period? true
    :default-period 14
    :min-period 2
    :max-period 200
    :default-config {:period 14
                     :smoothing 14}}
   {:id :sma
    :name "Moving Average"
    :short-name "MA"
    :description "Simple moving average"
    :supports-period? true
    :default-period 20
    :min-period 2
    :max-period 200
    :default-config {:period 20}}
   {:id :guppy-multiple-moving-average
    :name "Guppy Multiple Moving Average"
    :short-name "GMMA"
    :description "Short and long EMA ribbon"
    :supports-period? false
    :default-config {}
    :migrated-from :wave3}
   {:id :mcginley-dynamic
    :name "McGinley Dynamic"
    :short-name "MGD"
    :description "Adaptive moving average with speed correction"
    :supports-period? true
    :default-period 14
    :min-period 2
    :max-period 400
    :default-config {:period 14}
    :migrated-from :wave3}
   {:id :moving-average-adaptive
    :name "Moving Average Adaptive"
    :short-name "KAMA"
    :description "Kaufman Adaptive Moving Average"
    :supports-period? true
    :default-period 10
    :min-period 2
    :max-period 400
    :default-config {:period 10
                     :fast 2
                     :slow 30}
    :migrated-from :wave3}
   {:id :moving-average-hamming
    :name "Moving Average Hamming"
    :short-name "HAMMA"
    :description "Moving average with Hamming window weights"
    :supports-period? true
    :default-period 20
    :min-period 2
    :max-period 400
    :default-config {:period 20}
    :migrated-from :wave3}
   {:id :williams-alligator
    :name "Williams Alligator"
    :short-name "Alligator"
    :description "Three smoothed moving averages with offsets"
    :supports-period? false
    :default-config {:jaw-period 13
                     :jaw-shift 8
                     :teeth-period 8
                     :teeth-shift 5
                     :lips-period 5
                     :lips-shift 3}
    :migrated-from :wave3}])

(defn get-trend-indicators
  []
  trend-indicator-definitions)

(def ^:private finite-number? imath/finite-number?)
(def ^:private parse-period imath/parse-period)
(def ^:private field-values imath/field-values)

(defn- window-for-index
  [values idx period]
  (imath/window-for-index values idx period :lagged))

(defn- sma-values
  [values period]
  (imath/sma-values values period :lagged))

(defn- rma-values
  [values period]
  (imath/rma-values values period :lagged))

(defn- rma-aligned-values
  [values period]
  (imath/rma-values values period :aligned))

(defn- ema-values
  [values period]
  (imath/ema-values values period))

(defn calculate-sma-values
  [data period]
  (let [length (parse-period period 20 2 1000)
        closes (field-values data :close)]
    (sma-values closes length)))

(defn- shift-right
  [values shift]
  (let [size (count values)
        shifted (concat (repeat shift nil) values)]
    (vec (take size shifted))))

(defn- make-hamming-weights
  [period]
  (if (= period 1)
    [1]
    (let [weights (mapv (fn [idx]
                          (- 0.54
                             (* 0.46
                                (js/Math.cos (/ (* 2 js/Math.PI idx)
                                                (dec period))))))
                        (range period))
          total (reduce + 0 weights)]
      (mapv #(/ % total) weights))))

(defn- weighted-ma
  [values weights]
  (let [period (count weights)]
    (imath/rolling-apply values
                         period
                         (fn [window]
                           (reduce + 0 (map * window weights)))
                         :aligned)))

(defn- alma-weights
  [period offset sigma]
  (let [m (* offset (dec period))
        s (/ period sigma)]
    (mapv (fn [idx]
            (js/Math.exp
             (/ (- (* (- idx m) (- idx m)))
                (* 2 s s))))
          (range period))))

(defn- calculate-alma
  [data params]
  (let [period (parse-period (:period params) 9 2 200)
        offset (or (:offset params) 0.85)
        sigma (or (:sigma params) 6)
        weights (alma-weights period offset sigma)
        denominator (reduce + 0 weights)
        closes (field-values data :close)
        size (count data)
        values (mapv (fn [idx]
                       (when-let [window (window-for-index closes idx period)]
                         (when (every? finite-number? window)
                           (/ (reduce + 0 (map * window weights))
                              denominator))))
                     (range size))]
    (result/indicator-result :alma
                             :overlay
                             [(result/line-series :alma values)])))

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

(defn- calculate-guppy-multiple-moving-average
  [data _params]
  (let [close-values (field-values data :close)
        short-periods [3 5 8 10 12 15]
        long-periods [30 35 40 45 50 60]
        short-series (mapv (fn [period]
                             (result/line-series (keyword (str "ema-short-" period))
                                                 (ema-values close-values period)))
                           short-periods)
        long-series (mapv (fn [period]
                            (result/line-series (keyword (str "ema-long-" period))
                                                (ema-values close-values period)))
                          long-periods)]
    (result/indicator-result :guppy-multiple-moving-average
                             :overlay
                             (vec (concat short-series long-series)))))

(defn- calculate-mcginley-dynamic
  [data params]
  (let [period (parse-period (:period params) 14 2 400)
        close-values (field-values data :close)
        size (count data)
        values (loop [idx 0
                      prev nil
                      out []]
                 (if (= idx size)
                   out
                   (let [close (nth close-values idx)
                         current (if (nil? prev)
                                   close
                                   (let [ratio (if (zero? prev)
                                                 1
                                                 (/ close prev))
                                         denom (* period (js/Math.pow ratio 4))]
                                     (+ prev (/ (- close prev)
                                                (if (zero? denom) period denom)))))]
                     (recur (inc idx)
                            current
                            (conj out current)))))]
    (result/indicator-result :mcginley-dynamic
                             :overlay
                             [(result/line-series :mcginley values)])))

(defn- calculate-moving-average-adaptive
  [data params]
  (let [period (parse-period (:period params) 10 2 400)
        fast (parse-period (:fast params) 2 2 100)
        slow (parse-period (:slow params) 30 2 200)
        close-values (field-values data :close)
        size (count data)
        fast-sc (/ 2 (inc fast))
        slow-sc (/ 2 (inc slow))
        values (loop [idx 0
                      prev nil
                      out []]
                 (if (= idx size)
                   out
                   (let [close (nth close-values idx)
                         current (if (or (nil? prev) (< idx period))
                                   close
                                   (let [change (js/Math.abs (- close (nth close-values (- idx period))))
                                         volatility (reduce + 0
                                                            (map (fn [j]
                                                                   (js/Math.abs (- (nth close-values j)
                                                                                   (nth close-values (dec j)))))
                                                                 (range (- idx period -1) (inc idx))))
                                         er (if (zero? volatility)
                                              0
                                              (/ change volatility))
                                         sc (js/Math.pow (+ (* er (- fast-sc slow-sc)) slow-sc) 2)]
                                     (+ prev (* sc (- close prev)))))]
                     (recur (inc idx)
                            current
                            (conj out current)))))]
    (result/indicator-result :moving-average-adaptive
                             :overlay
                             [(result/line-series :kama values)])))

(defn- calculate-moving-average-hamming
  [data params]
  (let [period (parse-period (:period params) 20 2 400)
        weights (make-hamming-weights period)
        values (weighted-ma (field-values data :close) weights)]
    (result/indicator-result :moving-average-hamming
                             :overlay
                             [(result/line-series :hamming-ma values)])))

(defn- calculate-williams-alligator
  [data params]
  (let [jaw-period (parse-period (:jaw-period params) 13 2 200)
        jaw-shift (parse-period (:jaw-shift params) 8 0 50)
        teeth-period (parse-period (:teeth-period params) 8 2 200)
        teeth-shift (parse-period (:teeth-shift params) 5 0 50)
        lips-period (parse-period (:lips-period params) 5 2 200)
        lips-shift (parse-period (:lips-shift params) 3 0 50)
        median (mapv (fn [high low]
                       (/ (+ high low) 2))
                     (field-values data :high)
                     (field-values data :low))
        jaw (shift-right (rma-aligned-values median jaw-period) jaw-shift)
        teeth (shift-right (rma-aligned-values median teeth-period) teeth-shift)
        lips (shift-right (rma-aligned-values median lips-period) lips-shift)]
    (result/indicator-result :williams-alligator
                             :overlay
                             [(result/line-series :jaw jaw)
                              (result/line-series :teeth teeth)
                              (result/line-series :lips lips)])))

(def ^:private trend-calculators
  {:alma calculate-alma
   :aroon calculate-aroon
   :adx calculate-adx
   :guppy-multiple-moving-average calculate-guppy-multiple-moving-average
   :mcginley-dynamic calculate-mcginley-dynamic
   :moving-average-adaptive calculate-moving-average-adaptive
   :moving-average-hamming calculate-moving-average-hamming
   :sma (fn [data params]
          (result/indicator-result :sma
                                   :overlay
                                   [(result/line-series :sma
                                                        (calculate-sma-values data (:period params 20)))]))
   :williams-alligator calculate-williams-alligator})

(defn calculate-trend-indicator
  [indicator-type data params]
  (let [config (or params {})
        calculator (get trend-calculators indicator-type)]
    (when calculator
      (calculator data config))))
