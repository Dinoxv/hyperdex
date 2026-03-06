(ns hyperopen.views.portfolio.vm.metrics-bridge-helpers-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.portfolio.metrics :as portfolio-metrics]
            [hyperopen.system :as system]
            [hyperopen.views.portfolio.vm.metrics-bridge :as vm-metrics-bridge]))

(defn- approx=
  [a b]
  (< (js/Math.abs (- a b)) 1e-9))

(use-fixtures :each
  (fn [f]
    (reset! vm-metrics-bridge/last-metrics-request nil)
    (f)
    (reset! vm-metrics-bridge/last-metrics-request nil)))

(deftest worker-result-normalization-covers-nil-js-and-nested-status-maps-test
  (is (nil? (vm-metrics-bridge/normalize-worker-metric-values nil)))
  (is (= {:cagr 1
          :sharpe 2
          :metric-status {}
          :metric-reason {}}
         (vm-metrics-bridge/normalize-worker-metric-values #js {:cagr 1 :sharpe 2})))
  (is (= {:metric-status {:cagr :suppressed}
          :metric-reason {:cagr :core-gate-failed}}
         (vm-metrics-bridge/normalize-worker-metric-values
          #js {:metric-status #js {:cagr "suppressed"}
               :metric-reason #js {:cagr "core-gate-failed"}})))
  (is (= {:portfolio-values {:metric-status {:time-in-market :ok}
                             :metric-reason {}}
          :benchmark-values-by-coin {"SPY" {:metric-status {}
                                            :metric-reason {:r2 :benchmark-coverage-gate-failed}}}}
         (vm-metrics-bridge/normalize-worker-metrics-result
          #js {:portfolio-values #js {:metric-status #js {:time-in-market "ok"}}
               :benchmark-values-by-coin #js {"SPY" #js {:metric-reason #js {:r2 "benchmark-coverage-gate-failed"}}}}))))

(deftest request-metrics-computation-dedupes-and-posts-through-worker-test
  (let [posted-message (atom nil)
        store (atom {:portfolio-ui {}})
        signature-a {:summary-time-range :month
                     :selected-benchmark-coins ["SPY"]
                     :strategy-source-version 101
                     :benchmark-source-versions [["SPY" 201]]}
        signature-b (assoc signature-a :strategy-source-version 102)
        fake-worker #js {}]
    (set! (.-postMessage fake-worker)
          (fn [message]
            (reset! posted-message (js->clj message :keywordize-keys true))))
    (with-redefs [system/store store
                  vm-metrics-bridge/metrics-worker fake-worker]
      (vm-metrics-bridge/request-metrics-computation! {:seed 1} signature-a)
      (is (= {:type "compute-metrics"
              :payload {:seed 1}}
             @posted-message))
      (is (= signature-a (:signature @vm-metrics-bridge/last-metrics-request)))
      (is (true? (get-in @store [:portfolio-ui :metrics-loading?])))
      (reset! posted-message nil)
      (vm-metrics-bridge/request-metrics-computation! {:seed 2} signature-a)
      (is (nil? @posted-message))
      (vm-metrics-bridge/request-metrics-computation! {:seed 3} signature-b)
      (is (= {:type "compute-metrics"
              :payload {:seed 3}}
             @posted-message))
      (is (= signature-b (:signature @vm-metrics-bridge/last-metrics-request))))))

(deftest request-metrics-computation-keeps-existing-metrics-visible-test
  (let [store (atom {:portfolio-ui {:metrics-loading? false
                                    :metrics-result {:portfolio-values {:metric-status {}
                                                                        :metric-reason {}}}}})]
    (with-redefs [system/store store
                  vm-metrics-bridge/metrics-worker nil]
      (vm-metrics-bridge/request-metrics-computation! {:seed 7}
                                                      {:summary-time-range :month
                                                       :selected-benchmark-coins []
                                                       :strategy-source-version 1
                                                       :benchmark-source-versions []})
      (is (false? (get-in @store [:portfolio-ui :metrics-loading?]))))))

(deftest metrics-request-and-sync-helpers-cover-signature-and-row-defaults-test
  (let [signature-a (vm-metrics-bridge/metrics-request-signature :month
                                                                 ["SPY" "QQQ"]
                                                                 101
                                                                 {"SPY" 201
                                                                  "QQQ" 301})
        signature-b (vm-metrics-bridge/metrics-request-signature :week
                                                                 ["SPY" "QQQ"]
                                                                 101
                                                                 {"SPY" 201
                                                                  "QQQ" 301})]
    (is (= :month (:summary-time-range signature-a)))
    (is (= ["SPY" "QQQ"] (:selected-benchmark-coins signature-a)))
    (is (= [["SPY" 201] ["QQQ" 301]]
           (:benchmark-source-versions signature-a)))
    (is (not= signature-a signature-b)))
  (is (= [{:time-ms 1}]
         (vm-metrics-bridge/request-benchmark-daily-rows {:benchmark-daily-rows [{:time-ms 1}]})))
  (is (= [[1 5]]
         (vm-metrics-bridge/request-strategy-daily-rows {:strategy-daily-rows [[1 5]]})))
  (with-redefs [portfolio-metrics/daily-compounded-returns (fn [rows]
                                                             (mapv (fn [[time-ms value]]
                                                                     {:time-ms time-ms :value value})
                                                                   rows))
                portfolio-metrics/compute-performance-metrics (fn [{:keys [strategy-daily-rows benchmark-daily-rows]}]
                                                                {:strategy-count (count strategy-daily-rows)
                                                                 :benchmark-count (count benchmark-daily-rows)
                                                                 :metric-status {}
                                                                 :metric-reason {}})]
    (is (= {:portfolio-values {:strategy-count 2
                               :benchmark-count 2
                               :metric-status {}
                               :metric-reason {}}
            :benchmark-values-by-coin {"SPY" {:strategy-count 2
                                              :benchmark-count 0
                                              :metric-status {}
                                              :metric-reason {}}}}
           (vm-metrics-bridge/compute-metrics-sync
            {:portfolio-request {:strategy-cumulative-rows [[1 0] [2 5]]
                                 :strategy-daily-rows [{:time-ms 1 :value 0} {:time-ms 2 :value 5}]
                                 :benchmark-cumulative-rows [[1 0] [2 3]]}
             :benchmark-requests [{:coin "SPY"
                                   :request {:strategy-cumulative-rows [[1 0] [2 3]]}}]})))))

(deftest vault-snapshot-and-alignment-helpers-cover-branches-test
  (is (= ["1d" "7d" "30d"]
         (vm-metrics-bridge/vault-snapshot-range-keys)))
  (is (= 2 (vm-metrics-bridge/vault-snapshot-point-value [1 "2"])))
  (is (= 3 (vm-metrics-bridge/vault-snapshot-point-value {:value "3"})))
  (is (nil? (vm-metrics-bridge/vault-snapshot-point-value nil)))
  (is (= 4
         (vm-metrics-bridge/normalize-vault-snapshot-return "1d" {:returns {"1d" 4}})))
  (is (nil?
       (vm-metrics-bridge/normalize-vault-snapshot-return "7d" {:returns {"7d" js/Infinity}})))
  (is (= {"1d" 1 "7d" nil "30d" 3}
         (vm-metrics-bridge/vault-benchmark-snapshot-values
          {:returns {"1d" 1 "30d" 3}})))
  (let [aligned (vm-metrics-bridge/aligned-vault-return-rows
                 {:history [[1 100]
                            [2 110]
                            [3 121]]}
                 [{:time-ms 2}
                  {:time-ms 3}
                  {:time-ms 4}])]
    (is (= [2 3] (mapv :time-ms aligned)))
    (is (approx= 0 (get-in aligned [0 :value])))
    (is (approx= 10 (get-in aligned [1 :value]))))
  (is (= []
         (vm-metrics-bridge/aligned-vault-return-rows
          {:history [[10 50]
                     [11 55]]}
          [{:time-ms 9}
           {:time-ms 10}])))
  (is (= []
         (vm-metrics-bridge/aligned-vault-return-rows
          {:history [[1 100]]}
          []))))
