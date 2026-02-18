(ns hyperopen.views.trade.order-form-vm-submit-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trade.order-form-vm-submit :as vm-submit]))

(deftest submit-tooltip-message-branch-coverage-test
  (is (= "Fill required fields: Size, Price."
         (vm-submit/submit-tooltip-message ["Size" "Price"] false)))
  (is (= "Load order book data before placing a market order."
         (vm-submit/submit-tooltip-message [] true)))
  (is (nil? (vm-submit/submit-tooltip-message [] false))))

(deftest submit-tooltip-from-policy-delegates-to-message-rules-test
  (is (= "Fill required fields: Trigger."
         (vm-submit/submit-tooltip-from-policy {:required-fields ["Trigger"]
                                                :market-price-missing? true})))
  (is (= "Load order book data before placing a market order."
         (vm-submit/submit-tooltip-from-policy {:required-fields []
                                                :market-price-missing? true}))))
