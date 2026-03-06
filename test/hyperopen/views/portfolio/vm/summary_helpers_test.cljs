(ns hyperopen.views.portfolio.vm.summary-helpers-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.vm.summary :as vm-summary]))

(defn- approx=
  [left right]
  (< (js/Math.abs (- left right)) 1e-12))

(deftest summary-key-normalization-and-selection-follow-root-vm-contract-test
  (is (= :three-month (vm-summary/canonical-summary-key "3M")))
  (is (= :perp-two-year (vm-summary/canonical-summary-key "perp2Y")))
  (is (= {:month {:vlm 1}
          :perp-two-year {:vlm 2}}
         (vm-summary/normalize-summary-by-key {"month" {:vlm 1}
                                               "perp2Y" {:vlm 2}
                                               :ignored "bad"})))
  (is (= :perp-two-year (vm-summary/selected-summary-key :perps :two-year)))
  (is (= :month (vm-summary/selected-summary-key :all :month)))
  (is (= :perp-all-time (last (vm-summary/summary-key-candidates :perps :day)))))

(deftest derived-summary-and-metric-helpers-cover-all-time-fallbacks-test
  (let [t0 (.getTime (js/Date. "2024-01-01T00:00:00.000Z"))
        t1 (.getTime (js/Date. "2024-03-01T00:00:00.000Z"))
        t2 (.getTime (js/Date. "2024-04-01T00:00:00.000Z"))
        t3 (.getTime (js/Date. "2024-05-15T00:00:00.000Z"))
        t4 (.getTime (js/Date. "2024-06-30T00:00:00.000Z"))
        summary-by-key (vm-summary/normalize-summary-by-key
                        {:all-time {:pnlHistory [[t0 10] [t1 20] [t2 30] [t3 45] [t4 60]]
                                    :accountValueHistory [[t0 100] [t1 110] [t2 120] [t3 130] [t4 150]]}})
        derived (vm-summary/derived-summary-entry summary-by-key :all :three-month)
        selected (vm-summary/selected-summary-entry summary-by-key :all :three-month)
        drawdown-summary {:pnlHistory [[1 10] [2 30] [3 15]]
                          :accountValueHistory [[1 100] [2 100] [3 100]]}]
    (is (= [t2 t3 t4]
           (mapv :time-ms (:accountValueHistory derived))))
    (is (= [0 15 30]
           (mapv :value (:pnlHistory derived))))
    (is (= derived selected))
    (is (= 5 (vm-summary/pnl-delta drawdown-summary)))
    (is (approx= 0.15 (vm-summary/max-drawdown-ratio drawdown-summary)))))
