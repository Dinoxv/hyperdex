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
