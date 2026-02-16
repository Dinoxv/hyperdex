(ns hyperopen.websocket.orderbook-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.platform :as platform]
            [hyperopen.websocket.market-projection-runtime :as market-runtime]
            [hyperopen.websocket.orderbook :as orderbook]
            [hyperopen.websocket.orderbook-policy :as policy]
            [hyperopen.websocket.client :as ws-client]))

(defn- reset-orderbook-state!
  []
  (reset! orderbook/orderbook-state {:subscriptions {}
                                     :books {}}))

(deftest create-orderbook-data-handler-coalesces-burst-updates-per-frame-test
  (reset-orderbook-state!)
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}})
          store-write-count (atom 0)
          schedule-count (atom 0)
          scheduled-callback (atom nil)
          watch-key ::store-write-counter
          payload-a {:channel "l2Book"
                     :data {:coin "BTC"
                            :levels [[{:px "100" :sz "2"}]
                                     [{:px "101" :sz "3"}]]
                            :time 1}}
          payload-b {:channel "l2Book"
                     :data {:coin "BTC"
                            :levels [[{:px "99" :sz "4"}]
                                     [{:px "102" :sz "5"}]]
                            :time 2}}]
      (add-watch store watch-key
                 (fn [_ _ old-state new-state]
                   (when (not= old-state new-state)
                     (swap! store-write-count inc))))
      (with-redefs [platform/request-animation-frame! (fn [f]
                                                        (swap! schedule-count inc)
                                                        (reset! scheduled-callback f)
                                                        :raf-id)
                    policy/sort-bids identity
                    policy/sort-asks identity]
        (let [handler (orderbook/create-orderbook-data-handler store)]
          (handler payload-a)
          (handler payload-b)
          (is (= 1 @schedule-count))
          (is (= 0 @store-write-count))
          ;; Local module state still tracks latest payload immediately.
          (is (= 2 (get-in @orderbook/orderbook-state [:books "BTC" :timestamp])))
          (@scheduled-callback 16)
          (is (= 1 @store-write-count))
          (let [book (get-in @store [:orderbooks "BTC"])]
            (is (= [{:px "99" :sz "4"}] (:bids book)))
            (is (= [{:px "102" :sz "5"}] (:asks book)))
            (is (= 2 (:timestamp book)))
            (is (= [{:px "99" :sz "4" :px-num 99 :sz-num 4}]
                   (get-in book [:render :display-bids])))
            (is (= [{:px "102" :sz "5" :px-num 102 :sz-num 5}]
                   (get-in book [:render :display-asks])))
            (is (= [{:px "99" :sz "4" :px-num 99 :sz-num 4 :cum-size 4 :cum-value 396}]
                   (get-in book [:render :bids-with-totals])))
            (is (= [{:px "102" :sz "5" :px-num 102 :sz-num 5 :cum-size 5 :cum-value 510}]
                   (get-in book [:render :asks-with-totals])))
            (is (= {:px "99" :sz "4" :px-num 99 :sz-num 4}
                   (get-in book [:render :best-bid])))
            (is (= {:px "102" :sz "5" :px-num 102 :sz-num 5}
                   (get-in book [:render :best-ask]))))))
      (remove-watch store watch-key))
    (finally
      (reset-orderbook-state!)
      (market-runtime/reset-market-projection-runtime!))))

(deftest unsubscribe-orderbook-updates-local-state-atomically-test
  (reset-orderbook-state!)
  (try
    (reset! orderbook/orderbook-state {:subscriptions {"BTC" {:type "l2Book" :coin "BTC"}}
                                       :books {"BTC" {:bids [{:px "100"}]
                                                      :asks [{:px "101"}]}}})
    (let [write-count (atom 0)
          watch-key ::orderbook-write-counter]
      (add-watch orderbook/orderbook-state watch-key
                 (fn [_ _ old-state new-state]
                   (when (not= old-state new-state)
                     (swap! write-count inc))))
      (with-redefs [ws-client/send-message! (fn [_] true)]
        (orderbook/unsubscribe-orderbook! "BTC"))
      (is (= 1 @write-count))
      (is (nil? (get-in @orderbook/orderbook-state [:subscriptions "BTC"])))
      (is (nil? (get-in @orderbook/orderbook-state [:books "BTC"])))
      (remove-watch orderbook/orderbook-state watch-key))
    (finally
      (reset-orderbook-state!))))
