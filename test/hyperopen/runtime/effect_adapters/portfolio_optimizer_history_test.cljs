(ns hyperopen.runtime.effect-adapters.portfolio-optimizer-history-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer :as portfolio-optimizer-adapters]
            [hyperopen.test-support.async :as async-support]))

(deftest load-portfolio-optimizer-history-effect-persists-success-for-current-request-test
  (async done
    (let [calls (atom [])
          store (atom {:portfolio {:optimizer
                                    {:draft {:universe [{:instrument-id "perp:BTC"
                                                         :market-type :perp
                                                         :coin "BTC"}]}
                                     :runtime {:as-of-ms 3000
                                               :stale-after-ms 60000}}}})
          bundle {:candle-history-by-coin {"BTC" [{:time 1000 :close "100"}
                                                  {:time 2000 :close "110"}]}
                  :funding-history-by-coin {"BTC" [{:time-ms 1000
                                                    :funding-rate-raw 0.001}]}
                  :warnings [{:code :funding-partial}]
                  :request-plan {:candle-requests [{:coin "BTC"}]}}]
      (with-redefs [portfolio-optimizer-adapters/*request-history-bundle!*
                    (fn [deps request]
                      (swap! calls conj {:deps deps
                                         :request request})
                      (js/Promise.resolve bundle))
                    portfolio-optimizer-adapters/*now-ms* (fn [] 12345)]
        (let [promise (portfolio-optimizer-adapters/load-portfolio-optimizer-history-effect
                       nil
                       store
                       {:bars 90})]
          (is (= :loading
                 (get-in @store [:portfolio :optimizer :history-load-state :status])))
          (-> promise
              (.then (fn [result]
                       (is (= bundle result))
                       (is (= 1 (count @calls)))
                       (is (= [{:instrument-id "perp:BTC"
                                :market-type :perp
                                :coin "BTC"}]
                              (get-in @calls [0 :request :universe])))
                       (is (= 90 (get-in @calls [0 :request :bars])))
                       (is (fn? (get-in @calls [0 :deps :request-candle-snapshot!])))
                       (is (fn? (get-in @calls [0 :deps :request-market-funding-history!])))
                       (is (= {"BTC" [{:time 1000 :close "100"}
                                      {:time 2000 :close "110"}]}
                              (get-in @store
                                      [:portfolio :optimizer :history-data :candle-history-by-coin])))
                       (is (= {"BTC" [{:time-ms 1000
                                       :funding-rate-raw 0.001}]}
                              (get-in @store
                                      [:portfolio :optimizer :history-data :funding-history-by-coin])))
                       (is (= {:status :succeeded
                               :request-signature (get-in @store
                                                          [:portfolio
                                                           :optimizer
                                                           :history-load-state
                                                           :request-signature])
                               :started-at-ms 12345
                               :completed-at-ms 12345
                               :error nil
                               :warnings [{:code :funding-partial}]}
                              (get-in @store [:portfolio :optimizer :history-load-state])))
                       (done)))
              (.catch (async-support/unexpected-error done))))))))

(deftest load-portfolio-optimizer-history-selection-prefetch-drains-queue-and-merges-test
  (async done
    (let [btc-instrument {:instrument-id "perp:BTC"
                          :market-type :perp
                          :coin "BTC"}
          eth-instrument {:instrument-id "perp:ETH"
                          :market-type :perp
                          :coin "ETH"}
          calls (atom [])
          now-values (atom [1000 1100 1200 1300])
          now-ms (fn []
                   (let [value (or (first @now-values) 1400)]
                     (swap! now-values #(vec (rest %)))
                     value))
          bundle-by-coin {"BTC" {:candle-history-by-coin
                                 {"BTC" [{:time 1000 :close "100"}
                                         {:time 2000 :close "110"}]}
                                 :funding-history-by-coin
                                 {"BTC" [{:time-ms 1000
                                          :funding-rate-raw 0}]}
                                 :warnings []
                                 :request-plan {:candle-requests [{:coin "BTC"}]}}
                          "ETH" {:candle-history-by-coin
                                 {"ETH" [{:time 1000 :close "50"}
                                         {:time 2000 :close "55"}]}
                                 :funding-history-by-coin
                                 {"ETH" [{:time-ms 1000
                                          :funding-rate-raw 0}]}
                                 :warnings [{:code :funding-partial}]
                                 :request-plan {:candle-requests [{:coin "ETH"}]}}}
          queued-status {:status :queued
                         :started-at-ms nil
                         :completed-at-ms nil
                         :error nil
                         :warnings []}
          store (atom {:portfolio {:optimizer
                                    {:draft {:universe [btc-instrument eth-instrument]}
                                     :history-data {:candle-history-by-coin
                                                    {"SOL" [{:time 1000
                                                             :close "20"}]}
                                                    :funding-history-by-coin
                                                    {"SOL" [{:time-ms 1000
                                                             :funding-rate-raw 0}]}}
                                     :history-prefetch
                                     {:queue [btc-instrument eth-instrument]
                                      :active-instrument-id nil
                                      :by-instrument-id {"perp:BTC" queued-status
                                                         "perp:ETH" queued-status}}}}})]
      (with-redefs [portfolio-optimizer-adapters/*request-history-bundle!*
                    (fn [_deps request]
                      (swap! calls conj request)
                      (let [coin (get-in request [:universe 0 :coin])]
                        (js/Promise.resolve (get bundle-by-coin coin))))
                    portfolio-optimizer-adapters/*now-ms* now-ms]
        (-> (portfolio-optimizer-adapters/load-portfolio-optimizer-history-effect
             nil
             store
             {:source :selection-prefetch
              :queue? true
              :merge? true})
            (.then (fn [_result]
                     (is (= [["perp:BTC"] ["perp:ETH"]]
                            (mapv (fn [request]
                                    (mapv :instrument-id (:universe request)))
                                  @calls)))
                     (is (= #{"SOL" "BTC" "ETH"}
                            (set (keys (get-in @store
                                               [:portfolio
                                                :optimizer
                                                :history-data
                                                :candle-history-by-coin])))))
                     (is (= #{"SOL" "BTC" "ETH"}
                            (set (keys (get-in @store
                                               [:portfolio
                                                :optimizer
                                                :history-data
                                                :funding-history-by-coin])))))
                     (is (= []
                            (get-in @store
                                    [:portfolio :optimizer :history-prefetch :queue])))
                     (is (nil? (get-in @store
                                       [:portfolio
                                        :optimizer
                                        :history-prefetch
                                        :active-instrument-id])))
                     (is (= :succeeded
                            (get-in @store
                                    [:portfolio
                                     :optimizer
                                     :history-prefetch
                                     :by-instrument-id
                                     "perp:BTC"
                                     :status])))
                     (is (= [{:code :funding-partial}]
                            (get-in @store
                                    [:portfolio
                                     :optimizer
                                     :history-prefetch
                                     :by-instrument-id
                                     "perp:ETH"
                                     :warnings])))
                     (done)))
            (.catch (async-support/unexpected-error done)))))))

(deftest load-portfolio-optimizer-history-selection-prefetch-settles-removed-active-test
  (async done
    (let [btc-instrument {:instrument-id "perp:BTC"
                          :market-type :perp
                          :coin "BTC"}
          queued-status {:status :queued
                         :started-at-ms nil
                         :completed-at-ms nil
                         :error nil
                         :warnings []}
          resolve-bundle! (atom nil)
          now-values (atom [1000 1100])
          now-ms (fn []
                   (let [value (or (first @now-values) 1200)]
                     (swap! now-values #(vec (rest %)))
                     value))
          store (atom {:portfolio {:optimizer
                                    {:draft {:universe [btc-instrument]}
                                     :history-prefetch
                                     {:queue [btc-instrument]
                                      :active-instrument-id nil
                                      :by-instrument-id {"perp:BTC" queued-status}}}}})]
      (with-redefs [portfolio-optimizer-adapters/*request-history-bundle!*
                    (fn [_deps _request]
                      (js/Promise.
                       (fn [resolve _reject]
                         (reset! resolve-bundle! resolve))))
                    portfolio-optimizer-adapters/*now-ms* now-ms]
        (let [promise (portfolio-optimizer-adapters/load-portfolio-optimizer-history-effect
                       nil
                       store
                       {:source :selection-prefetch
                        :queue? true
                        :merge? true})]
          (is (= :loading
                 (get-in @store [:portfolio :optimizer :history-load-state :status])))
          (swap! store assoc-in [:portfolio :optimizer :draft :universe] [])
          (@resolve-bundle! {:candle-history-by-coin
                             {"BTC" [{:time 1000 :close "100"}]}
                             :funding-history-by-coin
                             {"BTC" [{:time-ms 1000
                                      :funding-rate-raw 0}]}
                             :warnings []
                             :request-plan {:candle-requests [{:coin "BTC"}]}})
          (-> promise
              (.then (fn [_result]
                       (is (nil? (get-in @store
                                         [:portfolio
                                          :optimizer
                                          :history-data
                                          :candle-history-by-coin
                                          "BTC"])))
                       (is (= :succeeded
                              (get-in @store
                                      [:portfolio
                                       :optimizer
                                       :history-load-state
                                       :status])))
                       (is (= []
                              (get-in @store
                                      [:portfolio
                                       :optimizer
                                       :history-prefetch
                                       :queue])))
                       (is (= {}
                              (get-in @store
                                      [:portfolio
                                       :optimizer
                                       :history-prefetch
                                       :by-instrument-id])))
                       (done)))
              (.catch (async-support/unexpected-error done))))))))

(deftest load-portfolio-optimizer-history-effect-preserves-data-on-error-test
  (async done
    (let [store (atom {:portfolio {:optimizer
                                    {:draft {:universe [{:instrument-id "perp:BTC"
                                                         :market-type :perp
                                                         :coin "BTC"}]}
                                     :history-data {:candle-history-by-coin {"BTC" [:old]}}}}})]
      (with-redefs [portfolio-optimizer-adapters/*request-history-bundle!*
                    (fn [_deps _request]
                      (js/Promise.reject (js/Error. "history boom")))
                    portfolio-optimizer-adapters/*now-ms* (fn [] 222)]
        (let [promise (portfolio-optimizer-adapters/load-portfolio-optimizer-history-effect nil store)]
          (-> promise
              (.then (fn [_]
                       (is (= {"BTC" [:old]}
                              (get-in @store
                                      [:portfolio :optimizer :history-data :candle-history-by-coin])))
                       (is (= :failed
                              (get-in @store [:portfolio :optimizer :history-load-state :status])))
                       (is (= "history boom"
                              (get-in @store [:portfolio :optimizer :history-load-state :error :message])))
                       (done)))
              (.catch (async-support/unexpected-error done))))))))
