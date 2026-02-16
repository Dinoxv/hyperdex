(ns hyperopen.websocket.active-asset-ctx-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.platform :as platform]
            [hyperopen.websocket.active-asset-ctx :as active-ctx]
            [hyperopen.websocket.client :as ws-client]
            [hyperopen.websocket.market-projection-runtime :as market-runtime]))

(defn- reset-active-asset-ctx-state!
  []
  (reset! active-ctx/active-asset-ctx-state {:subscriptions #{}
                                              :contexts {}}))

(deftest create-active-asset-data-handler-coalesces-burst-updates-per-frame-test
  (reset-active-asset-ctx-state!)
  (market-runtime/reset-market-projection-runtime!)
  (try
    (let [store (atom {:active-assets {:contexts {}
                                       :loading true}})
          store-write-count (atom 0)
          schedule-count (atom 0)
          scheduled-callback (atom nil)
          watch-key ::store-write-counter
          payload-a {:channel "activeAssetCtx"
                     :data {:coin "BTC"
                            :ctx {:markPx "100.0"
                                  :oraclePx "99.9"
                                  :prevDayPx "95.0"
                                  :funding "0.0001"
                                  :dayNtlVlm "111"
                                  :openInterest "222"}}}
          payload-b {:channel "activeAssetCtx"
                     :data {:coin "BTC"
                            :ctx {:markPx "101.5"
                                  :oraclePx "100.4"
                                  :prevDayPx "95.0"
                                  :funding "0.0002"
                                  :dayNtlVlm "333"
                                  :openInterest "444"}}}]
      (add-watch store watch-key
                 (fn [_ _ old-state new-state]
                   (when (not= old-state new-state)
                     (swap! store-write-count inc))))
      (with-redefs [platform/request-animation-frame! (fn [f]
                                                        (swap! schedule-count inc)
                                                        (reset! scheduled-callback f)
                                                        :raf-id)]
        (let [handler (active-ctx/create-active-asset-data-handler store)]
          (handler payload-a)
          (handler payload-b)
          (is (= 1 @schedule-count))
          (is (= 0 @store-write-count))
          (@scheduled-callback 16)
          (is (= 1 @store-write-count))
          (is (= false (get-in @store [:active-assets :loading])))
          (is (= 101.5 (get-in @store [:active-assets :contexts "BTC" :mark])))
          (is (= 6.5 (get-in @store [:active-assets :contexts "BTC" :change24h])))
          (is (= 0.02 (get-in @store [:active-assets :contexts "BTC" :fundingRate])))))
      (remove-watch store watch-key))
    (finally
      (reset-active-asset-ctx-state!)
      (market-runtime/reset-market-projection-runtime!))))

(deftest unsubscribe-active-asset-ctx-updates-local-state-atomically-test
  (reset-active-asset-ctx-state!)
  (try
    (reset! active-ctx/active-asset-ctx-state {:subscriptions #{"BTC"}
                                                :contexts {"BTC" {:markPx "100"}}})
    (let [write-count (atom 0)
          watch-key ::active-asset-write-counter]
      (add-watch active-ctx/active-asset-ctx-state watch-key
                 (fn [_ _ old-state new-state]
                   (when (not= old-state new-state)
                     (swap! write-count inc))))
      (with-redefs [ws-client/send-message! (fn [_] true)]
        (active-ctx/unsubscribe-active-asset-ctx! "BTC"))
      (is (= 1 @write-count))
      (is (empty? (:subscriptions @active-ctx/active-asset-ctx-state)))
      (is (nil? (get-in @active-ctx/active-asset-ctx-state [:contexts "BTC"])))
      (remove-watch active-ctx/active-asset-ctx-state watch-key))
    (finally
      (reset-active-asset-ctx-state!))))

