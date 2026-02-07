(ns hyperopen.views.trading-chart.utils.chart-options-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.trading-chart.utils.chart-options :as chart-options]))

(deftest base-chart-options-default-right-offset-test
  (let [options (chart-options/base-chart-options)]
    (testing "uses a default right-side gap in bars"
      (is (= chart-options/default-right-offset-bars
             (get-in options [:timeScale :rightOffset])))
      (is (= 4 (get-in options [:timeScale :rightOffset]))))
    (testing "retains existing time scale border color"
      (is (= "#374151" (get-in options [:timeScale :borderColor]))))))

(deftest fixed-height-chart-options-default-right-offset-test
  (let [options (chart-options/fixed-height-chart-options 400)]
    (testing "uses the same default right-side gap in fixed-height charts"
      (is (= chart-options/default-right-offset-bars
             (get-in options [:timeScale :rightOffset]))))
    (testing "retains fixed height configuration"
      (is (= 400 (:height options))))))
