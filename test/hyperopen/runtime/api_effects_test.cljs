(ns hyperopen.runtime.api-effects-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.api-effects :as api-effects]))

(deftest fetch-asset-selector-markets-uses-default-and-explicit-options-test
  (let [calls (atom [])
        fetch-fn (fn [store opts]
                   (swap! calls conj [store opts]))
        store (atom {})]
    (api-effects/fetch-asset-selector-markets!
     {:store store
      :fetch-asset-selector-markets-fn fetch-fn})
    (api-effects/fetch-asset-selector-markets!
     {:store store
      :opts {:phase :bootstrap}
      :fetch-asset-selector-markets-fn fetch-fn})
    (is (= [[store {:phase :full}]
            [store {:phase :bootstrap}]]
           @calls))))

(deftest load-user-data-issues-fetches-only-when-address-present-test
  (let [open-orders-calls (atom [])
        fills-calls (atom [])
        funding-calls (atom [])
        store (atom {})]
    (api-effects/load-user-data!
     {:store store
      :address nil
      :fetch-frontend-open-orders! (fn [runtime-store address]
                                     (swap! open-orders-calls conj [runtime-store address]))
      :fetch-user-fills! (fn [runtime-store address]
                           (swap! fills-calls conj [runtime-store address]))
      :fetch-and-merge-funding-history! (fn [runtime-store address opts]
                                          (swap! funding-calls conj [runtime-store address opts]))})
    (is (empty? @open-orders-calls))
    (is (empty? @fills-calls))
    (is (empty? @funding-calls))
    (api-effects/load-user-data!
     {:store store
      :address "0xabc"
      :fetch-frontend-open-orders! (fn [runtime-store address]
                                     (swap! open-orders-calls conj [runtime-store address]))
      :fetch-user-fills! (fn [runtime-store address]
                           (swap! fills-calls conj [runtime-store address]))
      :fetch-and-merge-funding-history! (fn [runtime-store address opts]
                                          (swap! funding-calls conj [runtime-store address opts]))})
    (is (= [[store "0xabc"]] @open-orders-calls))
    (is (= [[store "0xabc"]] @fills-calls))
    (is (= [[store "0xabc" {:priority :high}]] @funding-calls))))
