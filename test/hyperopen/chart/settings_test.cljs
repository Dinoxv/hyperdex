(ns hyperopen.chart.settings-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.chart.settings :as chart-settings]
            [hyperopen.platform :as platform]))

(defn- local-storage-get-stub
  [values]
  (fn [key]
    (get values key)))

(deftest restore-chart-options-migrates-legacy-histogram-key-test
  (let [store (atom {:chart-options {}})]
    (with-redefs [platform/local-storage-get (local-storage-get-stub {"chart-timeframe" "1h"
                                                                      "chart-type" "histogram"
                                                                      "chart-active-indicators" "{}"})]
      (chart-settings/restore-chart-options! store))
    (is (= :1h (get-in @store [:chart-options :selected-timeframe])))
    (is (= :columns (get-in @store [:chart-options :selected-chart-type])))))

(deftest restore-chart-options-accepts-wave1-chart-type-keys-test
  (let [store (atom {:chart-options {}})]
    (with-redefs [platform/local-storage-get (local-storage-get-stub {"chart-timeframe" "1d"
                                                                      "chart-type" "step-line"
                                                                      "chart-active-indicators" "{}"})]
      (chart-settings/restore-chart-options! store))
    (is (= :step-line (get-in @store [:chart-options :selected-chart-type])))))

(deftest restore-chart-options-falls-back-for-unknown-chart-type-test
  (let [store (atom {:chart-options {}})]
    (with-redefs [platform/local-storage-get (local-storage-get-stub {"chart-timeframe" "1d"
                                                                      "chart-type" "not-a-real-type"
                                                                      "chart-active-indicators" "{}"})]
      (chart-settings/restore-chart-options! store))
    (is (= :candlestick (get-in @store [:chart-options :selected-chart-type])))))
