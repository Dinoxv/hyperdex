(ns hyperopen.portfolio.optimizer.application.history-window-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.history-loader :as history-loader]
            [hyperopen.portfolio.optimizer.application.history-loader.api-v2 :as api-v2]))

(def day-ms
  (* 24 60 60 1000))

(defn- day-start-ms
  [day]
  (.getTime (js/Date. (str day "T00:00:00.000Z"))))

(deftest align-api-v2-history-window-identifies-weekday-proxy-limiter-test
  (let [d0 (day-start-ms "2026-01-01")
        day (fn [n] (+ d0 (* n day-ms)))
        universe [{:instrument-id "perp:BTC"
                   :market-type :perp
                   :coin "BTC"
                   :optimizer-history/instrument-id "hl:perp:BTC"}
                  {:instrument-id "external:SP500"
                   :market-type :external
                   :coin "SP500"
                   :optimizer-history/instrument-id "proxy:sp500"}]
        btc-points (mapv (fn [n]
                           {:time_ms (day n)
                            :close (+ 100 n)
                            :return (when (pos? n) 0.001)})
                         (range 10))
        proxy-days [0 1 2 5 6 7 8 9]
        proxy-points (mapv (fn [idx n]
                             {:time_ms (day n)
                              :close (+ 4000 idx)
                              :return (when (pos? idx) 0.0005)})
                           (range)
                           proxy-days)
        return-calendar (mapv day [1 2 5 6 7 8 9])
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    {:contract_version "optimizer-history-api-v2"
                     :request_id "rid-weekday-proxy"
                     :dataset_version "dv-weekday-proxy"
                     :status "ok"
                     :common_calendar (mapv day proxy-days)
                     :return_calendar return-calendar
                     :aligned_returns_by_instrument
                     {"hl:perp:BTC" {:instrument_id "hl:perp:BTC"
                                     :returns (vec (repeat 7 0.001))}
                      "proxy:sp500" {:instrument_id "proxy:sp500"
                                     :returns (vec (repeat 7 0.0005))}}
                     :series_by_instrument
                     {"hl:perp:BTC" {:instrument_id "hl:perp:BTC"
                                     :lineage_kind "native"
                                     :series_kind "market_price"
                                     :points btc-points
                                     :funding {:status "available"
                                               :annualized_carry 0}
                                     :warnings []}
                      "proxy:sp500" {:instrument_id "proxy:sp500"
                                     :lineage_kind "approved_proxy"
                                     :series_kind "market_price"
                                     :points proxy-points
                                     :funding {:status "not_applicable"}
                                     :warnings [{:code "proxy-history-used"}]}}
                     :warnings []})
        aligned (api-v2/align-api-v2-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :min-observations 2})]
    (is (= 7
           (get-in aligned [:history-window :return-observations])))
    (is (= 9
           (get-in aligned [:history-window :return-days])))
    (is (= "external:SP500"
           (get-in aligned [:history-window :limiting-instrument-id])))
    (is (= :fewest-return-observations
           (get-in aligned [:history-window :limiting-reason])))
    (is (= 7
           (get-in aligned [:history-window :limiting-source-return-observations])))))

(deftest align-history-inputs-history-window-identifies-late-starting-limiter-test
  (let [day (fn [n] (* n day-ms))
        aligned (history-loader/align-history-inputs
                 {:universe [{:instrument-id "perp:BTC"
                              :market-type :perp
                              :coin "BTC"}
                             {:instrument-id "perp:NEW"
                              :market-type :perp
                              :coin "NEW"}]
                  :candle-history-by-coin {"BTC" [{:time-ms (day 0) :close "100"}
                                                  {:time-ms (day 1) :close "101"}
                                                  {:time-ms (day 2) :close "102"}
                                                  {:time-ms (day 3) :close "103"}]
                                           "NEW" [{:time-ms (day 1) :close "50"}
                                                  {:time-ms (day 2) :close "51"}
                                                  {:time-ms (day 3) :close "52"}]}
                  :funding-history-by-coin {}
                  :as-of-ms (day 4)
                  :stale-after-ms (* 10 day-ms)})]
    (is (= [(day 1) (day 2) (day 3)]
           (:calendar aligned)))
    (is (= 2
           (get-in aligned [:history-window :return-observations])))
    (is (= 2
           (get-in aligned [:history-window :return-days])))
    (is (= "perp:NEW"
           (get-in aligned [:history-window :limiting-instrument-id])))
    (is (= :starts-later
           (get-in aligned [:history-window :limiting-reason])))))
