(ns hyperopen.portfolio.montecarlo.engine-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.montecarlo.engine :as engine]))

(defn- approx= [a b tol]
  (< (js/Math.abs (- a b)) tol))

(defn- constant-returns [r n]
  (vec (repeat n r)))

(defn- varied-returns [n]
  (mapv (fn [i] (* 0.02 (- (js/Math.sin i) 0.05))) (range n)))

(deftest mulberry32-is-deterministic-and-in-unit-interval-test
  (let [a (engine/mulberry32 7)
        b (engine/mulberry32 7)
        xs (vec (repeatedly 200 a))
        ys (vec (repeatedly 200 b))]
    (is (= xs ys) "same seed yields the same stream")
    (is (every? (fn [x] (and (<= 0 x) (< x 1))) xs) "values lie in [0, 1)")
    (is (not= xs (vec (repeatedly 200 (engine/mulberry32 8))))
        "a different seed yields a different stream")))

(deftest percentile-interpolates-test
  (let [s (vec (range 0 11))]
    (is (= 0 (engine/percentile s 0)))
    (is (= 10 (engine/percentile s 100)))
    (is (= 5 (engine/percentile s 50)))
    (is (approx= 2.5 (engine/percentile s 25) 1e-12)))
  (is (= 0 (engine/percentile [] 50)) "empty collection is safe"))

(deftest histogram-counts-sum-to-n-test
  (let [h (engine/histogram [0 1 2 3 4 5 6 7 8 9] 5)]
    (is (= 5 (count (:counts h))))
    (is (= 10 (reduce + (:counts h))) "every value falls in exactly one bin")
    (is (= 0 (:min h)))
    (is (= 9 (:max h))))
  (let [h (engine/histogram [] 8)]
    (is (= 8 (count (:counts h))))
    (is (= 0 (reduce + (:counts h))) "empty input is safe")))

(deftest run-is-deterministic-by-seed-test
  (let [returns (varied-returns 250)
        opts {:returns returns :sims 500 :horizon 90 :seed 123 :start-equity 1000}
        a (engine/run opts)
        b (engine/run opts)]
    (is (= (:band a) (:band b)) "identical inputs produce identical bands")
    (is (= (:goal-prob a) (:goal-prob b)))
    (is (= (:bust-prob a) (:bust-prob b)))
    (is (= (get-in a [:terminal :median]) (get-in b [:terminal :median])))))

(deftest run-with-different-seed-differs-test
  (let [returns (varied-returns 250)
        a (engine/run {:returns returns :sims 400 :horizon 60 :seed 1})
        b (engine/run {:returns returns :sims 400 :horizon 60 :seed 2})]
    (is (not= (:band a) (:band b)) "a different seed reshuffles the paths")))

(deftest constant-positive-returns-guarantee-goal-and-no-bust-test
  ;; Every bootstrap draw is identical (all returns equal), so each path is the
  ;; same monotonic climb and the terminal value is deterministic regardless of
  ;; the RNG. 1.01^90 ≈ 2.45 → +145% return, well above a +50% goal, and the
  ;; path never draws down, so it can never breach a -30% bust.
  (let [returns (constant-returns 0.01 252)
        res (engine/run {:returns returns :sims 300 :horizon 90
                         :goal 0.5 :bust -0.3 :start-equity 1})
        expected-terminal (dec (js/Math.pow 1.01 90))]
    (is (= 1 (:goal-prob res)) "all paths reach the goal")
    (is (= 0 (:bust-prob res)) "no path busts")
    (is (approx= expected-terminal (get-in res [:terminal :median]) 1e-9))
    (is (= 252 (get-in res [:meta :sample-size])))))

(deftest percentile-bands-are-monotonic-test
  (let [res (engine/run {:returns (varied-returns 252) :sims 600 :horizon 120 :seed 9})
        {:keys [p5 p25 p50 p75 p95]} (:band res)]
    (doseq [i (range (count p50))]
      (is (<= (nth p5 i) (nth p25 i) (nth p50 i) (nth p75 i) (nth p95 i))
          (str "band ordering holds at grid point " i)))
    (is (<= (get-in res [:terminal :p5])
            (get-in res [:terminal :p50])
            (get-in res [:terminal :p95]))
        "terminal distribution percentiles are ordered")))

(deftest grid-times-and-day-zero-anchor-test
  (let [res (engine/run {:returns (constant-returns 0.003 100)
                         :sims 50 :horizon 90 :start-equity 1000})]
    (is (= 0 (first (:times res))))
    (is (= 90 (peek (:times res))))
    (is (= 91 (count (:times res))))
    (is (= 1000 (first (get-in res [:band :p50]))) "day 0 anchors to start equity")))

(deftest draw-paths-are-capped-test
  (let [res (engine/run {:returns (constant-returns 0.001 50) :sims 1000 :horizon 30})]
    (is (= 220 (count (:draw-paths res))) "kept paths are capped at 220")
    (is (= 31 (alength (first (:draw-paths res)))) "each path has horizon+1 points")))

(deftest empty-returns-is-safe-test
  (let [res (engine/run {:returns [] :sims 100 :horizon 30})]
    (is (= 0 (:goal-prob res)))
    (is (= 0 (:bust-prob res)))
    (is (= 0 (get-in res [:meta :sample-size])))
    (is (= [] (:draw-paths res)))))
