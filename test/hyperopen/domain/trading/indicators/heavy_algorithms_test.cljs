(ns hyperopen.domain.trading.indicators.heavy-algorithms-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.domain.trading.indicators.structure :as structure]
            [hyperopen.domain.trading.indicators.volatility :as volatility]))

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn- synthetic-close-values
  [n]
  (mapv (fn [idx]
          (+ 100
             (* 0.07 idx)
             (* 2.5 (js/Math.sin (/ idx 7)))
             (* 1.1 (js/Math.cos (/ idx 13)))))
        (range n)))

(deftest tie-aware-ranks-determinism-and-performance-test
  (let [values (synthetic-close-values 2500)
        tie-aware-ranks (deref #'structure/tie-aware-ranks)
        start-ms (js/Date.now)
        result-a (tie-aware-ranks values)
        result-b (tie-aware-ranks values)
        elapsed-ms (- (js/Date.now) start-ms)]
    (is (= result-a result-b))
    (is (= (count values) (count result-a)))
    (is (every? finite-number? result-a))
    (is (< elapsed-ms 8000)
        (str "tie-aware-ranks took too long: " elapsed-ms "ms"))))

(deftest zigzag-pivots-determinism-and-performance-test
  (let [close-values (synthetic-close-values 4000)
        zigzag-pivots (deref #'structure/zigzag-pivots)
        start-ms (js/Date.now)
        pivots-a (zigzag-pivots close-values 0.03)
        pivots-b (zigzag-pivots close-values 0.03)
        elapsed-ms (- (js/Date.now) start-ms)]
    (is (= pivots-a pivots-b))
    (is (seq pivots-a))
    (is (every? map? pivots-a))
    (is (every? #(contains? % :idx) pivots-a))
    (is (every? #(contains? % :price) pivots-a))
    (is (< elapsed-ms 8000)
        (str "zigzag-pivots took too long: " elapsed-ms "ms"))))

(deftest rolling-regression-determinism-and-performance-test
  (let [close-values (synthetic-close-values 3000)
        rs-rolling (deref #'volatility/rs-rolling)
        start-ms (js/Date.now)
        regressions-a (rs-rolling close-values 30)
        regressions-b (rs-rolling close-values 30)
        elapsed-ms (- (js/Date.now) start-ms)
        realized (filter some? regressions-a)]
    (is (= regressions-a regressions-b))
    (is (seq realized))
    (is (every? #(contains? % :slope) realized))
    (is (every? #(contains? % :intercept) realized))
    (is (every? #(contains? % :standard-error) realized))
    (is (every? #(contains? % :center) realized))
    (is (every? finite-number? (map :slope realized)))
    (is (< elapsed-ms 8000)
        (str "rs-rolling took too long: " elapsed-ms "ms"))))
