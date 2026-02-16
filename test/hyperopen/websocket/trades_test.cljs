(ns hyperopen.websocket.trades-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.websocket.trades :as trades]
            [hyperopen.websocket.trades-policy :as policy]))

(deftest update-candles-from-trades-normalizes-filters-and-sorts-before-upsert-test
  (let [store (atom {:active-asset "BTC"
                     :chart-options {:selected-timeframe :1m}
                     :candles {"BTC" {:1m []}}})
        incoming [{:time-ms 30 :price 3 :coin "ETH"}
                  {:time-ms 10 :price 1 :coin "BTC"}
                  {:time-ms nil :price 4 :coin "BTC"}
                  {:time-ms 20 :price nil :coin "BTC"}
                  {:time-ms 15 :price 1.5 :coin nil}
                  {:time-ms 5 :price 0.5 :coin "BTC"}]]
    (with-redefs [policy/normalize-trade identity
                  policy/upsert-candle (fn [acc _ trade _]
                                         (conj (vec acc) trade))]
      (@#'trades/update-candles-from-trades! store incoming))
    (is (= [{:time-ms 5 :price 0.5 :coin "BTC"}
            {:time-ms 10 :price 1 :coin "BTC"}
            {:time-ms 15 :price 1.5 :coin nil}]
           (get-in @store [:candles "BTC" :1m])))))

(deftest update-candles-from-trades-preserves-map-entry-shape-when-updating-data-test
  (let [store (atom {:active-asset "BTC"
                     :chart-options {:selected-timeframe :1m}
                     :candles {"BTC" {:1m {:meta :keep
                                           :data []}}}})
        incoming [{:time-ms 200 :price 2 :coin "BTC"}
                  {:time-ms 100 :price 1 :coin "BTC"}]]
    (with-redefs [policy/normalize-trade identity
                  policy/upsert-candle (fn [acc _ trade _]
                                         (conj (vec acc) trade))]
      (@#'trades/update-candles-from-trades! store incoming))
    (let [entry (get-in @store [:candles "BTC" :1m])]
      (is (= :keep (:meta entry)))
      (is (= [{:time-ms 100 :price 1 :coin "BTC"}
              {:time-ms 200 :price 2 :coin "BTC"}]
             (:data entry))))))

(deftest create-trades-handler-updates-coin-indexed-display-cache-test
  (reset! trades/trades-state {:subscriptions #{}
                               :trades []
                               :trades-by-coin {}})
  (reset! trades/trades-buffer {:pending [] :timer nil})
  (let [handler (trades/create-trades-handler (atom {}))]
    (with-redefs [trades/schedule-candle-update! (fn [& _] nil)]
      (handler {:channel "trades"
                :data [{:coin "ETH" :px "3010.5" :sz "0.2" :side "B" :time 1700000001}
                       {:coin "BTC" :px "61500.1" :sz "0.03" :side "A" :time 1700000003}
                       {:coin "BTC" :px "61499.9" :sz "0.01" :side "B" :time 1700000002}]}))
    (is (= 2 (count (trades/get-recent-trades-for-coin "BTC"))))
    (is (= ["61500.1" "61499.9"]
           (mapv :price-raw (trades/get-recent-trades-for-coin "BTC"))))
    (is (= [1700000003000 1700000002000]
           (mapv :time-ms (trades/get-recent-trades-for-coin "BTC"))))
    (is (= "3010.5"
           (:price-raw (first (trades/get-recent-trades-for-coin "ETH"))))))
  (reset! trades/trades-state {:subscriptions #{}
                               :trades []
                               :trades-by-coin {}})
  (reset! trades/trades-buffer {:pending [] :timer nil}))

(deftest clear-trades-clears-coin-indexed-cache-test
  (reset! trades/trades-state {:subscriptions #{}
                               :trades [{:coin "BTC" :px "1"}]
                               :trades-by-coin {"BTC" [{:coin "BTC" :price-raw "1"}]}})
  (trades/clear-trades!)
  (is (= [] (trades/get-recent-trades)))
  (is (= [] (trades/get-recent-trades-for-coin "BTC"))))
