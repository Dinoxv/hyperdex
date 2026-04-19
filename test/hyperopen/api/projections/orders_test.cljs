(ns hyperopen.api.projections.orders-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api.projections.orders :as orders]))

(deftest order-projections-target-expected-state-paths-test
  (let [state {:orders {}}
        open-orders (orders/apply-open-orders-success state nil [{:oid 1}])
        open-orders-by-dex (orders/apply-open-orders-success state "vault" [{:oid 2}])
        open-orders-error (orders/apply-open-orders-error state (js/Error. "open-orders"))
        fills (orders/apply-user-fills-success state [{:tid 1}])
        fills-error (orders/apply-user-fills-error state (js/Error. "fills"))]
    (is (= true (get-in open-orders [:orders :open-orders-hydrated?])))
    (is (= true (get-in open-orders-by-dex [:orders :open-orders-hydrated?])))
    (is (= [{:oid 1}] (get-in open-orders [:orders :open-orders-snapshot])))
    (is (= [{:oid 2}] (get-in open-orders-by-dex [:orders :open-orders-snapshot-by-dex "vault"])))
    (is (= "Error: open-orders" (get-in open-orders-error [:orders :open-error])))
    (is (= :unexpected (get-in open-orders-error [:orders :open-error-category])))
    (is (= [{:tid 1}] (get-in fills [:orders :fills])))
    (is (= "Error: fills" (get-in fills-error [:orders :fills-error])))
    (is (= :unexpected (get-in fills-error [:orders :fills-error-category])))))
