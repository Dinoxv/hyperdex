(ns hyperopen.state.trading-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.state.trading :as trading]))

(deftest validate-order-form-test
  (testing "size is required"
    (is (seq (trading/validate-order-form (trading/default-order-form)))))

  (testing "limit order requires price"
    (let [form (assoc (trading/default-order-form)
                      :size "1"
                      :type :limit
                      :price "")]
      (is (seq (trading/validate-order-form form)))))

  (testing "market order does not require price"
    (let [form (assoc (trading/default-order-form)
                      :size "1"
                      :type :market
                      :price "")]
      (is (empty? (trading/validate-order-form form)))))

  (testing "twap requires minutes"
    (let [form (assoc (trading/default-order-form)
                      :size "1"
                      :type :twap
                      :twap {:minutes 0 :randomize true})]
      (is (seq (trading/validate-order-form form))))))
