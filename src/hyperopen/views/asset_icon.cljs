(ns hyperopen.views.asset-icon
  (:require [clojure.string :as str]))

(def ^:private hyperliquid-coin-icon-base-url
  "https://app.hyperliquid.xyz/coins/")

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn- spot-icon-key
  [coin]
  (let [coin* (non-blank-text coin)]
    (when (and coin*
               (str/includes? coin* "/"))
      (let [[base _quote] (str/split coin* #"/" 2)
            base* (non-blank-text base)]
        (when base*
          (str base* "_spot"))))))

(defn- normalize-icon-key
  [icon-key]
  (let [icon-key* (non-blank-text icon-key)]
    (when icon-key*
      (if (and (str/starts-with? icon-key* "k")
               (not (str/starts-with? icon-key* "km:")))
        (subs icon-key* 1)
        icon-key*))))

(def ^:private icon-key-aliases
  {"abcd:USA500" "cash:USA500"
   "cash:GOLD" "xyz:GOLD"
   "cash:INTC" "xyz:INTC"
   "cash:MSFT" "xyz:MSFT"
   "cash:SILVER" "xyz:SILVER"
   "flx:BTC" "BTC"
   "flx:GAS" "GAS"
   "flx:PALLADIUM" "xyz:PALLADIUM"
   "flx:PLATINUM" "xyz:PLATINUM"
   "hyna:ADA" "ADA"
   "hyna:BCH" "BCH"
   "hyna:BNB" "BNB"
   "hyna:DOGE" "DOGE"
   "hyna:ENA" "ENA"
   "hyna:FARTCOIN" "FARTCOIN"
   "hyna:IP" "IP"
   "hyna:LINK" "LINK"
   "hyna:LIT" "LIT"
   "hyna:LTC" "LTC"
   "hyna:PUMP" "PUMP"
   "hyna:SUI" "SUI"
   "hyna:XMR" "XMR"
   "hyna:XPL" "XPL"
   "km:AAPL" "xyz:AAPL"
   "km:EUR" "xyz:EUR"
   "km:GOLD" "xyz:GOLD"
   "km:GOOGL" "xyz:GOOGL"
   "km:MU" "xyz:MU"
   "km:NVDA" "xyz:NVDA"
   "km:SILVER" "xyz:SILVER"
   "xyz:COPPER" "flx:COPPER"})

(defn- alias-icon-key
  [icon-key]
  (or (get icon-key-aliases icon-key)
      icon-key))

(defn market-icon-key
  [{:keys [coin base]}]
  (let [coin* (non-blank-text coin)
        base* (non-blank-text base)
        candidate (or (spot-icon-key coin*)
                      (when-not (str/starts-with? (or coin* "") "@")
                        coin*)
                      base*)
        normalized (normalize-icon-key candidate)
        aliased (some-> normalized alias-icon-key)]
    (when (and aliased
               (not (str/starts-with? aliased "@")))
      aliased)))

(defn market-icon-url
  [market]
  (when-let [icon-key (market-icon-key market)]
    (str hyperliquid-coin-icon-base-url icon-key ".svg")))
