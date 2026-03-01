(ns hyperopen.vaults.detail.performance-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.vaults.detail.performance :as performance]))

(deftest snapshot-value-by-range-normalizes-monthly-snapshot-values-test
  (let [row {:snapshot-by-key {:month [0.1 0.2]
                               :all-time [0.5]}}]
    (is (= 20 (performance/snapshot-value-by-range row :month 200)))
    (is (= 50 (performance/snapshot-value-by-range row :all-time 200)))
    (is (nil? (performance/snapshot-value-by-range row :week 200)))))

(deftest portfolio-summary-derives-window-from-all-time-when-slice-missing-test
  (let [end-time-ms 1738306800000
        old-time-ms 1702006800000
        inside-window-start-ms 1712386800000
        details {:portfolio {:all-time {:accountValueHistory [[old-time-ms 100]
                                                              [inside-window-start-ms 120]
                                                              [end-time-ms 150]]
                                        :pnlHistory [[old-time-ms 0]
                                                     [inside-window-start-ms 10]
                                                     [end-time-ms 20]]}}}
        summary (performance/portfolio-summary details :one-year)]
    (is (= [inside-window-start-ms end-time-ms]
           (mapv first (:accountValueHistory summary))))
    (is (= [0 10]
           (mapv second (:pnlHistory summary))))))

(deftest performance-metrics-model-builds-benchmark-columns-test
  (let [selector {:selected-coins ["BTC"]
                  :label-by-coin {"BTC" "BTC (HL PERP)"}}
        model (performance/performance-metrics-model
               selector
               [[1 0] [2 10] [3 20]]
               {"BTC" [[1 0] [2 5] [3 7]]})]
    (is (true? (:benchmark-selected? model)))
    (is (= ["BTC"] (:benchmark-coins model)))
    (is (= "BTC (HL PERP)" (:benchmark-label model)))
    (is (seq (:groups model)))))
