(ns hyperopen.views.portfolio.optimize.frontier-chart-model-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.optimize.frontier-chart-model :as chart-model]))

(deftest chart-model-includes-current-portfolio-point-in-domains-test
  (let [result {:frontier [{:expected-return 0.04
                            :volatility 0.1}
                           {:expected-return 0.08
                            :volatility 0.2}]
                :frontier-overlays {:standalone []
                                    :contribution []}
                :expected-return 0.08
                :volatility 0.2
                :current-weights [0.4 0.1]
                :current-expected-return 0.5
                :current-volatility 0.9
                :current-performance {:in-sample-sharpe 0.56}}
        model (chart-model/chart-model {} result :none false)]
    (is (= {:expected-return 0.5
            :volatility 0.9
            :sharpe 0.56}
           (:current-point model)))
    (is (<= 0.9 (second (:x-domain model))))
    (is (<= 0.5 (second (:y-domain model))))))

(deftest chart-model-omits-current-portfolio-point-without-current-exposure-test
  (let [result {:frontier [{:expected-return 0.04
                            :volatility 0.1}
                           {:expected-return 0.08
                            :volatility 0.2}]
                :frontier-overlays {:standalone []
                                    :contribution []}
                :expected-return 0.08
                :volatility 0.2
                :current-weights [0 0]
                :current-expected-return 0.5
                :current-volatility 0.9
                :current-performance {:in-sample-sharpe 0.56}}
        model (chart-model/chart-model {} result :none false)]
    (is (nil? (:current-point model)))
    (is (< (second (:x-domain model)) 0.9))
    (is (< (second (:y-domain model)) 0.5))))

(deftest chart-model-includes-current-portfolio-point-from-outside-selected-universe-test
  (let [result {:frontier [{:expected-return 0.04
                            :volatility 0.1}
                           {:expected-return 0.08
                            :volatility 0.2}]
                :frontier-overlays {:standalone []
                                    :contribution []}
                :expected-return 0.08
                :volatility 0.2
                :current-weights [0 0]
                :current-portfolio-weights [0.25]
                :current-expected-return 0.5
                :current-volatility 0.9
                :current-performance {:in-sample-sharpe 0.56}}
        model (chart-model/chart-model {} result :none false)]
    (is (= {:expected-return 0.5
            :volatility 0.9
            :sharpe 0.56}
           (:current-point model)))
    (is (<= 0.9 (second (:x-domain model))))
    (is (<= 0.5 (second (:y-domain model))))))
