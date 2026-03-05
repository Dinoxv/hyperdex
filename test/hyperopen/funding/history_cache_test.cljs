(ns hyperopen.funding.history-cache-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.funding.history-cache :as funding-cache]
            [hyperopen.test-support.async :as async-support]))

(deftest merge-and-trim-market-funding-history-rows-test
  (let [coin "BTC"
        now-ms 1700000000000
        rows (funding-cache/merge-market-funding-history-rows
              coin
              [{:coin coin :time 1699990000000 :fundingRate "0.0001"}
               {:coin coin :time 1699993600000 :fundingRate "0.0002"}]
              [{:coin coin :time 1699993600000 :fundingRate "0.0003"}
               {:coin coin :time (- now-ms (* 40 24 60 60 1000)) :fundingRate "0.0009"}])
        trimmed (funding-cache/trim-market-funding-history-rows
                 rows
                 now-ms
                 funding-cache/cache-retention-window-ms)]
    (is (= [1699990000000 1699993600000]
           (mapv :time-ms trimmed)))
    (is (= [0.0001 0.0003]
           (mapv :fundingRate trimmed)))))

(deftest normalize-coin-canonicalizes-case-for-default-and-dex-prefixed-markets-test
  (is (= "BTC" (funding-cache/normalize-coin "btc")))
  (is (= "ETH" (funding-cache/normalize-coin " Eth ")))
  (is (= "xyz:GOLD" (funding-cache/normalize-coin "XYZ:GOLD")))
  (is (= "xyz:GOLD" (funding-cache/normalize-coin "xyz:GOLD"))))

(deftest sync-market-funding-history-cache-cold-then-warm-test
  (async done
    (let [now-ms (atom 1700007200000)
          cache-state (atom nil)
          request-calls (atom [])
          request-market-funding-history!
          (fn [_coin opts]
            (swap! request-calls conj opts)
            (js/Promise.resolve [{:coin "BTC"
                                  :time 1700000000000
                                  :fundingRate "0.0001"
                                  :premium "0.0002"}
                                 {:coin "BTC"
                                  :time 1700003600000
                                  :fundingRate "0.0002"
                                  :premium "0.0003"}]))]
      (-> (funding-cache/sync-market-funding-history-cache!
           "BTC"
           {:now-ms-fn (fn [] @now-ms)
            :load-cache-fn (fn [_coin _opts] @cache-state)
            :persist-cache-fn (fn [_coin snapshot]
                                (reset! cache-state snapshot))
            :request-market-funding-history! request-market-funding-history!
            :min-refresh-interval-ms (* 5 60 1000)})
          (.then (fn [first-result]
                   (is (= :network (:source first-result)))
                   (is (= 2 (:fetched-count first-result)))
                   (is (= 2 (count (:rows first-result))))
                   (is (= 1 (count @request-calls)))
                   (-> (funding-cache/sync-market-funding-history-cache!
                        "BTC"
                        {:now-ms-fn (fn [] @now-ms)
                         :load-cache-fn (fn [_coin _opts] @cache-state)
                         :persist-cache-fn (fn [_coin snapshot]
                                             (reset! cache-state snapshot))
                         :request-market-funding-history! request-market-funding-history!
                         :min-refresh-interval-ms (* 5 60 1000)})
                       (.then (fn [second-result]
                                (is (= :cache (:source second-result)))
                                (is (= :recent-sync (:reason second-result)))
                                (is (= 1 (count @request-calls)))
                                (done)))
                       (.catch (async-support/unexpected-error done)))))
          (.catch (async-support/unexpected-error done))))))

(deftest sync-market-funding-history-cache-fetches-delta-only-test
  (async done
    (let [now-ms (atom 1700007200000)
          cache-state (atom nil)
          request-calls (atom [])
          request-market-funding-history!
          (fn [_coin opts]
            (swap! request-calls conj opts)
            (let [start-ms (:start-time-ms opts)]
              (if (= 1700003600001 start-ms)
                (js/Promise.resolve [{:coin "BTC"
                                      :time 1700007200000
                                      :fundingRate "0.0003"
                                      :premium "0.0004"}])
                (js/Promise.resolve [{:coin "BTC"
                                      :time 1700000000000
                                      :fundingRate "0.0001"
                                      :premium "0.0002"}
                                     {:coin "BTC"
                                      :time 1700003600000
                                      :fundingRate "0.0002"
                                      :premium "0.0003"}]))))]
      (-> (funding-cache/sync-market-funding-history-cache!
           "BTC"
           {:now-ms-fn (fn [] @now-ms)
            :load-cache-fn (fn [_coin _opts] @cache-state)
            :persist-cache-fn (fn [_coin snapshot]
                                (reset! cache-state snapshot))
            :request-market-funding-history! request-market-funding-history!
            :min-refresh-interval-ms 0})
          (.then (fn [_]
                   (reset! now-ms 1700007200000)
                   (-> (funding-cache/sync-market-funding-history-cache!
                        "BTC"
                        {:now-ms-fn (fn [] @now-ms)
                         :load-cache-fn (fn [_coin _opts] @cache-state)
                         :persist-cache-fn (fn [_coin snapshot]
                                             (reset! cache-state snapshot))
                         :request-market-funding-history! request-market-funding-history!
                         :min-refresh-interval-ms 0
                         :force? true})
                       (.then (fn [result]
                                (is (= :network (:source result)))
                                (is (= 1 (:fetched-count result)))
                                (is (= [1700000000000 1700003600000 1700007200000]
                                       (mapv :time-ms (:rows result))))
                                (is (= 2 (count @request-calls)))
                                (is (= 1700003600001
                                       (:start-time-ms (second @request-calls))))
                                (done)))
                       (.catch (async-support/unexpected-error done)))))
          (.catch (async-support/unexpected-error done))))))

(deftest sync-market-funding-history-cache-empty-cache-does-not-skip-network-fetch-test
  (async done
    (let [now-ms 1700007200000
          request-calls (atom [])
          requested-coins (atom [])
          request-market-funding-history!
          (fn [coin opts]
            (swap! requested-coins conj coin)
            (swap! request-calls conj opts)
            (js/Promise.resolve [{:coin "xyz:GOLD"
                                  :time 1700007200000
                                  :fundingRate "0.0003"
                                  :premium "0.0004"}]))]
      (-> (funding-cache/sync-market-funding-history-cache!
           "XYZ:GOLD"
           {:now-ms-fn (constantly now-ms)
            :load-cache-fn (fn [_coin _opts]
                             {:version 1
                              :coin "XYZ:GOLD"
                              :rows []
                              :last-row-time-ms nil
                              :last-sync-ms now-ms})
            :persist-cache-fn (fn [_coin _snapshot] nil)
            :request-market-funding-history! request-market-funding-history!
            :min-refresh-interval-ms (* 5 60 1000)})
          (.then (fn [result]
                   (is (= :network (:source result)))
                   (is (= 1 (:fetched-count result)))
                   (is (= ["xyz:GOLD"] @requested-coins))
                   (is (= 1 (count @request-calls)))
                   (done)))
          (.catch (async-support/unexpected-error done))))))

(deftest sync-market-funding-history-cache-supports-promise-based-cache-adapters-test
  (async done
    (let [now-ms 1700007200000
          cache-state (atom nil)
          persisted-snapshots (atom [])
          request-market-funding-history!
          (fn [_coin _opts]
            (js/Promise.resolve [{:coin "BTC"
                                  :time 1700000000000
                                  :fundingRate "0.0001"
                                  :premium "0.0002"}]))]
      (-> (funding-cache/sync-market-funding-history-cache!
           "BTC"
           {:now-ms-fn (constantly now-ms)
            :load-cache-fn (fn [_coin _opts]
                             (js/Promise.resolve @cache-state))
            :persist-cache-fn (fn [_coin snapshot]
                                (reset! cache-state snapshot)
                                (swap! persisted-snapshots conj snapshot)
                                (js/Promise.resolve true))
            :request-market-funding-history! request-market-funding-history!
            :min-refresh-interval-ms 0})
          (.then (fn [result]
                   (is (= :network (:source result)))
                   (is (= 1 (count (:rows result))))
                   (is (= 1 (count @persisted-snapshots)))
                   (is (= 1700000000000
                          (get-in (first @persisted-snapshots) [:rows 0 :time-ms])))
                   (done)))
          (.catch (async-support/unexpected-error done))))))
