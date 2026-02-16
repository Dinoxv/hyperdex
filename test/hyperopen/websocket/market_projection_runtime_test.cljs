(ns hyperopen.websocket.market-projection-runtime-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.websocket.market-projection-runtime :as market-runtime]))

(defn- apply-store-with-count
  [write-count]
  (fn [store apply-update-fn]
    (swap! write-count inc)
    (swap! store apply-update-fn)))

(deftest queue-market-projection-coalesces-multiple-keys-into-one-frame-write-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}
                       :active-assets {:contexts {}
                                       :loading true}})
          scheduled-callback (atom nil)
          schedule-count (atom 0)
          write-count (atom 0)
          apply-store! (apply-store-with-count write-count)
          schedule-animation-frame! (fn [f]
                                      (swap! schedule-count inc)
                                      (reset! scheduled-callback f)
                                      :raf-id)]
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "BTC"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (assoc-in state
                                     [:orderbooks "BTC"]
                                     {:bids [{:px "100"}]
                                      :asks [{:px "101"}]
                                      :timestamp 1}))})
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:active-asset-ctx "BTC"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (-> state
                               (assoc-in [:active-assets :contexts "BTC"] {:mark 100.5})
                               (assoc-in [:active-assets :loading] false)))})
      (is (= 1 @schedule-count))
      (is (= 0 @write-count))
      (@scheduled-callback 16)
      (is (= 1 @write-count))
      (is (= {:bids [{:px "100"}]
              :asks [{:px "101"}]
              :timestamp 1}
             (get-in @store [:orderbooks "BTC"])))
      (is (= {:mark 100.5}
             (get-in @store [:active-assets :contexts "BTC"])))
      (is (false? (get-in @store [:active-assets :loading]))))
    (finally
      (market-runtime/reset-market-projection-runtime!))))

(deftest queue-market-projection-keeps-latest-update-for-same-key-in-frame-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}})
          scheduled-callback (atom nil)
          schedule-count (atom 0)
          write-count (atom 0)
          apply-store! (apply-store-with-count write-count)
          schedule-animation-frame! (fn [f]
                                      (swap! schedule-count inc)
                                      (reset! scheduled-callback f)
                                      :raf-id)]
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "BTC"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:timestamp 1 :mark 100}))})
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "BTC"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:timestamp 2 :mark 101}))})
      (is (= 1 @schedule-count))
      (is (= 0 @write-count))
      (@scheduled-callback 16)
      (is (= 1 @write-count))
      (is (= {:timestamp 2 :mark 101}
             (get-in @store [:orderbooks "BTC"]))))
    (finally
      (market-runtime/reset-market-projection-runtime!))))

(deftest queue-market-projection-schedules-next-frame-after-prior-flush-test
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:orderbooks {}})
          scheduled-callbacks (atom [])
          write-count (atom 0)
          apply-store! (apply-store-with-count write-count)
          schedule-animation-frame! (fn [f]
                                      (swap! scheduled-callbacks conj f)
                                      (keyword (str "raf-" (count @scheduled-callbacks))))]
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "BTC"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "BTC"] {:timestamp 1}))})
      ((first @scheduled-callbacks) 16)
      (market-runtime/queue-market-projection!
       {:store store
        :coalesce-key [:orderbook "ETH"]
        :schedule-animation-frame! schedule-animation-frame!
        :apply-store! apply-store!
        :apply-update-fn (fn [state]
                           (assoc-in state [:orderbooks "ETH"] {:timestamp 2}))})
      (is (= 2 (count @scheduled-callbacks)))
      ((second @scheduled-callbacks) 32)
      (is (= 2 @write-count))
      (is (= {:timestamp 1}
             (get-in @store [:orderbooks "BTC"])))
      (is (= {:timestamp 2}
             (get-in @store [:orderbooks "ETH"]))))
    (finally
      (market-runtime/reset-market-projection-runtime!))))

