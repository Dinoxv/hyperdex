(ns hyperopen.funding.predictability-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.predictability :as predictability]))

(def ^:private day-ms
  predictability/day-ms)

(defn- approx=
  [left right epsilon]
  (<= (js/Math.abs (- left right))
      epsilon))

(deftest compute-30d-summary-derives-mean-volatility-and-lag-autocorrelations-test
  (let [base-ms (* 20000 day-ms)
        rows (vec
              (mapcat (fn [day]
                        [{:time-ms (+ base-ms (* day day-ms))
                          :funding-rate-raw (/ day 10000)}
                         {:time-ms (+ base-ms (* day day-ms) (* 6 60 60 1000))
                          :funding-rate-raw (/ (+ day 0.5) 10000)}])
                      (range 30)))
        now-ms (+ base-ms (* 29 day-ms) (* 12 60 60 1000))
        summary (predictability/compute-30d-summary rows now-ms)]
    (is (= 60 (:sample-count summary)))
    (is (= 30 (:daily-count summary)))
    (is (number? (:mean summary)))
    (is (number? (:stddev summary)))
    (is (approx= 1 (get-in summary [:autocorrelation :lag-1d :value]) 1e-4))
    (is (approx= 1 (get-in summary [:autocorrelation :lag-5d :value]) 1e-4))
    (is (approx= 1 (get-in summary [:autocorrelation :lag-15d :value]) 1e-3))
    (is (= false (get-in summary [:autocorrelation :lag-15d :insufficient?])))))

(deftest compute-30d-summary-flags-insufficient-data-for-long-lags-test
  (let [base-ms (* 20000 day-ms)
        rows (vec
              (map (fn [day]
                     {:time-ms (+ base-ms (* day day-ms))
                      :funding-rate-raw (/ day 10000)})
                   (range 6)))
        now-ms (+ base-ms (* 5 day-ms))
        summary (predictability/compute-30d-summary rows now-ms)]
    (is (= 6 (:daily-count summary)))
    (is (= false (get-in summary [:autocorrelation :lag-1d :insufficient?])))
    (is (= false (get-in summary [:autocorrelation :lag-5d :insufficient?])))
    (is (= true (get-in summary [:autocorrelation :lag-5d :undefined?])))
    (is (= true (get-in summary [:autocorrelation :lag-15d :insufficient?])))
    (is (= nil (get-in summary [:autocorrelation :lag-15d :value])))
    (is (= 16 (get-in summary [:autocorrelation :lag-15d :minimum-daily-count])))))

(deftest compute-30d-summary-ignores-invalid-and-outside-window-rows-test
  (let [base-ms (* 20000 day-ms)
        now-ms (+ base-ms (* 29 day-ms))
        rows [{:time-ms (+ base-ms (* 2 day-ms))
               :funding-rate-raw 0.001}
              {:time-ms (+ base-ms (* 3 day-ms))
               :fundingRate "0.002"}
              {:time-ms (+ base-ms (* 4 day-ms))
               :funding-rate "0.003"}
              {:time-ms (- base-ms (* 45 day-ms))
               :funding-rate-raw 0.5}
              {:time-ms (+ base-ms (* 5 day-ms))
               :funding-rate-raw "not-a-number"}
              {:time-ms "invalid"
               :funding-rate-raw 0.01}
              {:funding-rate-raw 0.01}]
        summary (predictability/compute-30d-summary rows now-ms)]
    (is (= 3 (:sample-count summary)))
    (is (approx= 0.002 (:mean summary) 1e-9))
    (is (number? (:stddev summary)))))
