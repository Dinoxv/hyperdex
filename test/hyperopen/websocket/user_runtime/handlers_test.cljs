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

(deftest twap-handlers-store-active-history-and-slice-fill-payloads-test
  (let [store (atom {:wallet {:address address}
                     :orders {:twap-states []
                              :twap-history []
                              :twap-slice-fills []}})
        handle-twap-states! (handlers/twap-states-handler store)
        handle-twap-history! (handlers/user-twap-history-handler store)
        handle-twap-fills! (handlers/user-twap-slice-fills-handler store)]
    (handle-twap-states! {:channel "twapStates"
                          :data {:user address
                                 :states [[17 {:coin "BTC"
                                               :sz "1.0"
                                               :timestamp 1700000000000}]]}})
    (is (= [[17 {:coin "BTC"
                 :sz "1.0"
                 :timestamp 1700000000000}]]
           (get-in @store [:orders :twap-states])))

    (handle-twap-history! {:channel "userTwapHistory"
                           :data {:user address
                                  :isSnapshot true
                                  :history [{:time 1700000000
                                             :status {:status "finished"}
                                             :state {:coin "BTC"
                                                     :sz "1.0"
                                                     :executedSz "1.0"
                                                     :executedNtl "100.0"
                                                     :minutes 30
                                                     :timestamp 1700000000000}}]}})
    (is (= 1
           (count (get-in @store [:orders :twap-history]))))

    (handle-twap-fills! {:channel "userTwapSliceFills"
                         :data {:user address
                                :isSnapshot true
                                :twapSliceFills [{:fill {:tid 99
                                                         :coin "BTC"
                                                         :time 1700000000000
                                                         :px "100"
                                                         :sz "0.1"}}]}})
    (is (= [{:tid 99
             :coin "BTC"
             :time 1700000000000
             :px "100"
             :sz "0.1"}]
           (get-in @store [:orders :twap-slice-fills])))))

(deftest twap-handlers-ignore-payloads-for-stale-addresses-test
  (let [store (atom {:wallet {:address address}
                     :orders {:twap-states []
                              :twap-history []
                              :twap-slice-fills []}})
        handle-twap-states! (handlers/twap-states-handler store)
        handle-twap-history! (handlers/user-twap-history-handler store)
        handle-twap-fills! (handlers/user-twap-slice-fills-handler store)
        stale-address "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"]
    (handle-twap-states! {:channel "twapStates"
                          :data {:user stale-address
                                 :states [[1 {:coin "ETH"}]]}})
    (handle-twap-history! {:channel "userTwapHistory"
                           :data {:user stale-address
                                  :history [{:state {:coin "ETH"}}]}})
    (handle-twap-fills! {:channel "userTwapSliceFills"
                         :data {:user stale-address
                                :twapSliceFills [{:fill {:tid 1 :coin "ETH"}}]}})
    (is (= [] (get-in @store [:orders :twap-states])))
    (is (= [] (get-in @store [:orders :twap-history])))
    (is (= [] (get-in @store [:orders :twap-slice-fills])))))
