(ns hyperopen.portfolio.benchmark-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.actions :as actions]))

(def ^:private replace-shareable-route-query-effect
  [:effects/replace-shareable-route-query])

(deftest select-portfolio-summary-time-range-fetches-selected-benchmark-candles-test
  (is (= [[:effects/save-many [[[:portfolio-ui :summary-time-range] :week]
                               [[:portfolio-ui :summary-scope-dropdown-open?] false]
                               [[:portfolio-ui :summary-time-range-dropdown-open?] false]
                               [[:portfolio-ui :performance-metrics-time-range-dropdown-open?] false]]]
          [:effects/local-storage-set "portfolio-summary-time-range" "week"]
          replace-shareable-route-query-effect
          [:effects/fetch-candle-snapshot :coin "BTC" :interval :15m :bars 800]
          [:effects/fetch-candle-snapshot :coin "ETH" :interval :15m :bars 800]]
         (actions/select-portfolio-summary-time-range
          {:portfolio-ui {:returns-benchmark-coins ["BTC"
                                                    "vault:0x1234567890abcdef1234567890abcdef12345678"
                                                    "ETH"
                                                    "BTC"]
                          :returns-benchmark-coin "DOGE"}}
          :week)))
  (is (= [[:effects/save-many [[[:portfolio-ui :summary-time-range] :week]
                               [[:portfolio-ui :summary-scope-dropdown-open?] false]
                               [[:portfolio-ui :summary-time-range-dropdown-open?] false]
                               [[:portfolio-ui :performance-metrics-time-range-dropdown-open?] false]]]
          [:effects/local-storage-set "portfolio-summary-time-range" "week"]
          replace-shareable-route-query-effect
          [:effects/fetch-candle-snapshot :coin "BTC" :interval :15m :bars 800]]
         (actions/select-portfolio-summary-time-range
          {:portfolio-ui {:returns-benchmark-coin "BTC"}}
          :week))))

(deftest select-portfolio-chart-tab-fetches-selected-benchmark-candles-test
  (is (= [[:effects/save-many
           [[[:portfolio-ui :chart-tab] :returns]]]
          replace-shareable-route-query-effect
          [:effects/fetch-candle-snapshot :coin "ETH" :interval :1h :bars 800]
          [:effects/fetch-candle-snapshot :coin "SPY" :interval :1h :bars 800]]
         (actions/select-portfolio-chart-tab
          {:portfolio-ui {:returns-benchmark-coins ["ETH"
                                                    "vault:0x1234567890abcdef1234567890abcdef12345678"
                                                    "SPY"]
                          :summary-time-range :month}}
          :returns)))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :chart-tab] :returns]]]
          replace-shareable-route-query-effect
          [:effects/fetch-candle-snapshot :coin "ETH" :interval :1h :bars 800]]
         (actions/select-portfolio-chart-tab
          {:portfolio-ui {:returns-benchmark-coin "ETH"
                          :summary-time-range :month}}
          :returns))))

(deftest set-portfolio-returns-benchmark-search-and-open-state-test
  (is (= [[:effects/save
           [:portfolio-ui :returns-benchmark-search]
           "spy"]]
         (actions/set-portfolio-returns-benchmark-search {} "spy")))
  (is (= [[:effects/save
           [:portfolio-ui :returns-benchmark-search]
           "42"]]
         (actions/set-portfolio-returns-benchmark-search {} 42)))
  (is (= [[:effects/save
           [:portfolio-ui :returns-benchmark-suggestions-open?]
           true]
          [:effects/api-fetch-vault-index]
          [:effects/api-fetch-vault-summaries]]
         (actions/set-portfolio-returns-benchmark-suggestions-open {} true)))
  (is (= [[:effects/save
           [:portfolio-ui :returns-benchmark-suggestions-open?]
           true]
          [:effects/api-fetch-vault-index]
          [:effects/api-fetch-vault-summaries]]
         (actions/set-portfolio-returns-benchmark-suggestions-open
          {:vaults {}}
          true)))
  (is (= [[:effects/save
           [:portfolio-ui :returns-benchmark-suggestions-open?]
           false]]
         (actions/set-portfolio-returns-benchmark-suggestions-open {} nil))))

(deftest normalize-portfolio-returns-benchmark-coins-accepts-public-selection-shapes-test
  (is (= "ETH"
         (actions/normalize-portfolio-returns-benchmark-coin {:coin " ETH "})))
  (is (= "BTC"
         (actions/normalize-portfolio-returns-benchmark-coin :BTC)))
  (is (= ["BTC" "ETH" "SOL"]
         (actions/normalize-portfolio-returns-benchmark-coins
          [" BTC " {:coin "ETH"} :SOL "BTC" nil {:symbol "DOGE"} " "])))
  (is (= ["BTC"]
         (actions/normalize-portfolio-returns-benchmark-coins {:coin "BTC"}))))

(deftest selected-portfolio-vault-benchmark-addresses-normalizes-selected-vaults-test
  (is (= "0xabc"
         (actions/vault-benchmark-address " Vault:0xABC ")))
  (is (nil? (actions/vault-benchmark-address "BTC")))
  (is (= ["0xabc" "0xdef"]
         (actions/selected-portfolio-vault-benchmark-addresses
          {:portfolio-ui {:returns-benchmark-coins ["Vault:0xABC"
                                                    "BTC"
                                                    {:coin "vault:0xDEF"}
                                                    "vault:0xabc"]}})))
  (is (= ["0xabc"]
         (actions/selected-portfolio-vault-benchmark-addresses
          {:portfolio-ui {:returns-benchmark-coin "vault:0xABC"}}))))

(deftest ensure-portfolio-vault-benchmark-effects-fetches-only-missing-data-test
  (is (= []
         (actions/ensure-portfolio-vault-benchmark-effects
          {:portfolio-ui {:returns-benchmark-suggestions-open? false
                          :returns-benchmark-coins []}})))
  (is (= [[:effects/api-fetch-vault-index]
          [:effects/api-fetch-vault-summaries]
          [:effects/api-fetch-vault-benchmark-details "0xaaa"]]
         (actions/ensure-portfolio-vault-benchmark-effects
          {:portfolio-ui {:returns-benchmark-suggestions-open? true
                          :returns-benchmark-coins ["vault:0xAAA"
                                                    "vault:0xBBB"
                                                    "BTC"
                                                    "vault:0xCCC"]}
           :vaults {:benchmark-details-by-address {"0xbbb" {:name "cached"}}
                    :loading {:benchmark-details-by-address {"0xccc" true}}}})))
  (is (= [[:effects/api-fetch-vault-benchmark-details "0xaaa"]]
         (actions/ensure-portfolio-vault-benchmark-effects
          {:portfolio-ui {:returns-benchmark-coins ["vault:0xAAA"]}
           :vaults {:merged-index-rows [{:address "0xaaa"}]}})))
  (is (= []
         (actions/ensure-portfolio-vault-benchmark-effects
          {:portfolio-ui {:returns-benchmark-coins ["vault:0xabc"]}
           :vaults {:merged-index-rows [{:address "0xabc"}]
                    :loading {:benchmark-details-by-address {"0xabc" true}}}})))
  (is (= [[:effects/save [:portfolio-ui :returns-benchmark-suggestions-open?] true]]
         (actions/set-portfolio-returns-benchmark-suggestions-open
          {:vaults {:merged-index-rows [{:address "0xaaa"}]}}
          true))))

(deftest select-and-clear-portfolio-returns-benchmark-test
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] ["SPY" "QQQ"]]
            [[:portfolio-ui :returns-benchmark-coin] "SPY"]
            [[:portfolio-ui :returns-benchmark-search] ""]
            [[:portfolio-ui :returns-benchmark-suggestions-open?] false]]]
          replace-shareable-route-query-effect
          [:effects/fetch-candle-snapshot :coin "QQQ" :interval :1d :bars 5000]]
         (actions/select-portfolio-returns-benchmark
          {:portfolio-ui {:summary-time-range :all-time
                          :returns-benchmark-coins ["SPY"]}}
          "QQQ")))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] ["SPY"]]
            [[:portfolio-ui :returns-benchmark-coin] "SPY"]
            [[:portfolio-ui :returns-benchmark-search] ""]
            [[:portfolio-ui :returns-benchmark-suggestions-open?] false]]]
          replace-shareable-route-query-effect]
         (actions/select-portfolio-returns-benchmark
          {:portfolio-ui {:summary-time-range :month
                          :returns-benchmark-coins ["SPY"]}}
          "SPY")))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] ["SPY"]]
            [[:portfolio-ui :returns-benchmark-coin] "SPY"]
            [[:portfolio-ui :returns-benchmark-search] ""]
            [[:portfolio-ui :returns-benchmark-suggestions-open?] false]]]
          replace-shareable-route-query-effect]
         (actions/select-portfolio-returns-benchmark
          {:portfolio-ui {:summary-time-range :all-time
                          :returns-benchmark-coin "SPY"}}
          "SPY")))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] ["vault:0xAAA"]]
            [[:portfolio-ui :returns-benchmark-coin] "vault:0xAAA"]
            [[:portfolio-ui :returns-benchmark-search] ""]
            [[:portfolio-ui :returns-benchmark-suggestions-open?] false]]]
          replace-shareable-route-query-effect]
         (actions/select-portfolio-returns-benchmark
          {:portfolio-ui {:summary-time-range :week
                          :returns-benchmark-coins []}
           :vaults {:details-by-address {"0xaaa" {:address "0xaaa"}}}}
          "vault:0xAAA")))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] ["vault:0xabc"]]
            [[:portfolio-ui :returns-benchmark-coin] "vault:0xabc"]
            [[:portfolio-ui :returns-benchmark-search] ""]
            [[:portfolio-ui :returns-benchmark-suggestions-open?] false]]]
          replace-shareable-route-query-effect]
         (actions/select-portfolio-returns-benchmark
          {:portfolio-ui {:summary-time-range :week
                          :returns-benchmark-coins ["vault:0xabc"]}}
          "vault:0xabc")))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] ["vault:0x1234567890abcdef1234567890abcdef12345678"]]
            [[:portfolio-ui :returns-benchmark-coin] "vault:0x1234567890abcdef1234567890abcdef12345678"]
            [[:portfolio-ui :returns-benchmark-search] ""]
            [[:portfolio-ui :returns-benchmark-suggestions-open?] false]]]
          replace-shareable-route-query-effect
          [:effects/api-fetch-vault-benchmark-details "0x1234567890abcdef1234567890abcdef12345678"]]
         (actions/select-portfolio-returns-benchmark
          {:portfolio-ui {:summary-time-range :all-time
                          :returns-benchmark-coins []}}
          "vault:0x1234567890abcdef1234567890abcdef12345678")))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] []]
            [[:portfolio-ui :returns-benchmark-coin] nil]
            [[:portfolio-ui :returns-benchmark-search] ""]
            [[:portfolio-ui :returns-benchmark-suggestions-open?] false]]]
          replace-shareable-route-query-effect]
         (actions/select-portfolio-returns-benchmark
          {:portfolio-ui {:summary-time-range :day}}
          "   ")))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] []]
            [[:portfolio-ui :returns-benchmark-coin] nil]
            [[:portfolio-ui :returns-benchmark-search] ""]
            [[:portfolio-ui :returns-benchmark-suggestions-open?] false]]]
          replace-shareable-route-query-effect]
         (actions/clear-portfolio-returns-benchmark {}))))

(deftest remove-portfolio-returns-benchmark-test
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] ["QQQ"]]
            [[:portfolio-ui :returns-benchmark-coin] "QQQ"]]]
          replace-shareable-route-query-effect]
         (actions/remove-portfolio-returns-benchmark
          {:portfolio-ui {:returns-benchmark-coins ["SPY" "QQQ"]}}
          "SPY")))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] []]
            [[:portfolio-ui :returns-benchmark-coin] nil]]]
          replace-shareable-route-query-effect]
         (actions/remove-portfolio-returns-benchmark
          {:portfolio-ui {:returns-benchmark-coin "SPY"}}
          "SPY")))
  (is (= []
         (actions/remove-portfolio-returns-benchmark
          {:portfolio-ui {:returns-benchmark-coins ["SPY"]}}
          "   "))))

(deftest handle-portfolio-returns-benchmark-search-keydown-test
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] ["SPY"]]
            [[:portfolio-ui :returns-benchmark-coin] "SPY"]
            [[:portfolio-ui :returns-benchmark-search] ""]
            [[:portfolio-ui :returns-benchmark-suggestions-open?] false]]]
          replace-shareable-route-query-effect
          [:effects/fetch-candle-snapshot :coin "SPY" :interval :15m :bars 800]]
         (actions/handle-portfolio-returns-benchmark-search-keydown
          {:portfolio-ui {:summary-time-range :week}}
          "Enter"
          "SPY")))
  (is (= [[:effects/save-many
           [[[:portfolio-ui :returns-benchmark-coins] ["vault:0x1234567890abcdef1234567890abcdef12345678"]]
            [[:portfolio-ui :returns-benchmark-coin] "vault:0x1234567890abcdef1234567890abcdef12345678"]
            [[:portfolio-ui :returns-benchmark-search] ""]
            [[:portfolio-ui :returns-benchmark-suggestions-open?] false]]]
          replace-shareable-route-query-effect
          [:effects/api-fetch-vault-benchmark-details "0x1234567890abcdef1234567890abcdef12345678"]]
         (actions/handle-portfolio-returns-benchmark-search-keydown
          {:portfolio-ui {:summary-time-range :week}}
          "Enter"
          "vault:0x1234567890abcdef1234567890abcdef12345678")))
  (is (= []
         (actions/handle-portfolio-returns-benchmark-search-keydown
          {:portfolio-ui {:summary-time-range :week}}
          "Enter"
          nil)))
  (is (= [[:effects/save [:portfolio-ui :returns-benchmark-suggestions-open?] false]]
         (actions/handle-portfolio-returns-benchmark-search-keydown
          {}
          "Escape"
          "SPY")))
  (is (= []
         (actions/handle-portfolio-returns-benchmark-search-keydown
          {}
          "ArrowDown"
          "SPY"))))

(deftest returns-benchmark-candle-request-selects-range-specific-window-test
  (is (= {:interval :5m :bars 400}
         (actions/returns-benchmark-candle-request :day)))
  (is (= {:interval :15m :bars 800}
         (actions/returns-benchmark-candle-request :week)))
  (is (= {:interval :1h :bars 800}
         (actions/returns-benchmark-candle-request :month)))
  (is (= {:interval :4h :bars 720}
         (actions/returns-benchmark-candle-request "3M")))
  (is (= {:interval :8h :bars 720}
         (actions/returns-benchmark-candle-request :six-month)))
  (is (= {:interval :12h :bars 900}
         (actions/returns-benchmark-candle-request :one-year)))
  (is (= {:interval :1d :bars 900}
         (actions/returns-benchmark-candle-request "2Y")))
  (is (= {:interval :12h :bars 900}
         (actions/returns-benchmark-candle-request "not-a-range")))
  (is (= {:interval :1d :bars 5000}
         (actions/returns-benchmark-candle-request :all-time))))
