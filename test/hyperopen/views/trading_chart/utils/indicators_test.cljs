(ns hyperopen.views.trading-chart.utils.indicators-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.trading-chart.utils.indicators :as indicators]))

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn- sample-candle
  [idx]
  {:time (+ 1700000000 (* idx 86400))
   :open (+ 100 (* idx 0.8))
   :high (+ 101 (* idx 0.9))
   :low (+ 99 (* idx 0.7))
   :close (+ 100.5 (* idx 0.85))
   :volume (+ 1000 (* idx 25))})

(def sample-candles
  (mapv sample-candle (range 60)))

(defn- indicator-series-by-id
  [indicator id]
  (some (fn [series]
          (when (= id (:id series))
            series))
        (:series indicator)))

(defn- last-value
  [series]
  (:value (last (:data series))))

(deftest calculate-sma-test
  (let [candles [{:time 1 :close 10}
                 {:time 2 :close 20}
                 {:time 3 :close 30}
                 {:time 4 :close 40}]
        result (vec (indicators/calculate-sma candles 2))]
    (testing "warmup entries are whitespace points"
      (is (= {:time 1} (nth result 0)))
      (is (= {:time 2} (nth result 1))))
    (testing "later entries include SMA values"
      (is (= {:time 3 :value 25} (nth result 2)))
      (is (= {:time 4 :value 35} (nth result 3))))))

(deftest available-indicators-test
  (let [available (indicators/get-available-indicators)
        ids (set (map :id available))
        expected-ids #{:week-52-high-low
                       :accelerator-oscillator
                       :accumulation-distribution
                       :accumulative-swing-index
                       :advance-decline
                       :alma
                       :aroon
                       :adx
                       :average-price
                       :atr
                       :awesome-oscillator
                       :balance-of-power
                       :bollinger-bands
                       :sma}
        by-id (into {} (map (juxt :id identity) available))]
    (is (= expected-ids ids))
    (is (= 14 (count available)))
    (is (true? (:supports-period? (get by-id :sma))))
    (is (false? (:supports-period? (get by-id :awesome-oscillator))))))

(deftest calculate-indicator-sma-shape-test
  (let [result (indicators/calculate-indicator :sma sample-candles {:period 5})
        series (first (:series result))]
    (is (= :sma (:type result)))
    (is (= :overlay (:pane result)))
    (is (= :line (:series-type series)))
    (is (= (count sample-candles) (count (:data series))))
    (is (nil? (:value (nth (:data series) 4))))
    (is (finite-number? (last-value series)))))

(deftest calculate-indicator-bollinger-bands-multi-series-test
  (let [result (indicators/calculate-indicator :bollinger-bands sample-candles {:period 5 :multiplier 2})
        upper (indicator-series-by-id result :upper)
        basis (indicator-series-by-id result :basis)
        lower (indicator-series-by-id result :lower)
        upper-last (last-value upper)
        basis-last (last-value basis)
        lower-last (last-value lower)]
    (is (= :overlay (:pane result)))
    (is (= 3 (count (:series result))))
    (is (every? some? [upper basis lower]))
    (is (finite-number? upper-last))
    (is (finite-number? basis-last))
    (is (finite-number? lower-last))
    (is (> upper-last basis-last))
    (is (> basis-last lower-last))))

(deftest calculate-indicator-oscillator-histogram-test
  (doseq [indicator-id [:awesome-oscillator :accelerator-oscillator]]
    (let [result (indicators/calculate-indicator indicator-id sample-candles {})
          series (first (:series result))]
      (is (= :separate (:pane result)))
      (is (= :histogram (:series-type series)))
      (is (= (count sample-candles) (count (:data series))))
      (is (finite-number? (last-value series))))))

(deftest calculate-indicator-aroon-lines-test
  (let [result (indicators/calculate-indicator :aroon sample-candles {:period 5})
        up (indicator-series-by-id result :aroon-up)
        down (indicator-series-by-id result :aroon-down)
        up-values (filter finite-number? (map :value (:data up)))
        down-values (filter finite-number? (map :value (:data down)))]
    (is (= :separate (:pane result)))
    (is (= 2 (count (:series result))))
    (is (seq up-values))
    (is (seq down-values))
    (is (every? #(<= 0 % 100) up-values))
    (is (every? #(<= 0 % 100) down-values))))

(deftest calculate-indicator-52-week-high-low-and-average-price-test
  (let [week-result (indicators/calculate-indicator :week-52-high-low sample-candles {:period 1})
        high-series (indicator-series-by-id week-result :high)
        low-series (indicator-series-by-id week-result :low)
        highs (mapv :high sample-candles)
        lows (mapv :low sample-candles)
        expected-last-high (apply max (subvec highs (- (count highs) 8) (count highs)))
        expected-last-low (apply min (subvec lows (- (count lows) 8) (count lows)))
        average-price-result (indicators/calculate-indicator :average-price sample-candles {})
        average-series (first (:series average-price-result))
        first-candle (first sample-candles)
        expected-first-average (/ (+ (:open first-candle)
                                     (:high first-candle)
                                     (:low first-candle)
                                     (:close first-candle))
                                  4)]
    (is (= expected-last-high (last-value high-series)))
    (is (= expected-last-low (last-value low-series)))
    (is (= expected-first-average (:value (first (:data average-series)))))))

(deftest calculate-indicator-advance-decline-proxy-test
  (let [result (indicators/calculate-indicator :advance-decline sample-candles {})
        series (first (:series result))]
    (is (= 59 (last-value series)))))

(deftest calculate-indicator-test
  (is (nil? (indicators/calculate-indicator :unknown sample-candles {}))))
