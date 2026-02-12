(ns hyperopen.runtime.effect-adapters-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.app-effects :as app-effects]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.websocket.subscriptions-runtime :as subscriptions-runtime]))

(deftest subscribe-active-asset-persists-through-local-storage-effect-boundary-test
  (let [persist-calls (atom [])
        store (atom {:asset-selector {:market-by-key {}}
                     :chart-options {:selected-timeframe :1d}})]
    (with-redefs [app-effects/local-storage-set!
                  (fn [key value]
                    (swap! persist-calls conj [key value]))
                  subscriptions-runtime/subscribe-active-asset!
                  (fn [{:keys [persist-active-asset!]}]
                    (persist-active-asset! "ETH"))]
      (effect-adapters/subscribe-active-asset nil store "ETH"))
    (is (= [["active-asset" "ETH"]] @persist-calls))))
