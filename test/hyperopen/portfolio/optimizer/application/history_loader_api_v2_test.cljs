(ns hyperopen.portfolio.optimizer.application.history-loader-api-v2-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.history-loader :as history-loader]
            [hyperopen.portfolio.optimizer.application.history-loader.api-v2 :as api-v2]))

(defn near?
  [expected actual]
  (< (js/Math.abs (- expected actual)) 0.000001))

(deftest normalize-discovery-preserves-backend-and-local-identities-test
  (let [discovery (api-v2/normalize-discovery
                   {:contract_version "optimizer-history-api-v2"
                    :request_id "rid-discovery"
                    :dataset_version "dv-1"
                    :status "partial"
                    :instruments
                    [{:instrument_id "hl:perp:BTC"
                      :display_symbol "BTC"
                      :instrument_kind "hl_perp"
                      :funding_enabled true
                      :aliases {:hyperopen_market_key "perp:BTC"}
                      :proxy {:available true
                              :proxy_mapping_id "proxy-review:btc"}
                      :history {:status "available"
                                :quality_status "passed"
                                :observation_count 0
                                :max_native_lookback_days 0
                                :native_only {:status "available"
                                              :observation_count 0
                                              :max_lookback_days 0}}}]
                    :warnings [{:code "validation-failed"
                                :message "discovery warning"}]})
        candidate (api-v2/with-discovery-metadata
                    {:key "perp:BTC"
                     :market-type :perp
                     :coin "BTC"}
                    discovery)]
    (is (= :partial (:status discovery)))
    (is (= "hl:perp:BTC"
           (get-in discovery [:backend-id-by-local-id "perp:BTC"])))
    (is (= "perp:BTC"
           (get-in discovery
                   [:instruments-by-backend-id
                    "hl:perp:BTC"
                    :aliases
                    :hyperopen-market-key])))
    (is (= 0
           (get-in discovery
                   [:instruments-by-backend-id
                    "hl:perp:BTC"
                    :history
                    :observation-count])))
    (is (= [{:code :validation-failed
             :message "discovery warning"}]
           (:warnings discovery)))
    (is (= {:key "perp:BTC"
            :market-type :perp
            :coin "BTC"
            :optimizer-history/instrument-id "hl:perp:BTC"
            :optimizer-history/display-symbol "BTC"
            :optimizer-history/instrument-kind :hl-perp
            :optimizer-history/history-status :available
            :optimizer-history/quality-status :passed
            :optimizer-history/proxy {:available true
                                      :proxy-mapping-id "proxy-review:btc"}}
           candidate))))

(deftest align-api-v2-history-uses-api-aligned-returns-and-funding-policy-test
  (let [universe [{:instrument-id "perp:BTC"
                   :market-type :perp
                   :coin "BTC"
                   :optimizer-history/instrument-id "hl:perp:BTC"}
                  {:instrument-id "spot:PURR/USDC"
                   :market-type :spot
                   :coin "PURR/USDC"
                   :optimizer-history/instrument-id "hl:spot:PURR/USDC"}]
        api-body {:contract_version "optimizer-history-api-v2"
                  :request_id "rid-bundle"
                  :dataset_version "dv-2"
                  :status "partial"
                  :common_calendar [1000 2000 3000]
                  :return_calendar [2000 3000]
                  :aligned_returns_by_instrument
                  {"perp:BTC" {:instrument_id "hl:perp:BTC"
                               :returns [0.05 0.02]}
                   "spot:PURR/USDC" {:instrument_id "hl:spot:PURR/USDC"
                                     :returns [0.03 0.04]}}
                  :series_by_instrument
                  {"perp:BTC" {:instrument_id "hl:perp:BTC"
                               :lineage_kind "stitched_native_proxy"
                               :series_kind "market_price"
                               :points [{:time_ms 1000 :close 100 :return nil}
                                        {:time_ms 2000 :close 120 :return 0.05}
                                        {:time_ms 3000 :close 122.4 :return 0.02}]
                               :funding {:status "available"
                                         :annualized_carry 0.0123}
                               :warnings [{:code "proxy-history-used"}]}
                   "spot:PURR/USDC" {:instrument_id "hl:spot:PURR/USDC"
                                     :lineage_kind "native"
                                     :series_kind "market_price"
                                     :points [{:time_ms 1000 :close 10 :return nil}
                                              {:time_ms 2000 :close 10.3 :return 0.03}
                                              {:time_ms 3000 :close 10.712 :return 0.04}]
                                     :funding {:status "missing"}
                                     :warnings []}}
                  :warnings [{:code "stale-history"
                              :instrument_id "perp:BTC"}]}
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    api-body)
        aligned (history-loader/align-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :as-of-ms 4000
                  :stale-after-ms 10000})]
    (is (= [1000 2000 3000] (:calendar aligned)))
    (is (= [2000 3000] (:return-calendar aligned)))
    (is (near? 0.05
               (get-in aligned [:return-series-by-instrument "perp:BTC" 0])))
    (is (near? 0.02
               (get-in aligned [:return-series-by-instrument "perp:BTC" 1])))
    (is (near? 0.03
               (get-in aligned [:return-series-by-instrument "spot:PURR/USDC" 0])))
    (is (= :api-v2-aligned-returns
           (get-in aligned [:alignment-source :kind])))
    (is (= :history-api-v2
           (get-in aligned [:funding-by-instrument "perp:BTC" :source])))
    (is (= 0.0123
           (get-in aligned [:funding-by-instrument "perp:BTC" :annualized-carry])))
    (is (= :not-applicable
           (get-in aligned [:funding-by-instrument "spot:PURR/USDC" :source])))
    (is (= #{:proxy-history-used :stale-history}
           (set (map :code (:warnings aligned)))))))

(deftest align-api-v2-history-does-not-recompute-nil-boundary-returns-test
  (let [universe [{:instrument-id "perp:NEW"
                   :market-type :perp
                   :coin "NEW"
                   :optimizer-history/instrument-id "hl:perp:NEW"}]
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    {:contract_version "optimizer-history-api-v2"
                     :request_id "rid-no-aligned"
                     :dataset_version "dv-3"
                     :status "partial"
                     :series_by_instrument
                     {"perp:NEW" {:instrument_id "hl:perp:NEW"
                                  :lineage_kind "stitched_native_proxy"
                                  :series_kind "market_price"
                                  :points [{:time_ms 1000 :close 10 :return nil}
                                           {:time_ms 2000 :close 100 :return nil}
                                           {:time_ms 3000 :close 110 :return 0.1}]
                                  :funding {:status "missing"}
                                  :warnings [{:code "funding-history-missing"}]}}
                     :warnings []})
        aligned (api-v2/align-api-v2-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :min-observations 2})]
    (is (= [1000 2000 3000] (:calendar aligned)))
    (is (= [3000] (:return-calendar aligned)))
    (is (= [0.1]
           (get-in aligned [:return-series-by-instrument "perp:NEW"])))
    (is (= :missing-market-funding-history
           (get-in aligned [:funding-by-instrument "perp:NEW" :source])))
    (is (= #{:funding-history-missing}
           (set (map :code (:warnings aligned)))))))

(deftest align-api-v2-history-blocks-missing-and-ambiguous-series-test
  (let [universe [{:instrument-id "perp:BTC"
                   :market-type :perp
                   :coin "BTC"
                   :optimizer-history/instrument-id "hl:perp:BTC"}
                  {:instrument-id "perp:MISSING"
                   :market-type :perp
                   :coin "MISSING"
                   :optimizer-history/instrument-id "hl:perp:MISSING"}
                  {:instrument-id "perp:UNKNOWN"
                   :market-type :perp
                   :coin "UNKNOWN"}]
        normalized (api-v2/normalize-history-bundle
                    {:universe universe}
                    {:contract_version "optimizer-history-api-v2"
                     :request_id "rid-partial"
                     :dataset_version "dv-4"
                     :status "partial"
                     :common_calendar [1000 2000]
                     :return_calendar [2000]
                     :aligned_returns_by_instrument
                     {"perp:BTC" {:instrument_id "hl:perp:BTC"
                                  :returns [0.1]}}
                     :series_by_instrument
                     {"perp:BTC" {:instrument_id "hl:perp:BTC"
                                  :lineage_kind "native"
                                  :series_kind "market_price"
                                  :points [{:time_ms 1000 :close 100 :return nil}
                                           {:time_ms 2000 :close 110 :return 0.1}]
                                  :funding {:status "available"
                                            :annualized_carry 0}}
                      "perp:MISSING" {:instrument_id "hl:perp:MISSING"
                                      :lineage_kind "missing"
                                      :series_kind "market_price"
                                      :points []
                                      :warnings [{:code "missing-candle-history"}]}}
                     :warnings []})
        aligned (api-v2/align-api-v2-history-inputs
                 {:universe universe
                  :api-v2-history normalized
                  :min-observations 2})]
    (is (= ["perp:BTC"]
           (mapv :instrument-id (:eligible-instruments aligned))))
    (is (= ["perp:MISSING" "perp:UNKNOWN"]
           (mapv :instrument-id (:excluded-instruments aligned))))
    (is (= #{:missing-candle-history :identity-ambiguous}
           (set (map :code (:warnings aligned)))))))
