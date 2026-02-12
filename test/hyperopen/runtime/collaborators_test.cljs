(ns hyperopen.runtime.collaborators-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.history.actions :as account-history-actions]
            [hyperopen.account.history.effects :as account-history-effects]
            [hyperopen.asset-selector.actions :as asset-actions]
            [hyperopen.chart.actions :as chart-actions]
            [hyperopen.order.actions :as order-actions]
            [hyperopen.runtime.collaborators :as collaborators]
            [hyperopen.wallet.actions :as wallet-actions]))

(deftest runtime-effect-deps-merges-defaults-with-overrides-test
  (let [save-fn (fn [& _] :save)
        export-fn (fn [& _] :export)
        deps (collaborators/runtime-effect-deps
              {:storage {:save save-fn}
               :api {:export-funding-history-csv export-fn}})]
    (is (identical? save-fn (get-in deps [:storage :save])))
    (is (identical? export-fn (get-in deps [:api :export-funding-history-csv])))
    (is (identical? account-history-effects/api-fetch-user-funding-history-effect
                    (get-in deps [:api :api-fetch-user-funding-history])))
    (is (identical? account-history-effects/api-fetch-historical-orders-effect
                    (get-in deps [:api :api-fetch-historical-orders])))))

(deftest runtime-action-deps-provides-default-domain-action-handlers-test
  (let [deps (collaborators/runtime-action-deps {})]
    (is (identical? wallet-actions/connect-wallet-action
                    (:connect-wallet-action deps)))
    (is (identical? asset-actions/select-asset
                    (:select-asset deps)))
    (is (identical? chart-actions/select-chart-type
                    (:select-chart-type deps)))
    (is (identical? account-history-actions/select-account-info-tab
                    (:select-account-info-tab deps)))
    (is (identical? order-actions/submit-order
                    (:submit-order deps)))))

(deftest runtime-action-deps-overrides-default-action-handlers-test
  (let [connect-wallet-action* (fn [& _] :override-connect)
        navigate* (fn [& _] :navigate)
        deps (collaborators/runtime-action-deps
              {:connect-wallet-action connect-wallet-action*
               :navigate navigate*})]
    (is (identical? connect-wallet-action*
                    (:connect-wallet-action deps)))
    (is (identical? navigate*
                    (:navigate deps)))))
