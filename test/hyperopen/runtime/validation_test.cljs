(ns hyperopen.runtime.validation-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.validation :as validation]
            [hyperopen.system :as system]))

(deftest wrap-action-handler-rejects-invalid-payload-arity-test
  (with-redefs [validation/validation-enabled? (constantly true)]
    (let [wrapped (validation/wrap-action-handler :actions/select-asset
                                                  (fn [_state _coin]
                                                    []))]
      (is (thrown-with-msg?
           js/Error
           #"action payload"
           (wrapped {}))))))

(deftest wrap-action-handler-rejects-invalid-emitted-effect-test
  (with-redefs [validation/validation-enabled? (constantly true)]
    (let [wrapped (validation/wrap-action-handler :actions/test-invalid-effect
                                                  (fn [_state]
                                                    [[:effects/save "not-a-path" 42]]))]
      (is (thrown-with-msg?
           js/Error
           #"effect request"
           (wrapped {}))))))

(deftest wrap-action-handler-allows-projection-before-heavy-for-covered-action-test
  (with-redefs [validation/validation-enabled? (constantly true)]
    (let [wrapped (validation/wrap-action-handler :actions/select-chart-timeframe
                                                  (fn [_state timeframe]
                                                    [[:effects/save [:chart-options :selected-timeframe] timeframe]
                                                     [:effects/fetch-candle-snapshot :interval timeframe]]))]
      (is (= [[:effects/save [:chart-options :selected-timeframe] :5m]
              [:effects/fetch-candle-snapshot :interval :5m]]
             (wrapped {} :5m))))))

(deftest wrap-action-handler-rejects-heavy-before-projection-for-covered-action-test
  (with-redefs [validation/validation-enabled? (constantly true)]
    (let [wrapped (validation/wrap-action-handler :actions/select-chart-timeframe
                                                  (fn [_state timeframe]
                                                    [[:effects/fetch-candle-snapshot :interval timeframe]
                                                     [:effects/save [:chart-options :selected-timeframe] timeframe]]))]
      (is (thrown-with-msg?
           js/Error
           #"rule=heavy-before-projection-phase"
           (wrapped {} :5m))))))

(deftest wrap-action-handler-rejects-duplicate-heavy-effects-for-covered-action-test
  (with-redefs [validation/validation-enabled? (constantly true)]
    (let [wrapped (validation/wrap-action-handler :actions/select-orderbook-price-aggregation
                                                  (fn [_state mode]
                                                    [[:effects/save [:orderbook-ui :price-aggregation-dropdown-visible?] false]
                                                     [:effects/subscribe-orderbook "BTC"]
                                                     [:effects/subscribe-orderbook (str mode)]]))]
      (is (thrown-with-msg?
           js/Error
           #"rule=duplicate-heavy-effect"
           (wrapped {} "BTC"))))))

(deftest wrap-action-handler-does-not-apply-order-contract-to-uncovered-actions-test
  (with-redefs [validation/validation-enabled? (constantly true)]
    (let [wrapped (validation/wrap-action-handler :actions/refresh-asset-markets
                                                  (fn [_state]
                                                    [[:effects/fetch-asset-selector-markets]
                                                     [:effects/save [:asset-selector :visible-dropdown] nil]]))]
      (is (= [[:effects/fetch-asset-selector-markets]
              [:effects/save [:asset-selector :visible-dropdown] nil]]
             (wrapped {}))))))

(deftest wrap-effect-handler-rejects-invalid-save-args-test
  (with-redefs [validation/validation-enabled? (constantly true)]
    (let [wrapped (validation/wrap-effect-handler :effects/save
                                                  (fn [_ctx _store _path _value]
                                                    nil))]
      (is (thrown-with-msg?
           js/Error
           #"effect request"
           (wrapped nil (atom {}) "not-a-path" 1))))))

(deftest install-store-state-validation-rejects-invalid-transition-test
  (with-redefs [validation/validation-enabled? (constantly true)]
    (let [store (atom (system/default-store-state))]
      (validation/install-store-state-validation! store)
      (is (thrown-with-msg?
           js/Error
           #"app state"
           (swap! store assoc :active-market {:coin "BTC"}))))))
