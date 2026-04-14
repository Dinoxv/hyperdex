(ns hyperopen.portfolio.metrics.history-formal-conformance-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.formal.portfolio-returns-estimator-vectors :as vectors]
            [hyperopen.portfolio.metrics :as metrics]
            [hyperopen.portfolio.metrics.test-utils :refer [approx=]]
            [hyperopen.schema.portfolio-returns-contracts :as contracts]))

(def ^:private epsilon 1e-9)

(defn- assert-cumulative-rows=
  [expected actual context]
  (is (= (count expected) (count actual))
      (str "row count mismatch " (pr-str context)))
  (doseq [[idx expected-row] (map-indexed vector expected)]
    (let [actual-row (nth actual idx)]
      (is (= (:time-ms expected-row)
             (:time-ms actual-row))
          (str "time-ms mismatch " (pr-str (assoc context :idx idx))))
      (is (approx= (:percent expected-row)
                   (:percent actual-row)
                   epsilon)
          (str "percent mismatch " (pr-str (assoc context :idx idx))
               " expected=" (pr-str expected-row)
               " actual=" (pr-str actual-row))))))

(defn- assert-interval-rows=
  [expected actual context]
  (is (= (count expected) (count actual))
      (str "row count mismatch " (pr-str context)))
  (doseq [[idx expected-row] (map-indexed vector expected)]
    (let [actual-row (nth actual idx)]
      (is (= (:time-ms expected-row)
             (:time-ms actual-row))
          (str "time-ms mismatch " (pr-str (assoc context :idx idx))))
      (is (approx= (:return expected-row)
                   (:return actual-row)
                   epsilon)
          (str "return mismatch " (pr-str (assoc context :idx idx))
               " expected=" (pr-str expected-row)
               " actual=" (pr-str actual-row))))))

(defn- assert-daily-rows=
  [expected actual context]
  (is (= (count expected) (count actual))
      (str "row count mismatch " (pr-str context)))
  (doseq [[idx expected-row] (map-indexed vector expected)]
    (let [actual-row (nth actual idx)]
      (is (= (:day expected-row)
             (:day actual-row))
          (str "day mismatch " (pr-str (assoc context :idx idx))))
      (is (= (:time-ms expected-row)
             (:time-ms actual-row))
          (str "time-ms mismatch " (pr-str (assoc context :idx idx))))
      (is (approx= (:return expected-row)
                   (:return actual-row)
                   epsilon)
          (str "return mismatch " (pr-str (assoc context :idx idx))
               " expected=" (pr-str expected-row)
               " actual=" (pr-str actual-row))))))

(deftest returns-history-rows-conform-to-committed-formal-vectors-test
  (doseq [{:keys [id summary expected] :as entry} vectors/returns-series-vectors]
    (testing (name id)
      (contracts/assert-series-vector! entry {:vector id})
      (let [actual (metrics/returns-history-rows-from-summary summary)
            projection (contracts/assert-runtime-cumulative-projection!
                        (contracts/runtime-cumulative-rows-projection actual)
                        {:vector id})
            expected* (-> expected
                          (contracts/assert-generated-cumulative-rows! {:vector id :expected true})
                          contracts/generated-cumulative-rows->number-projection)]
        (assert-cumulative-rows= expected* projection {:vector id})))))

(deftest cumulative-percent-rows->interval-returns-conforms-to-committed-formal-vectors-test
  (doseq [{:keys [id rows expected] :as entry} vectors/interval-return-vectors]
    (testing (name id)
      (contracts/assert-interval-vector! entry {:vector id})
      (let [runtime-input (contracts/generated-cumulative-rows->runtime-input rows)
            actual (metrics/cumulative-percent-rows->interval-returns runtime-input)
            projection (contracts/assert-runtime-interval-projection!
                        (contracts/runtime-interval-rows-projection actual)
                        {:vector id})
            expected* (-> expected
                          (contracts/assert-generated-interval-rows! {:vector id :expected true})
                          contracts/generated-interval-rows->number-projection)]
        (assert-interval-rows= expected* projection {:vector id})))))

(deftest daily-compounded-returns-conform-to-committed-formal-vectors-test
  (doseq [{:keys [id rows expected] :as entry} vectors/daily-compounded-vectors]
    (testing (name id)
      (contracts/assert-daily-vector! entry {:vector id})
      (let [runtime-input (contracts/generated-cumulative-rows->runtime-input rows)
            actual (metrics/daily-compounded-returns runtime-input)
            projection (contracts/assert-runtime-daily-projection!
                        (contracts/runtime-daily-rows-projection actual)
                        {:vector id})
            expected* (-> expected
                          (contracts/assert-generated-daily-rows! {:vector id :expected true})
                          contracts/generated-daily-rows->number-projection)]
        (assert-daily-rows= expected* projection {:vector id})))))
