(ns hyperopen.account.history.effects-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.account.history.actions :as history-actions]
            [hyperopen.account.history.effects :as history-effects]
            [hyperopen.api.default :as api]
            [hyperopen.domain.funding-history :as funding-history]
            [hyperopen.views.account-info-view :as account-info-view]))

(defn- apply-save-many-effect!
  [store effect]
  (doseq [[path value] (second effect)]
    (swap! store assoc-in path value)))

(defn- collect-strings
  [node]
  (cond
    (string? node)
    [node]

    (vector? node)
    (let [children (if (map? (second node))
                     (nnext node)
                     (next node))]
      (mapcat collect-strings children))

    (seq? node)
    (mapcat collect-strings node)

    :else
    []))

(deftest funding-history-flow-select-fetch-and-render-shows-rows-test
  (async done
    (let [filters {:coin-set #{}
                   :start-time-ms 0
                   :end-time-ms 2000000000000}
          store (atom {:wallet {:address "0xabc"}
                       :account-info {:selected-tab :balances
                                      :loading false
                                      :error nil
                                      :funding-history {:filters filters
                                                        :draft-filters filters
                                                        :sort {:column "Time"
                                                               :direction :desc}
                                                        :filter-open? false
                                                        :page-size 50
                                                        :page 1
                                                        :page-input "1"
                                                        :loading? false
                                                        :error nil
                                                        :request-id 0}}
                       :account {:mode :classic
                                 :abstraction-raw nil}
                       :asset-selector {:market-by-key {}}
                       :orders {:open-orders []
                                :open-orders-snapshot []
                                :open-orders-snapshot-by-dex {}
                                :fills []
                                :fundings-raw []
                                :fundings []
                                :order-history []
                                :ledger []}
                       :webdata2 {}})
          effects (history-actions/select-account-info-tab @store :funding-history)
          save-effect (first effects)
          fetch-effect (second effects)
          request-id (second fetch-effect)
          funding-row (funding-history/normalize-info-funding-row
                       {:time 1700000000000
                        :delta {:type "funding"
                                :coin "HYPE"
                                :usdc "0.3500"
                                :szi "25.0"
                                :fundingRate "0.0002"}})]
      (apply-save-many-effect! store save-effect)
      (with-redefs [api/request-user-funding-history! (fn
                                                        ([_address]
                                                         (js/Promise.resolve [funding-row]))
                                                        ([_address _opts]
                                                         (js/Promise.resolve [funding-row])))]
        (-> (history-effects/api-fetch-user-funding-history-effect nil store request-id)
            (.then (fn [_]
                     (let [panel (account-info-view/account-info-panel @store)
                           strings (set (collect-strings panel))]
                       (is (= :funding-history (get-in @store [:account-info :selected-tab])))
                       (is (= 1 (count (get-in @store [:orders :fundings]))))
                       (is (= "HYPE" (get-in @store [:orders :fundings 0 :coin])))
                       (is (contains? strings "HYPE"))
                       (is (contains? strings "Long"))
                       (is (not (contains? strings "No funding history")))
                       (done))))
            (.catch (fn [err]
                      (is false (str "Unexpected error: " err))
                      (done))))))))
