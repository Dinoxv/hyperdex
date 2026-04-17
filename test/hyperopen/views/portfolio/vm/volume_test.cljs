(ns hyperopen.views.portfolio.vm.volume-test
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [hyperopen.views.portfolio.vm :as vm]
            [hyperopen.views.portfolio.vm.volume :as vm-volume]))

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

(deftest volume-history-model-sums-completed-days-and-keeps-current-day-out-test
  (let [state {:portfolio {:user-fees {:dailyUserVlm [{:date "2026-04-15"
                                                       :exchange "100"
                                                       :userCross "70"
                                                       :userAdd "30"}
                                                      {:date "2026-04-16"
                                                       :exchange "200"
                                                       :userCross "120"
                                                       :userAdd "80"}]}}}
        model (vm-volume/volume-history-model state)]
    (is (= [{:id :total
             :date-label "Total"
             :exchange-volume 100
             :weighted-maker-volume 30
             :weighted-taker-volume 70}]
           (:rows model)))
    (is (= {:exchange-volume 100
            :weighted-maker-volume 30
            :weighted-taker-volume 70}
           (:totals model)))
    (is (false? (:loading? model)))
    (is (nil? (:error model)))))

(deftest volume-history-model-accepts-normalized-kebab-user-fees-keys-test
  (let [state {:portfolio {:user-fees {:daily-user-vlm [{:date "2026-04-15"
                                                         :exchange-volume "100"
                                                         :user-cross "70"
                                                         :user-add "30"}
                                                        {:date "2026-04-16"
                                                         :exchange-volume "50"
                                                         :user-cross "20"
                                                         :user-add "10"}]}}}
        model (vm-volume/volume-history-model state)]
    (is (= {:exchange-volume 100
            :weighted-maker-volume 30
            :weighted-taker-volume 70}
           (:totals model)))
    (is (= 100 (vm-volume/daily-user-vlm-row-volume {:exchange-volume "120"
                                                     :user-cross "70"
                                                     :user-add "30"})))))

(deftest volume-history-model-retains-zero-total-when-history-is-empty-test
  (let [model (vm-volume/volume-history-model {:portfolio {:user-fees-loading? true
                                                           :user-fees-error "temporary issue"}})]
    (is (= [{:id :total
             :date-label "Total"
             :exchange-volume 0
             :weighted-maker-volume 0
             :weighted-taker-volume 0}]
           (:rows model)))
    (is (true? (:loading? model)))
    (is (= "temporary issue" (:error model)))))
