(ns hyperopen.views.portfolio.vm.metrics-bridge-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.vm.metrics-bridge :as vm-metrics-bridge]))

(deftest portfolio-vm-metrics-request-signature-captures-time-range-coins-and-source-versions-test
  (let [signature-a (vm-metrics-bridge/metrics-request-signature :month
                                                                 ["SPY" "QQQ"]
                                                                 101
                                                                 {"SPY" 201
                                                                  "QQQ" 301})
        signature-b (vm-metrics-bridge/metrics-request-signature :week
                                                                 ["SPY" "QQQ"]
                                                                 101
                                                                 {"SPY" 201
                                                                  "QQQ" 301})
        signature-c (vm-metrics-bridge/metrics-request-signature :month
                                                                 ["SPY" "IWM"]
                                                                 101
                                                                 {"SPY" 201
                                                                  "IWM" 401})
        signature-d (vm-metrics-bridge/metrics-request-signature :month
                                                                 ["SPY" "QQQ"]
                                                                 102
                                                                 {"SPY" 201
                                                                  "QQQ" 301})]
    (is (= :month (:summary-time-range signature-a)))
    (is (= ["SPY" "QQQ"] (:selected-benchmark-coins signature-a)))
    (is (= [["SPY" 201] ["QQQ" 301]]
           (:benchmark-source-versions signature-a)))
    (is (not= signature-a signature-b))
    (is (not= signature-a signature-c))
    (is (not= signature-a signature-d))))

(deftest normalize-worker-metrics-result-deserializes-nested-status-maps-test
  (let [worker-result {:portfolio-values {:cumulative-return 0.1
                                          :time-in-market 0.9
                                          :metric-status {:time-in-market "ok"
                                                          :r2 "suppressed"}
                                          :metric-reason {:r2 "benchmark-coverage-gate-failed"}}
                       :benchmark-values-by-coin {"SPY" {:metric-status {:time-in-market "ok"}}}}
        deserialized (-> worker-result
                         clj->js
                         (vm-metrics-bridge/normalize-worker-metrics-result))]
    (is (= :ok (get-in deserialized [:portfolio-values :metric-status :time-in-market])))
    (is (= :suppressed (get-in deserialized [:portfolio-values :metric-status :r2])))
    (is (= :benchmark-coverage-gate-failed (get-in deserialized [:portfolio-values :metric-reason :r2])))
    (is (contains? (:benchmark-values-by-coin deserialized) "SPY"))))
