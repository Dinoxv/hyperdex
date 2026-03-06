(ns hyperopen.views.portfolio.vm.performance-helpers-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.metrics :as portfolio-metrics]
            [hyperopen.views.portfolio.vm.metrics-bridge :as vm-metrics-bridge]
            [hyperopen.views.portfolio.vm.performance :as vm-performance]))

(deftest build-metrics-request-data-derives-strategy-daily-and-benchmark-requests-test
  (let [strategy-cumulative-rows [[1 0]
                                  [2 5]]
        benchmark-cumulative-rows-by-coin {"SPY" [[1 0]
                                                  [2 3]]
                                           "QQQ" [[1 0]
                                                  [2 4]]}]
    (with-redefs [portfolio-metrics/daily-compounded-returns (fn [rows]
                                                               (mapv (fn [[time-ms value]]
                                                                       {:time-ms time-ms
                                                                        :value value})
                                                                     rows))]
      (is (= {:portfolio-request {:strategy-cumulative-rows strategy-cumulative-rows
                                  :strategy-daily-rows [{:time-ms 1 :value 0}
                                                        {:time-ms 2 :value 5}]
                                  :benchmark-cumulative-rows [[1 0]
                                                              [2 3]]}
              :benchmark-requests [{:coin "SPY"
                                    :request {:strategy-cumulative-rows [[1 0]
                                                                         [2 3]]}}
                                   {:coin "QQQ"
                                    :request {:strategy-cumulative-rows [[1 0]
                                                                         [2 4]]}}]}
             (vm-performance/build-metrics-request-data strategy-cumulative-rows
                                                        benchmark-cumulative-rows-by-coin
                                                        ["SPY" "QQQ"]))))))

(deftest performance-row-helpers-hide-suppressed-rows-and-enrich-benchmark-columns-test
  (let [groups [{:title "Returns"
                 :rows [{:key :sharpe
                         :label "Sharpe"}
                        {:key :time-in-market
                         :label "Time In Market"}]}]
        portfolio-values {:sharpe 1.5
                          :metric-status {:sharpe :ok
                                          :time-in-market :suppressed}
                          :metric-reason {:time-in-market :core-gate-failed}}
        benchmark-columns [{:coin "SPY"
                            :values {:sharpe 1.2
                                     :metric-status {:sharpe :ok}
                                     :metric-reason {}}}
                           {:coin "QQQ"
                            :values {:sharpe 0.9
                                     :metric-status {:sharpe :warning}
                                     :metric-reason {:sharpe :coverage-gate}}}]
        rows (-> groups
                 vm-performance/remove-hidden-portfolio-metric-rows
                 (vm-performance/with-performance-metric-columns portfolio-values benchmark-columns)
                 first
                 :rows)]
    (is (= [{:key :sharpe
             :label "Sharpe"
             :portfolio-value 1.5
             :portfolio-status :ok
             :portfolio-reason nil
             :benchmark-value 1.2
             :benchmark-status :ok
             :benchmark-reason nil
             :benchmark-values {"SPY" 1.2
                                "QQQ" 0.9}
             :benchmark-statuses {"SPY" :ok
                                  "QQQ" :warning}
             :benchmark-reasons {"SPY" nil
                                 "QQQ" :coverage-gate}}]
           rows))))

(deftest performance-metrics-model-skips-request-build-when-worker-signature-is-unchanged-test
  (let [strategy-cumulative-rows [[1 0]
                                  [2 11]
                                  [3 19]]
        benchmark-cumulative-rows-by-coin {"SPY" [[1 0]
                                                  [2 4]
                                                  [3 8]]
                                           "QQQ" [[1 0]
                                                  [2 2]
                                                  [3 5]]}
        selected-benchmark-coins ["SPY" "QQQ"]
        summary-time-range :month
        benchmark-context {:strategy-cumulative-rows strategy-cumulative-rows
                           :benchmark-cumulative-rows-by-coin benchmark-cumulative-rows-by-coin
                           :strategy-source-version 101
                           :benchmark-source-version-map {"SPY" 201
                                                          "QQQ" 301}}
        request-signature (vm-metrics-bridge/metrics-request-signature summary-time-range
                                                                       selected-benchmark-coins
                                                                       (:strategy-source-version benchmark-context)
                                                                       (:benchmark-source-version-map benchmark-context))
        state {:portfolio-ui {:metrics-loading? false
                              :metrics-result {:portfolio-values {:metric-status {}
                                                                  :metric-reason {}}
                                               :benchmark-values-by-coin {"SPY" {:metric-status {}
                                                                                  :metric-reason {}}
                                                                          "QQQ" {:metric-status {}
                                                                                  :metric-reason {}}}}}}
        benchmark-selector {:selected-coins selected-benchmark-coins
                            :label-by-coin {"SPY" "SPY (SPOT)"
                                            "QQQ" "QQQ (SPOT)"}}
        request-build-count (atom 0)
        request-dispatch-count (atom 0)]
    (binding [vm-performance/*metrics-worker* (delay #js {:postMessage (fn [_payload] nil)})
              vm-performance/*last-metrics-request* (atom {:signature request-signature})
              vm-performance/*build-metrics-request-data* (fn [& _]
                                                            (swap! request-build-count inc)
                                                            {})
              vm-performance/*request-metrics-computation!* (fn [& _]
                                                              (swap! request-dispatch-count inc))]
      (with-redefs [portfolio-metrics/metric-rows (fn [_]
                                                    [])]
        (let [model (vm-performance/performance-metrics-model state
                                                              summary-time-range
                                                              benchmark-selector
                                                              benchmark-context)]
          (is (= 0 @request-build-count))
          (is (= 0 @request-dispatch-count))
          (is (= ["SPY" "QQQ"] (:benchmark-coins model)))
          (is (= [{:coin "SPY" :label "SPY (SPOT)"}
                  {:coin "QQQ" :label "QQQ (SPOT)"}]
                 (:benchmark-columns model))))))))
