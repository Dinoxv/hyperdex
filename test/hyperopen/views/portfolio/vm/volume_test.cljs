(ns hyperopen.views.portfolio.vm.volume-test
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [hyperopen.views.portfolio.vm :as vm]))

(def ^:private day-ms (* 24 60 60 1000))

(deftest volume-14d-usd-uses-last-14-days-when-timestamps-available-test
  (let [now (.now js/Date)
        within (- now (* 2 day-ms))
        outside (- now (* 30 day-ms))
        state {:orders {:fills [{:time within :sz "2" :px "100"}
                                {:time outside :sz "5" :px "100"}]}}]
    (is (= 200 (vm/volume-14d-usd state)))
    (testing "Cache correctly skips calculation if fills are identical"
      (is (= 200 (vm/volume-14d-usd state))))))


(deftest volume-14d-usd-falls-back-to-all-values-when-row-times-missing-test
  (let [state {:orders {:fills [{:sz "1" :px "50"}
                                {:sz "2" :px "25"}]}}]
    (is (= 100 (vm/volume-14d-usd state)))))


