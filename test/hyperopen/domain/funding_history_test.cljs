(ns hyperopen.domain.funding-history-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.domain.funding-history :as funding-history]))

(deftest normalize-info-funding-row-maps-and-validates-shape-test
  (let [row (funding-history/normalize-info-funding-row
             {:time 1700000000000
              :delta {:type "funding"
                      :coin "HYPE"
                      :usdc "-1.2500"
                      :szi "-250.5"
                      :fundingRate "0.00045"}})]
    (is (= "HYPE" (:coin row)))
    (is (= 1700000000000 (:time-ms row)))
    (is (= :short (:position-side row)))
    (is (= 250.5 (:size-raw row)))
    (is (= -1.25 (:payment-usdc-raw row)))
    (is (= 4.5e-4 (:funding-rate-raw row)))))

(deftest normalize-funding-history-filters-is-deterministic-for-fixed-now-test
  (let [now 1700600000000
        filters {:coin-set #{"BTC" "ETH"}
                 :start-time-ms 1700700000000
                 :end-time-ms 1700100000000}
        a (funding-history/normalize-funding-history-filters filters now)
        b (funding-history/normalize-funding-history-filters filters now)]
    (is (= a b))
    ;; Inverted input range is normalized.
    (is (= [1700100000000 1700700000000]
           [(:start-time-ms a) (:end-time-ms a)]))))

(deftest merge-and-filter-funding-history-rows-dedupes-by-id-test
  (let [row-a (funding-history/normalize-ws-funding-row {:time 1700000000000
                                                          :coin "HYPE"
                                                          :usdc "1.0"
                                                          :szi "100.0"
                                                          :fundingRate "0.0001"})
        row-b (funding-history/normalize-ws-funding-row {:time 1700003600000
                                                          :coin "BTC"
                                                          :usdc "-2.0"
                                                          :szi "-50.0"
                                                          :fundingRate "-0.0003"})
        merged (funding-history/merge-funding-history-rows [row-a row-b row-a] [])
        normalized-filters (funding-history/normalize-funding-history-filters
                            {:coin-set #{"BTC"}
                             :start-time-ms 0
                             :end-time-ms 2000000000000}
                            1700000000000)
        projected (funding-history/filter-funding-history-rows merged normalized-filters)]
    (is (= 2 (count merged)))
    (is (= [1700003600000 1700000000000] (mapv :time-ms merged)))
    (is (= ["BTC"] (mapv :coin projected)))))
