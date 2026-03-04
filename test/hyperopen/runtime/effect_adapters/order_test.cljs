(ns hyperopen.runtime.effect-adapters.order-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.runtime.effect-adapters.order :as order-adapters]))

(deftest facade-order-adapters-delegate-to-order-module-test
  (is (identical? order-adapters/api-submit-order effect-adapters/api-submit-order))
  (is (identical? order-adapters/api-cancel-order effect-adapters/api-cancel-order))
  (is (identical? order-adapters/api-submit-position-tpsl effect-adapters/api-submit-position-tpsl))
  (is (identical? order-adapters/api-submit-position-margin effect-adapters/api-submit-position-margin))
  (is (identical? order-adapters/make-api-submit-order effect-adapters/make-api-submit-order))
  (is (identical? order-adapters/make-api-cancel-order effect-adapters/make-api-cancel-order))
  (is (identical? order-adapters/make-api-submit-position-tpsl effect-adapters/make-api-submit-position-tpsl))
  (is (identical? order-adapters/make-api-submit-position-margin effect-adapters/make-api-submit-position-margin)))
