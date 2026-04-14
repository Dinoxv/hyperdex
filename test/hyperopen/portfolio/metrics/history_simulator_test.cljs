(ns hyperopen.portfolio.metrics.history-simulator-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.formal.portfolio-returns-estimator-vectors :as vectors]
            [hyperopen.portfolio.metrics :as metrics]
            [hyperopen.portfolio.metrics.test-utils :refer [approx=]]
            [hyperopen.schema.portfolio-returns-contracts :as contracts]))

(def ^:private epsilon 1e-9)
(def ^:private wipeout-threshold -99.9999)

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

(deftest simulator-cases-preserve-committed-safety-and-cadence-behavior-test
  (doseq [{:keys [id observed-summary expected] :as entry} vectors/returns-simulator-vectors]
    (testing (name id)
      (contracts/assert-simulator-vector! entry {:vector id})
      (let [actual (metrics/returns-history-rows-from-summary observed-summary)
            projection (contracts/assert-runtime-cumulative-projection!
                        (contracts/runtime-cumulative-rows-projection actual)
                        {:vector id})
            expected-rows (-> (:estimator-rows expected)
                              (contracts/assert-generated-cumulative-rows! {:vector id :phase :expected-estimator-rows})
                              contracts/generated-cumulative-rows->number-projection)
            expected-final (contracts/ratio->number (:estimator-final-percent expected))
            latent-final (contracts/ratio->number (:latent-window-final-percent expected))
            actual-final (contracts/final-percent projection)
            final-error-bps (contracts/final-error-bps actual-final latent-final)]
        (assert-cumulative-rows= expected-rows projection {:vector id})
        (is (approx= expected-final actual-final epsilon)
            (str "estimator final percent mismatch " (pr-str {:vector id
                                                              :expected expected-final
                                                              :actual actual-final})))
        (if (:exact? expected)
          (is (approx= latent-final actual-final epsilon)
              (str "exact latent comparison mismatch " (pr-str {:vector id
                                                                :latent latent-final
                                                                :actual actual-final})))
          (is (<= final-error-bps
                  (+ (:max-final-error-bps expected) epsilon))
              (str "cadence error budget exceeded " (pr-str {:vector id
                                                             :actual-error-bps final-error-bps
                                                             :max-final-error-bps (:max-final-error-bps expected)}))))
        (if (:first-row-zero? expected)
          (if (seq projection)
            (is (approx= 0 (:percent (first projection)) epsilon)
                (str "first-row-zero invariant failed " (pr-str {:vector id
                                                                 :first-row (first projection)})))
            (is true))
          (is true))
        (if (:avoid-false-wipeout? expected)
          (is (> actual-final wipeout-threshold)
              (str "false wipeout regression " (pr-str {:vector id
                                                        :actual-final actual-final
                                                        :threshold wipeout-threshold})))
          (is true))))))
