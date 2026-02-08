(ns hyperopen.views.l2-orderbook-view-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.websocket.trades :as ws-trades]
            [hyperopen.views.l2-orderbook-view :as view]))

(defn- class-values [class-attr]
  (cond
    (nil? class-attr) []
    (string? class-attr) (remove str/blank? (str/split class-attr #"\s+"))
    (sequential? class-attr) (mapcat class-values class-attr)
    :else []))

(defn- classes-from-tag [tag]
  (if (keyword? tag)
    (let [parts (str/split (name tag) #"\.")]
      (if (> (count parts) 1)
        (rest parts)
        []))
    []))

(defn- collect-all-classes [node]
  (cond
    (vector? node)
    (let [attrs (when (map? (second node)) (second node))
          children (if attrs (drop 2 node) (drop 1 node))]
      (concat (classes-from-tag (first node))
              (class-values (:class attrs))
              (mapcat collect-all-classes children)))

    (seq? node)
    (mapcat collect-all-classes node)

    :else []))

(deftest symbol-resolution-test
  (testing "market metadata takes precedence"
    (is (= "PUMP" (view/resolve-base-symbol "PUMP" {:base "PUMP"})))
    (is (= "USDC" (view/resolve-quote-symbol "PUMP" {:quote "USDC"}))))

  (testing "coin fallback works for spot and dex-perp strings"
    (is (= "PURR" (view/resolve-base-symbol "PURR/USDC" nil)))
    (is (= "USDC" (view/resolve-quote-symbol "PURR/USDC" nil)))
    (is (= "GOLD" (view/resolve-base-symbol "hyna:GOLD" nil)))
    (is (= "USDC" (view/resolve-quote-symbol "hyna:GOLD" nil)))))

(deftest quote-vs-base-size-total-test
  (let [order {:px "2.5"
               :sz "100"
               :cum-size 250
               :cum-value 625}
        base-unit :base
        quote-unit :quote]
    (testing "size conversion switches between base and quote units"
      (is (= 100 (view/order-size-for-unit order base-unit)))
      (is (= 250 (view/order-size-for-unit order quote-unit))))

    (testing "cumulative total switches between base and quote units"
      (is (= 250 (view/order-total-for-unit order base-unit)))
      (is (= 625 (view/order-total-for-unit order quote-unit))))

    (testing "formatted quote values are rounded whole numbers"
      (is (= "250" (view/format-order-size order quote-unit)))
      (is (= "625" (view/format-order-total order quote-unit))))

    (testing "formatted base values preserve raw size and cumulative precision"
      (is (= "100" (view/format-order-size order base-unit)))
      (is (= "250" (view/format-order-total order base-unit))))))

(deftest cumulative-totals-test
  (let [orders [{:px "2" :sz "3"}
                {:px "4" :sz "5"}]
        totals (view/calculate-cumulative-totals orders)]
    (is (= 2 (count totals)))
    (is (= 3 (:cum-size (first totals))))
    (is (= 6 (:cum-value (first totals))))
    (is (= 8 (:cum-size (second totals))))
    (is (= 26 (:cum-value (second totals))))))

(deftest normalize-orderbook-tab-test
  (testing "valid tabs pass through and invalid tabs fallback to orderbook"
    (is (= :orderbook (view/normalize-orderbook-tab :orderbook)))
    (is (= :trades (view/normalize-orderbook-tab :trades)))
    (is (= :trades (view/normalize-orderbook-tab "trades")))
    (is (= :orderbook (view/normalize-orderbook-tab "invalid")))
    (is (= :orderbook (view/normalize-orderbook-tab nil)))))

(deftest trade-time-formatting-test
  (testing "seconds and milliseconds normalize to the same HH:MM:SS output"
    (is (= 1700000000000 (view/trade-time->ms 1700000000)))
    (is (= 1700000000000 (view/trade-time->ms 1700000000000)))
    (let [formatted-seconds (view/format-trade-time 1700000000)
          formatted-millis (view/format-trade-time 1700000000000)]
      (is (= formatted-seconds formatted-millis))
      (is (re-matches #"\d{2}:\d{2}:\d{2}" formatted-seconds)))))

(deftest trade-side-class-test
  (testing "trade sides map to expected price classes"
    (is (= "text-green-400" (view/trade-side->price-class "B")))
    (is (= "text-red-400" (view/trade-side->price-class "A")))
    (is (= "text-red-400" (view/trade-side->price-class "S")))
    (is (= "text-gray-100" (view/trade-side->price-class "X")))))

(deftest recent-trades-for-coin-test
  (testing "mixed coin trades are filtered to the selected coin and sorted newest first"
    (with-redefs [ws-trades/get-recent-trades
                  (fn []
                    [{:coin "ETH" :px "3010.5" :sz "0.2" :side "B" :time 1700000001}
                     {:coin "BTC" :px "61500.1" :sz "0.03" :side "A" :time 1700000003}
                     {:coin "BTC" :px "61499.9" :sz "0.01" :side "B" :time 1700000002}])]
      (let [filtered-trades (view/recent-trades-for-coin "BTC")]
        (is (= 2 (count filtered-trades)))
        (is (every? #(= "BTC" (:coin %)) filtered-trades))
        (is (>= (:time-ms (first filtered-trades))
                (:time-ms (second filtered-trades))))))))

(deftest orderbook-panel-uses-base-background-and-border-tokens-test
  (let [panel (view/l2-orderbook-panel "BTC"
                                       {:market-type :perp
                                        :base "BTC"
                                        :quote "USDC"
                                        :szDecimals 4}
                                       {:bids [{:px "99" :sz "2"}]
                                        :asks [{:px "101" :sz "1"}]}
                                       {:size-unit :base
                                        :size-unit-dropdown-visible? false
                                        :price-aggregation-dropdown-visible? false
                                        :price-aggregation-by-coin {"BTC" :full}})
        classes (set (collect-all-classes panel))]
    (is (contains? classes "bg-base-100"))
    (is (contains? classes "border-base-300"))
    (is (not (contains? classes "bg-gray-900")))))
