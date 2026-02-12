(ns hyperopen.core-public-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.asset-selector.actions :as asset-actions]
            [hyperopen.core :as core]
            [hyperopen.order.actions :as order-actions]))

(deftest core-exposes-public-action-aliases-test
  (let [state {:active-asset "ETH"
               :asset-selector {:open? true}
               :order-form {:entry-mode :pro
                            :type :stop-market}
               :active-market {:mark-price 101.0
                               :mid-price 101.0}}
        market {:coin "BTC" :symbol "BTC"}
        select-asset-effects (asset-actions/select-asset state market)
        select-order-entry-effects (order-actions/select-order-entry-mode state :market)]
    (is (= select-asset-effects
           (core/select-asset state market)))
    (is (= select-order-entry-effects
           (core/select-order-entry-mode state :market)))
    (is (= [[:effects/fetch-asset-selector-markets]]
           (core/refresh-asset-markets state)))
    (is (= [[:effects/api-load-user-data "0xabc"]]
           (core/load-user-data state "0xabc")))
    (is (= [[:effects/save [:funding-ui :modal] :history]]
           (core/set-funding-modal state :history)))))
