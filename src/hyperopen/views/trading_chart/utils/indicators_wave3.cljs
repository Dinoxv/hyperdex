(ns hyperopen.views.trading-chart.utils.indicators-wave3
  (:require [hyperopen.domain.trading.indicators.math :as imath]))

(def ^:private wave3-indicator-definitions
  [{:id :chaikin-volatility
    :name "Chaikin Volatility"
    :short-name "CHV"
    :description "Rate-of-change of EMA high-low range"
    :supports-period? true
    :default-period 10
    :min-period 2
    :max-period 200
    :default-config {:period 10
                     :roc-period 10}}
   {:id :chande-kroll-stop
    :name "Chande Kroll Stop"
    :short-name "CKS"
    :description "ATR-based long and short stop lines"
    :supports-period? true
    :default-period 10
    :min-period 2
    :max-period 200
    :default-config {:period 10
                     :atr-period 10
                     :multiplier 1.0}}
   {:id :chop-zone
    :name "Chop Zone"
    :short-name "CZ"
    :description "Trend-angle zone derived from EMA slope"
    :supports-period? true
    :default-period 14
    :min-period 2
    :max-period 200
    :default-config {:period 14}}
   {:id :connors-rsi
    :name "Connors RSI"
    :short-name "CRSI"
    :description "Average of short RSI, streak RSI, and percent-rank"
    :supports-period? false
    :default-config {:rsi-period 3
                     :streak-period 2
                     :rank-period 100}}
   {:id :correlation-log
    :name "Correlation - Log"
    :short-name "Corr Log"
    :description "Rolling Pearson correlation of log price and time"
    :supports-period? true
    :default-period 20
    :min-period 3
    :max-period 400
    :default-config {:period 20}}
   {:id :klinger-oscillator
    :name "Klinger Oscillator"
    :short-name "KVO"
    :description "Volume-force EMA oscillator"
    :supports-period? false
    :default-config {:fast 34
                     :slow 55
                     :signal 13}}
   {:id :know-sure-thing
    :name "Know Sure Thing"
    :short-name "KST"
    :description "Weighted sum of smoothed rate-of-change"
    :supports-period? false
    :default-config {:roc1 10
                     :roc2 15
                     :roc3 20
                     :roc4 30
                     :sma1 10
                     :sma2 10
                     :sma3 10
                     :sma4 15
                     :signal 9}}
   {:id :volume
    :name "Volume"
    :short-name "VOL"
    :description "Raw traded volume"
    :supports-period? false
    :default-config {}}
   ])

(defn get-wave3-indicators
  []
  wave3-indicator-definitions)

(def ^:private finite-number? imath/finite-number?)
(def ^:private parse-period imath/parse-period)
(def ^:private parse-number imath/parse-number)
(def ^:private times imath/times)
(def ^:private field-values imath/field-values)

(defn- highs [data] (field-values data :high))
(defn- lows [data] (field-values data :low))
(defn- opens [data] (field-values data :open))
(defn- closes [data] (field-values data :close))
(defn- volumes [data] (field-values data :volume))

(def ^:private mean imath/mean)

(defn- window-for-index
  [values idx period]
  (imath/window-for-index values idx period :aligned))

(defn- rolling-apply
  [values period f]
  (imath/rolling-apply values period f :aligned))

(defn- rolling-sum
  [values period]
  (imath/rolling-sum values period :aligned))

(defn- sma-values
  [values period]
  (imath/sma-values values period :aligned))

(defn- ema-values
  [values period]
  (imath/ema-values values period))

(defn- rma-values
  [values period]
  (imath/rma-values values period :aligned))

(defn- true-range-values
  [data]
  (let [high-values (highs data)
        low-values (lows data)
        close-values (closes data)
        size (count data)]
    (mapv (fn [idx]
            (let [high (nth high-values idx)
                  low (nth low-values idx)
                  prev-close (when (pos? idx)
                               (nth close-values (dec idx)))
                  range-1 (- high low)
                  range-2 (if (finite-number? prev-close)
                            (js/Math.abs (- high prev-close))
                            range-1)
                  range-3 (if (finite-number? prev-close)
                            (js/Math.abs (- low prev-close))
                            range-1)]
              (max range-1 range-2 range-3)))
          (range size))))

(defn- rolling-max
  [values period]
  (imath/rolling-max values period :aligned))

(defn- rolling-min
  [values period]
  (imath/rolling-min values period :aligned))

(defn- roc-percent-values
  [values period]
  (let [size (count values)]
    (mapv (fn [idx]
            (if (< idx period)
              nil
              (let [current (nth values idx)
                    base (nth values (- idx period))]
                (when (and (finite-number? current)
                           (finite-number? base)
                           (not= base 0))
                  (* 100 (/ (- current base) base))))))
          (range size))))

(defn- pearson-correlation
  [xs ys]
  (when (and (= (count xs) (count ys))
             (seq xs)
             (every? finite-number? xs)
             (every? finite-number? ys))
    (let [mx (mean xs)
          my (mean ys)
          cov (reduce + 0 (map (fn [x y]
                                 (* (- x mx) (- y my)))
                               xs ys))
          sx (reduce + 0 (map (fn [x]
                                (let [d (- x mx)]
                                  (* d d)))
                              xs))
          sy (reduce + 0 (map (fn [y]
                                (let [d (- y my)]
                                  (* d d)))
                              ys))
          denom (js/Math.sqrt (* sx sy))]
      (when (and (finite-number? denom) (> denom 0))
        (/ cov denom)))))

(defn- rolling-correlation-with-time
  [values period]
  (let [time-axis (vec (range 1 (inc period)))
        size (count values)]
    (mapv (fn [idx]
            (when-let [window (window-for-index values idx period)]
              (pearson-correlation window time-axis)))
          (range size))))

(defn- rs-rolling
  [values period]
  (let [size (count values)]
    (mapv (fn [idx]
            (when-let [window (window-for-index values idx period)]
              (when (every? finite-number? window)
                (let [x-values (vec (range period))
                      x-mean (/ (reduce + 0 x-values) period)
                      y-mean (mean window)
                      sxx (reduce + 0 (map (fn [x]
                                             (let [dx (- x x-mean)]
                                               (* dx dx)))
                                           x-values))
                      sxy (reduce + 0 (map (fn [x y]
                                             (* (- x x-mean)
                                                (- y y-mean)))
                                           x-values window))
                      slope (if (zero? sxx) 0 (/ sxy sxx))
                      intercept (- y-mean (* slope x-mean))
                      residuals (map (fn [x y]
                                       (- y (+ intercept (* slope x))))
                                     x-values window)
                      rss (reduce + 0 (map #(* % %) residuals))
                      denom (max 1 (- period 2))]
                  {:slope slope
                   :intercept intercept
                   :standard-error (js/Math.sqrt (/ rss denom))
                   :center (+ intercept (* slope (dec period)))}))))
          (range size))))

(defn- point
  [time value]
  (if (finite-number? value)
    {:time time :value value}
    {:time time}))

(defn- histogram-point
  [time value]
  (if (finite-number? value)
    {:time time
     :value value
     :color (if (neg? value) "#ef4444" "#22c55e")}
    {:time time}))

(defn- points-from-values
  [time-values indicator-values]
  (mapv point time-values indicator-values))

(defn- histogram-points-from-values
  [time-values indicator-values]
  (mapv histogram-point time-values indicator-values))

(defn- line-series
  [id name color time-values indicator-values]
  {:id id
   :name name
   :series-type :line
   :color color
   :line-width 2
   :data (points-from-values time-values indicator-values)})

(defn- histogram-series
  [id name time-values indicator-values]
  {:id id
   :name name
   :series-type :histogram
   :data (histogram-points-from-values time-values indicator-values)})

(defn- indicator-result
  ([indicator-type pane series]
   {:type indicator-type
    :pane pane
    :series series})
  ([indicator-type pane series markers]
   (cond-> {:type indicator-type
            :pane pane
            :series series}
     (seq markers) (assoc :markers markers))))

(defn- streak-values
  [close-values]
  (let [size (count close-values)]
    (loop [idx 0
           prev-close nil
           prev-streak 0
           out []]
      (if (= idx size)
        out
        (let [close (nth close-values idx)
              streak (if (nil? prev-close)
                       0
                       (cond
                         (> close prev-close) (if (pos? prev-streak) (inc prev-streak) 1)
                         (< close prev-close) (if (neg? prev-streak) (dec prev-streak) -1)
                         :else 0))]
          (recur (inc idx)
                 close
                 streak
                 (conj out streak)))))))

(defn- rsi-values
  [values period]
  (let [size (count values)
        diffs (mapv (fn [idx]
                      (if (pos? idx)
                        (- (nth values idx) (nth values (dec idx)))
                        nil))
                    (range size))
        gains (mapv (fn [d]
                      (when (finite-number? d)
                        (max d 0)))
                    diffs)
        losses (mapv (fn [d]
                       (when (finite-number? d)
                         (max (- d) 0)))
                     diffs)
        avg-gains (rma-values gains period)
        avg-losses (rma-values losses period)]
    (mapv (fn [g l]
            (when (and (finite-number? g)
                       (finite-number? l))
              (if (zero? l)
                100
                (- 100 (/ 100 (+ 1 (/ g l)))))))
          avg-gains avg-losses)))

(defn- percent-rank-values
  [values period]
  (let [size (count values)]
    (mapv (fn [idx]
            (when-let [window (window-for-index values idx period)]
              (let [current (last window)]
                (when (finite-number? current)
                  (let [comparable (filter finite-number? window)
                        below (count (filter #(< % current) comparable))
                        denom (max 1 (count comparable))]
                    (* 100 (/ below denom)))))))
          (range size))))

(defn- calculate-chaikin-volatility
  [data params]
  (let [period (parse-period (:period params) 10 2 200)
        roc-period (parse-period (:roc-period params) period 1 200)
        ranges (mapv - (highs data) (lows data))
        ema-range (ema-values ranges period)
        size (count data)
        values (mapv (fn [idx]
                       (if (< idx roc-period)
                         nil
                         (let [current (nth ema-range idx)
                               base (nth ema-range (- idx roc-period))]
                           (when (and (finite-number? current)
                                      (finite-number? base)
                                      (not= base 0))
                             (* 100 (/ (- current base) base))))))
                     (range size))
        time-values (times data)]
    (indicator-result :chaikin-volatility
                      :separate
                      [(line-series :chv "CHV" "#22d3ee" time-values values)])))

(defn- calculate-chande-kroll-stop
  [data params]
  (let [period (parse-period (:period params) 10 2 200)
        atr-period (parse-period (:atr-period params) period 2 200)
        multiplier (parse-number (:multiplier params) 1.0)
        high-stop-base (rolling-max (highs data) period)
        low-stop-base (rolling-min (lows data) period)
        atr-values (rma-values (true-range-values data) atr-period)
        long-stop (mapv (fn [high atr]
                          (when (and (finite-number? high)
                                     (finite-number? atr))
                            (- high (* multiplier atr))))
                        high-stop-base atr-values)
        short-stop (mapv (fn [low atr]
                           (when (and (finite-number? low)
                                      (finite-number? atr))
                             (+ low (* multiplier atr))))
                         low-stop-base atr-values)
        time-values (times data)]
    (indicator-result :chande-kroll-stop
                      :overlay
                      [(line-series :long-stop "CK Long" "#ef4444" time-values long-stop)
                       (line-series :short-stop "CK Short" "#22c55e" time-values short-stop)])))

(defn- calculate-chop-zone
  [data params]
  (let [period (parse-period (:period params) 14 2 200)
        close-values (closes data)
        ema-close (ema-values close-values period)
        atr-values (rma-values (true-range-values data) period)
        size (count data)
        zones (mapv (fn [idx]
                      (if (zero? idx)
                        nil
                        (let [ema-now (nth ema-close idx)
                              ema-prev (nth ema-close (dec idx))
                              atr-now (nth atr-values idx)]
                          (when (and (finite-number? ema-now)
                                     (finite-number? ema-prev)
                                     (finite-number? atr-now)
                                     (pos? atr-now))
                            (let [strength (* 100 (/ (- ema-now ema-prev) atr-now))]
                              (cond
                                (>= strength 35) 4
                                (>= strength 15) 3
                                (>= strength 5) 2
                                (>= strength -5) 1
                                (>= strength -15) 0
                                (>= strength -35) -1
                                :else -2))))))
                    (range size))
        time-values (times data)]
    (indicator-result :chop-zone
                      :separate
                      [(histogram-series :chop-zone "Chop Zone" time-values zones)])))

(defn- calculate-connors-rsi
  [data params]
  (let [rsi-period (parse-period (:rsi-period params) 3 2 50)
        streak-period (parse-period (:streak-period params) 2 2 50)
        rank-period (parse-period (:rank-period params) 100 2 400)
        close-values (closes data)
        streaks (streak-values close-values)
        close-rsi (rsi-values close-values rsi-period)
        streak-rsi (rsi-values (mapv #(+ 100 %) streaks) streak-period)
        roc1 (roc-percent-values close-values 1)
        rank (percent-rank-values roc1 rank-period)
        values (mapv (fn [a b c]
                       (when (and (finite-number? a)
                                  (finite-number? b)
                                  (finite-number? c))
                         (/ (+ a b c) 3)))
                     close-rsi streak-rsi rank)
        time-values (times data)]
    (indicator-result :connors-rsi
                      :separate
                      [(line-series :connors-rsi "Connors RSI" "#f97316" time-values values)])))

(defn- calculate-correlation-log
  [data params]
  (let [period (parse-period (:period params) 20 3 400)
        close-values (closes data)
        logged (mapv (fn [price]
                       (when (and (finite-number? price)
                                  (pos? price))
                         (js/Math.log price)))
                     close-values)
        values (rolling-correlation-with-time logged period)
        time-values (times data)]
    (indicator-result :correlation-log
                      :separate
                      [(line-series :correlation-log "Corr Log" "#a78bfa" time-values values)])))

(defn- calculate-klinger-oscillator
  [data params]
  (let [fast (parse-period (:fast params) 34 2 400)
        slow (parse-period (:slow params) 55 2 400)
        signal-period (parse-period (:signal params) 13 2 400)
        highs-v (highs data)
        lows-v (lows data)
        closes-v (closes data)
        volumes-v (volumes data)
        size (count data)
        typical (mapv (fn [h l c] (/ (+ h l c) 3)) highs-v lows-v closes-v)
        vf (mapv (fn [idx]
                   (if (zero? idx)
                     nil
                     (let [tp (nth typical idx)
                           prev-tp (nth typical (dec idx))
                           direction (cond
                                       (> tp prev-tp) 1
                                       (< tp prev-tp) -1
                                       :else 0)
                           dm (- (nth highs-v idx) (nth lows-v idx))
                           vol (nth volumes-v idx)]
                       (when (and (finite-number? dm)
                                  (finite-number? vol))
                         (* direction vol dm)))))
                 (range size))
        fast-ema (ema-values vf fast)
        slow-ema (ema-values vf slow)
        kvo (mapv (fn [f s]
                    (when (and (finite-number? f)
                               (finite-number? s))
                      (- f s)))
                  fast-ema slow-ema)
        signal (ema-values kvo signal-period)
        hist (mapv (fn [k s]
                     (when (and (finite-number? k)
                                (finite-number? s))
                       (- k s)))
                   kvo signal)
        time-values (times data)]
    (indicator-result :klinger-oscillator
                      :separate
                      [(histogram-series :hist "KVO Hist" time-values hist)
                       (line-series :kvo "KVO" "#22d3ee" time-values kvo)
                       (line-series :signal "Signal" "#f97316" time-values signal)])))

(defn- calculate-know-sure-thing
  [data params]
  (let [roc1 (parse-period (:roc1 params) 10 1 400)
        roc2 (parse-period (:roc2 params) 15 1 400)
        roc3 (parse-period (:roc3 params) 20 1 400)
        roc4 (parse-period (:roc4 params) 30 1 400)
        sma1 (parse-period (:sma1 params) 10 2 400)
        sma2 (parse-period (:sma2 params) 10 2 400)
        sma3 (parse-period (:sma3 params) 10 2 400)
        sma4 (parse-period (:sma4 params) 15 2 400)
        signal-period (parse-period (:signal params) 9 2 400)
        close-values (closes data)
        rcma1 (sma-values (roc-percent-values close-values roc1) sma1)
        rcma2 (sma-values (roc-percent-values close-values roc2) sma2)
        rcma3 (sma-values (roc-percent-values close-values roc3) sma3)
        rcma4 (sma-values (roc-percent-values close-values roc4) sma4)
        kst (mapv (fn [a b c d]
                    (when (every? finite-number? [a b c d])
                      (+ a (* 2 b) (* 3 c) (* 4 d))))
                  rcma1 rcma2 rcma3 rcma4)
        signal (sma-values kst signal-period)
        time-values (times data)]
    (indicator-result :know-sure-thing
                      :separate
                      [(line-series :kst "KST" "#22d3ee" time-values kst)
                       (line-series :signal "Signal" "#f97316" time-values signal)])))

(defn- calculate-volume
  [data _params]
  (let [time-values (times data)
        values (volumes data)]
    (indicator-result :volume
                      :separate
                      [(histogram-series :volume "Volume" time-values values)])))

(def ^:private wave3-calculators
  {:chaikin-volatility calculate-chaikin-volatility
   :chande-kroll-stop calculate-chande-kroll-stop
   :chop-zone calculate-chop-zone
   :connors-rsi calculate-connors-rsi
   :correlation-log calculate-correlation-log
   :klinger-oscillator calculate-klinger-oscillator
   :know-sure-thing calculate-know-sure-thing
   :volume calculate-volume
   })

(defn calculate-wave3-indicator
  [indicator-type data params]
  (let [config (or params {})
        calculator (get wave3-calculators indicator-type)]
    (when calculator
      (calculator data config))))
