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
