(ns hyperopen.websocket.orderbook-policy-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.websocket.orderbook-policy :as policy]))

(deftest parse-number-test
  (is (= 42 (policy/parse-number 42)))
  (is (= 12.5 (policy/parse-number "12.5")))
  (is (nil? (policy/parse-number "abc")))
  (is (nil? (policy/parse-number nil))))

(deftest sort-levels-test
  (let [levels [{:px "100"} {:px 101} {:px "99.5"}]]
    (testing "bids sorted descending by parsed price"
      (is (= [101 "100" "99.5"]
             (mapv :px (policy/sort-bids levels)))))
    (testing "asks keep legacy descending sort for compatibility"
      (is (= [101 "100" "99.5"]
             (mapv :px (policy/sort-asks levels)))))))

(deftest normalize-levels-and-cumulative-depth-test
  (let [levels (policy/normalize-levels [{:px "101.5" :sz "2"}
                                         {:px "100.5" :sz "3"}])
        with-totals (policy/calculate-cumulative-totals levels)]
    (is (= [{:px "101.5" :sz "2" :px-num 101.5 :sz-num 2}
            {:px "100.5" :sz "3" :px-num 100.5 :sz-num 3}]
           levels))
    (is (= [{:px "101.5" :sz "2" :px-num 101.5 :sz-num 2 :cum-size 2 :cum-value 203}
            {:px "100.5" :sz "3" :px-num 100.5 :sz-num 3 :cum-size 5 :cum-value 504.5}]
           with-totals))))

(deftest build-book-test
  (let [book (policy/build-book [{:px "100" :sz "2"}
                                 {:px "99" :sz "1"}]
                                [{:px "101" :sz "4"}
                                 {:px "102" :sz "5"}]
                                1)]
    (testing "legacy keys remain sorted in compatibility order"
      (is (= [{:px "100" :sz "2"} {:px "99" :sz "1"}]
             (:bids book)))
      (is (= [{:px "102" :sz "5"} {:px "101" :sz "4"}]
             (:asks book))))
    (testing "render snapshot stores limited display and cumulative slices"
      (is (= [{:px "100" :sz "2" :px-num 100 :sz-num 2}]
             (get-in book [:render :display-bids])))
      (is (= [{:px "101" :sz "4" :px-num 101 :sz-num 4}]
             (get-in book [:render :display-asks])))
      (is (= [{:px "100" :sz "2" :px-num 100 :sz-num 2 :cum-size 2 :cum-value 200}]
             (get-in book [:render :bids-with-totals])))
      (is (= [{:px "101" :sz "4" :px-num 101 :sz-num 4 :cum-size 4 :cum-value 404}]
             (get-in book [:render :asks-with-totals])))
      (is (= {:px "100" :sz "2" :px-num 100 :sz-num 2}
             (get-in book [:render :best-bid])))
      (is (= {:px "101" :sz "4" :px-num 101 :sz-num 4}
             (get-in book [:render :best-ask]))))))

(deftest normalize-aggregation-config-test
  (is (= {:nSigFigs 4}
         (policy/normalize-aggregation-config {:nSigFigs 4})))
  (is (= {}
         (policy/normalize-aggregation-config {:nSigFigs 6})))
  (is (= {}
         (policy/normalize-aggregation-config {}))))

(deftest build-subscription-test
  (is (= {:type "l2Book" :coin "BTC" :nSigFigs 3}
         (policy/build-subscription "BTC" {:nSigFigs 3})))
  (is (= {:type "l2Book" :coin "ETH"}
         (policy/build-subscription "ETH" {:nSigFigs 8}))))
