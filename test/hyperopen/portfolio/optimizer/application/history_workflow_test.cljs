(ns hyperopen.portfolio.optimizer.application.history-workflow-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.history-workflow :as workflow]))

(def btc-instrument
  {:instrument-id "perp:BTC"
   :market-type :perp
   :coin "BTC"})

(def eth-instrument
  {:instrument-id "perp:ETH"
   :market-type :perp
   :coin "ETH"})

(def queued-status
  {:status :queued
   :started-at-ms nil
   :completed-at-ms nil
   :error nil
   :warnings []})

(defn- prefetch-state
  []
  {:portfolio {:optimizer
               {:draft {:universe [btc-instrument eth-instrument]}
                :history-data {:candle-history-by-coin
                               {"SOL" [{:time 1000 :close "20"}]}
                               :funding-history-by-coin
                               {"SOL" [{:time-ms 1000 :funding-rate-raw 0}]}}
                :history-prefetch {:queue [btc-instrument eth-instrument]
                                   :active-instrument-id nil
                                   :by-instrument-id {"perp:BTC" queued-status
                                                      "perp:ETH" queued-status}}
                :runtime {:as-of-ms 3000
                          :stale-after-ms 60000}}}})

(deftest begin-selection-prefetch-starts-first-queued-instrument-test
  (let [result (workflow/begin-selection-prefetch
                {:state (prefetch-state)
                 :opts {:source :selection-prefetch
                        :queue? true
                        :merge? true}
                 :now-ms 1000})]
    (is (= "perp:BTC"
           (get-in result [:state :portfolio :optimizer :history-prefetch :active-instrument-id])))
    (is (= :loading
           (get-in result
                   [:state
                    :portfolio
                    :optimizer
                    :history-prefetch
                    :by-instrument-id
                    "perp:BTC"
                    :status])))
    (is (= [{:command/type :optimizer.workflow/request-history-bundle
             :source :selection-prefetch
             :instrument-id "perp:BTC"
             :request-signature (get-in result
                                        [:state
                                         :portfolio
                                         :optimizer
                                         :history-load-state
                                         :request-signature])
             :request (get-in result [:commands 0 :request])}]
           (:commands result)))
    (is (= ["perp:BTC"]
           (mapv :instrument-id (get-in result [:commands 0 :request :universe]))))))

(deftest complete-selection-prefetch-failure-continues-with-next-queued-instrument-test
  (let [started (workflow/begin-selection-prefetch
                 {:state (prefetch-state)
                  :opts {:source :selection-prefetch
                         :queue? true
                         :merge? true}
                  :now-ms 1000})
        signature (get-in started
                          [:state
                           :portfolio
                           :optimizer
                           :history-load-state
                           :request-signature])
        result (workflow/complete-selection-prefetch
                {:state (:state started)
                 :instrument-id "perp:BTC"
                 :request-signature signature
                 :completed-at-ms 1100
                 :error {:message "history boom"}
                 :opts {:source :selection-prefetch
                        :queue? true
                        :merge? true}})]
    (is (= :failed
           (get-in result
                   [:state
                    :portfolio
                    :optimizer
                    :history-prefetch
                    :by-instrument-id
                    "perp:BTC"
                    :status])))
    (is (= "perp:ETH"
           (get-in result [:state :portfolio :optimizer :history-prefetch :active-instrument-id])))
    (is (= ["perp:ETH"]
           (mapv :instrument-id
                 (get-in result [:state :portfolio :optimizer :history-prefetch :queue]))))
    (is (= :optimizer.workflow/request-history-bundle
           (get-in result [:commands 0 :command/type])))
    (is (= ["perp:ETH"]
           (mapv :instrument-id (get-in result [:commands 0 :request :universe]))))
    (is (= {"SOL" [{:time 1000 :close "20"}]}
           (get-in result
                   [:state :portfolio :optimizer :history-data :candle-history-by-coin])))))
