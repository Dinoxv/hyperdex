(ns hyperopen.portfolio.metrics-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.metrics :as metrics]))

(defn- approx=
  [left right tolerance]
  (<= (js/Math.abs (- left right)) tolerance))

(deftest returns-history-rows-adjusts-for-cashflows-test
  (let [state {:wallet {:address "0xabc"}
               :portfolio {:ledger-updates [{:time 2
                                             :hash "0xflow"
                                             :delta {:type "deposit"
                                                     :usdc "201"}}]}
               :orders {:ledger []}}
        summary {:accountValueHistory [[1 4]
                                       [2 205]
                                       [3 204]
                                       [4 205]]}
        rows (metrics/returns-history-rows state summary :all)
        values (mapv second rows)]
    (is (= [1 2 3 4]
           (mapv first rows)))
    (is (approx= 0 (nth values 0) 1e-12))
    (is (approx= 0 (nth values 1) 1e-12))
    (is (approx= -0.48780487804878053 (nth values 2) 1e-12))
    (is (approx= 0 (nth values 3) 1e-12))))

(deftest returns-history-rows-treats-account-class-transfer-as-perps-flow-test
  (let [state {:wallet {:address "0xabc"}
               :portfolio {:ledger-updates [{:time 2
                                             :hash "0xperp"
                                             :delta {:type "accountClassTransfer"
                                                     :usdc "50"
                                                     :toPerp true}}]}
               :orders {:ledger []}}
        summary {:accountValueHistory [[1 100]
                                       [2 150]
                                       [3 150]]}
        rows (metrics/returns-history-rows state summary :perps)]
    (is (= [0 0 0]
           (mapv second rows)))))

(deftest returns-history-rows-keeps-distinct-same-hash-ledger-events-test
  (let [state {:wallet {:address "0xabc"}
               :portfolio {:ledger-updates [{:time 2
                                             :hash "0xshared"
                                             :delta {:type "deposit"
                                                     :usdc "100"}}]}
               :orders {:ledger [{:time 2
                                  :hash "0xshared"
                                  :delta {:type "withdraw"
                                          :usdc "50"
                                          :fee "0"}}]}}
        summary {:accountValueHistory [[1 100]
                                       [2 200]]}
        rows (metrics/returns-history-rows state summary :all)]
    (is (= [[1 0]
            [2 50]]
           rows))))

(deftest daily-compounded-returns-builds-canonical-daily-series-test
  (let [rows [[1000 0]
              [2000 10]
              [3000 21]]
        interval-returns (metrics/cumulative-percent-rows->interval-returns rows)
        daily-returns (metrics/daily-compounded-returns rows)]
    (testing "interval return extraction"
      (is (= [2000 3000]
             (mapv :time-ms interval-returns)))
      (is (approx= 0.1 (get-in interval-returns [0 :return]) 1e-12))
      (is (approx= 0.1 (get-in interval-returns [1 :return]) 1e-12)))
    (testing "daily compounding"
      (is (= 1 (count daily-returns)))
      (is (= "1970-01-01"
             (get-in daily-returns [0 :day])))
      (is (approx= 0.21 (get-in daily-returns [0 :return]) 1e-12)))))
