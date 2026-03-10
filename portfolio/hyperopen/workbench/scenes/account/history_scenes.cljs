(ns hyperopen.workbench.scenes.account.history-scenes
  (:require [portfolio.replicant :as portfolio]
            [hyperopen.workbench.support.layout :as layout]
            [hyperopen.views.account-info.projections.trades :as trade-projections]
            [hyperopen.views.account-info.tabs.funding-history :as funding-history]
            [hyperopen.views.account-info.tabs.order-history :as order-history]
            [hyperopen.views.account-info.tabs.trade-history :as trade-history]))

(portfolio/configure-scenes
  {:title "History"
   :collection :account})

(def trade-fills
  [{:tid 1
    :coin "xyz:NVDA"
    :side "B"
    :sz "0.500"
    :px "187.88"
    :tradeValue "93.94"
    :fee "0.01"
    :closedPnl "-0.01"
    :time 1700000000000
    :hash "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}
   {:tid 2
    :coin "BTC"
    :dir "Close Long (Price Improved)"
    :side "A"
    :sz "0.100"
    :px "102420.0"
    :tradeValue "10242"
    :fee "2.10"
    :closedPnl "145.22"
    :liquidation {:markPx "102390.2"}
    :time 1700003600000}])

(def funding-rows
  [{:id "funding-1"
    :time-ms 1700000000000
    :coin "BTC"
    :position-size-raw 0.5
    :payment-usdc-raw -0.42
    :funding-rate-raw 0.0006}
   {:id "funding-2"
    :time-ms 1700003600000
    :coin "ETH"
    :position-size-raw -1.2
    :payment-usdc-raw 1.14
    :funding-rate-raw -0.0003}])

(def order-rows
  [{:order {:coin "xyz:NVDA"
            :oid 307891000622
            :side "B"
            :origSz "0.500"
            :remainingSz "0.000"
            :limitPx "0"
            :orderType "Market"
            :reduceOnly false
            :isTrigger false
            :timestamp 1700000000000}
    :status "filled"
    :statusTimestamp 1700000000500}
   {:order {:coin "PUMP"
            :oid 275043415805
            :side "A"
            :origSz "11386"
            :remainingSz "11386"
            :limitPx "0.001000"
            :orderType "Limit"
            :reduceOnly true
            :isTrigger false
            :timestamp 1700003600000}
    :status "canceled"
    :statusTimestamp 1700003600500}])

(def trade-market-by-key
  {"xyz:NVDA" {:coin "xyz:NVDA" :symbol "NVDA-USD"}
   "BTC" {:coin "BTC" :symbol "BTC-USDC"}})

(defn- trade-history-state
  ([] (trade-history-state {}))
  ([overrides]
   (merge {:sort {:column "Time" :direction :desc}
           :direction-filter :all
           :coin-search ""
           :market-by-key trade-market-by-key}
          overrides)))

(defn- history-panel
  [content]
  (layout/page-shell
   (layout/desktop-shell
    (layout/panel-shell {:class ["h-[660px]"]}
     content))))

(defn- mobile-history-panel
  [content]
  (layout/page-shell
   (layout/mobile-shell
    (layout/panel-shell {:class ["h-[680px]" "p-0"]}
     content))))

(portfolio/defscene trade-history-default
  []
  (history-panel
   (trade-history/trade-history-tab-content
    trade-fills
    (trade-history-state))))

(portfolio/defscene trade-history-mobile-closed
  []
  (mobile-history-panel
   (trade-history/trade-history-tab-content
    trade-fills
    (trade-history-state))))

(portfolio/defscene trade-history-mobile-open
  []
  (mobile-history-panel
   (trade-history/trade-history-tab-content
    trade-fills
    (trade-history-state
     {:mobile-expanded-card {:trade-history (trade-projections/trade-history-row-id (second trade-fills))}}))))

(portfolio/defscene trade-history-empty
  []
  (history-panel
   (trade-history/trade-history-tab-content [] (trade-history-state))))

(portfolio/defscene funding-history-default
  []
  (history-panel
   (funding-history/funding-history-tab-content
    funding-rows
    {:sort {:column "Time" :direction :desc}
     :page-size 25
     :page 1
     :page-input "1"
     :loading? false}
    funding-rows)))

(portfolio/defscene order-history-mixed
  []
  (history-panel
   (order-history/order-history-tab-content
    order-rows
    {:sort {:column "Time" :direction :desc}
     :status-filter :all
     :loading? false
     :market-by-key {"xyz:NVDA" {:coin "xyz:NVDA" :symbol "NVDA-USD"}
                     "PUMP" {:coin "PUMP" :symbol "PUMP-USDC"}}})))

(portfolio/defscene order-history-loading
  []
  (history-panel
   (order-history/order-history-tab-content
    []
    {:sort {:column "Time" :direction :desc}
     :status-filter :all
     :loading? true})))
