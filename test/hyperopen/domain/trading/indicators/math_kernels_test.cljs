(ns hyperopen.domain.trading.indicators.math-kernels-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.domain.trading.indicators.math.patterns :as patterns]
            [hyperopen.domain.trading.indicators.math.statistics :as mstats]))

(defn- approx=
  [a b]
  (and (number? a)
       (number? b)
       (< (js/Math.abs (- a b)) 1e-9)))

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn- synthetic-close-values
  [n]
  (mapv (fn [idx]
          (+ 120
             (* 0.06 idx)
             (* 2.1 (js/Math.sin (/ idx 9)))
             (* 0.9 (js/Math.cos (/ idx 17)))))
        (range n)))

(deftest rolling-regression-parity-linear-series-test
  (let [values [10 11 12 13 14]
        result (mstats/rolling-regression values 3)]
    (is (= [nil nil] (subvec result 0 2)))
    (is (approx= 1 (:slope (nth result 2))))
    (is (approx= 10 (:intercept (nth result 2))))
    (is (approx= 12 (:center (nth result 2))))
    (is (approx= 0 (:standard-error (nth result 2))))
    (is (approx= 1 (:slope (nth result 3))))
    (is (approx= 11 (:intercept (nth result 3))))
    (is (approx= 13 (:center (nth result 3))))
    (is (approx= 0 (:standard-error (nth result 3))))
    (is (approx= 1 (:slope (nth result 4))))
    (is (approx= 12 (:intercept (nth result 4))))
    (is (approx= 14 (:center (nth result 4))))
    (is (approx= 0 (:standard-error (nth result 4))))))

(deftest rolling-correlation-parity-monotonic-series-test
  (let [ascending [2 4 6 8 10]
        descending [10 8 6 4 2]
        corr-up (mstats/rolling-correlation-with-time ascending 3)
        corr-down (mstats/rolling-correlation-with-time descending 3)]
    (is (= [nil nil] (subvec corr-up 0 2)))
    (is (= [nil nil] (subvec corr-down 0 2)))
    (is (every? #(approx= 1 %) (subvec corr-up 2 5)))
    (is (every? #(approx= -1 %) (subvec corr-down 2 5)))))

(deftest zigzag-pivots-parity-simple-reversal-test
  (let [close-values [100 111 106 94 97 121]
        pivots (patterns/zigzag-pivots close-values 0.10)
        interpolated (patterns/interpolate-zigzag (count close-values) pivots)]
    (is (= [{:idx 0 :price 100}
            {:idx 1 :price 111}
            {:idx 3 :price 94}
            {:idx 5 :price 121}]
           pivots))
    (is (= [100 111 102.5 94 107.5 121] interpolated))))

(deftest rolling-regression-micro-bench-test
  (let [values (synthetic-close-values 5000)
        start-ms (js/Date.now)
        regressions (mstats/rolling-regression values 30)
        elapsed-ms (- (js/Date.now) start-ms)
        realized (filter some? regressions)]
    (is (= 5000 (count regressions)))
    (is (seq realized))
    (is (every? finite-number? (map :slope realized)))
    (is (every? finite-number? (map :center realized)))
    (is (< elapsed-ms 8000)
        (str "rolling-regression took too long: " elapsed-ms "ms"))))

(deftest rolling-correlation-micro-bench-test
  (let [values (synthetic-close-values 5000)
        start-ms (js/Date.now)
        correlations (mstats/rolling-correlation-with-time values 40)
        elapsed-ms (- (js/Date.now) start-ms)
        realized (filter some? correlations)]
    (is (= 5000 (count correlations)))
    (is (seq realized))
    (is (every? finite-number? realized))
    (is (< elapsed-ms 8000)
        (str "rolling-correlation-with-time took too long: " elapsed-ms "ms"))))

(deftest zigzag-pivots-micro-bench-test
  (let [values (synthetic-close-values 6000)
        start-ms (js/Date.now)
        pivots (patterns/zigzag-pivots values 0.03)
        elapsed-ms (- (js/Date.now) start-ms)]
    (is (seq pivots))
    (is (every? map? pivots))
    (is (every? #(contains? % :idx) pivots))
    (is (every? #(contains? % :price) pivots))
    (is (< elapsed-ms 8000)
        (str "zigzag-pivots took too long: " elapsed-ms "ms"))))
