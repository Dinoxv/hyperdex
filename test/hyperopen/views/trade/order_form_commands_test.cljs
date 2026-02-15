(ns hyperopen.views.trade.order-form-commands-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trade.order-form-commands :as commands]
            [hyperopen.views.trade.order-form-intent-adapter :as intent-adapter]))

(deftest order-form-command-builders-return-semantic-intents-test
  (is (= {:command-id :order-form/select-entry-mode
          :args [:market]}
         (commands/select-entry-market)))
  (is (= {:command-id :order-form/set-order-ui-leverage
          :args [25]}
         (commands/set-order-ui-leverage 25)))
  (is (= {:command-id :order-form/update-order-form
          :args [[:side] :sell]}
         (commands/set-order-side :sell))))

(deftest intent-adapter-translates-to-runtime-action-vectors-test
  (is (= [[:actions/select-order-entry-mode :limit]]
         (intent-adapter/command->actions
          (commands/select-entry-limit))))
  (is (= [[:actions/update-order-form [:price] [:event.target/value]]]
         (intent-adapter/command->actions
          (commands/set-limit-price-input))))
  (is (= [[:actions/update-order-form [:reduce-only] [:event.target/checked]]]
         (intent-adapter/command->actions
          (commands/toggle-reduce-only)))))
