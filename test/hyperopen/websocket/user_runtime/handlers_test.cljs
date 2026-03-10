(ns hyperopen.websocket.user-runtime.handlers-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.websocket.user-runtime.handlers :as handlers]))

(def ^:private address
  "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")

(deftest open-orders-handler-stores-payload-and-marks-surface-hydrated-test
  (let [store (atom {:wallet {:address address}
                     :orders {:open-orders []
                              :open-orders-hydrated? false}})
        handle-open-orders! (handlers/open-orders-handler store)
        payload {:user address
                 :openOrders [{:coin "SOL"
                               :oid 11}]}]
    (handle-open-orders! {:channel "openOrders"
                          :data payload})
    (is (= payload
           (get-in @store [:orders :open-orders])))
    (is (= true
           (get-in @store [:orders :open-orders-hydrated?])))))

(deftest open-orders-handler-ignores-payloads-for-stale-addresses-test
  (let [store (atom {:wallet {:address address}
                     :orders {:open-orders []
                              :open-orders-hydrated? false}})
        handle-open-orders! (handlers/open-orders-handler store)]
    (handle-open-orders! {:channel "openOrders"
                          :data {:user "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                                 :openOrders [{:coin "BTC"
                                               :oid 22}]}})
    (is (= []
           (get-in @store [:orders :open-orders])))
    (is (= false
           (get-in @store [:orders :open-orders-hydrated?])))))
