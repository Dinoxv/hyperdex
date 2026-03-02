(ns hyperopen.views.autocorrelation-plot-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.autocorrelation-plot :as autocorrelation-plot]))

(defn- collect-strings
  [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings node)
    (seq? node) (mapcat collect-strings node)
    :else []))

(defn- collect-bar-attrs
  [node]
  (letfn [(walk [n]
            (cond
              (vector? n)
              (let [tag (first n)
                    attrs (when (map? (second n)) (second n))
                    children (if attrs (drop 2 n) (drop 1 n))
                    child-results (mapcat walk children)]
                (if (and (= :rect tag)
                         (contains? attrs :data-lag))
                  (cons attrs child-results)
                  child-results))

              (seq? n)
              (mapcat walk n)

              :else
              []))]
    (vec (walk node))))

(deftest autocorrelation-plot-renders-title-axis-and-bars-test
  (let [series [{:lag-days 1 :value 0.62}
                {:lag-days 2 :value -0.18}
                {:lag-days 3 :value nil :undefined? true}
                {:lag-days 4 :value 0.02}
                {:lag-days 5 :value 0.35}]
        node (autocorrelation-plot/autocorrelation-plot series)
        strings (set (collect-strings node))
        bars (collect-bar-attrs node)]
    (is (contains? strings "Autocorrelation (30d Daily Lags)"))
    (is (contains? strings "Lag (days)"))
    (is (contains? strings "+1"))
    (is (contains? strings "0"))
    (is (contains? strings "-1"))
    (is (= 5 (count bars)))
    (is (= [1 2 3 4 5]
           (mapv :data-lag bars)))))

(deftest autocorrelation-plot-sorts-and-clamps-series-values-test
  (let [series [{:lag-days 3 :value 2}
                {:lag-days 1 :value -2}
                {:lag-days 2 :value 0.5}]
        node (autocorrelation-plot/autocorrelation-plot series)
        bars (collect-bar-attrs node)
        values (set (map :data-autocorrelation-value bars))]
    (is (= [1 2 3]
           (mapv :data-lag bars)))
    (is (contains? values "-1"))
    (is (contains? values "0.5"))
    (is (contains? values "1"))))

