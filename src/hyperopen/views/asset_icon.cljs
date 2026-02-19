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

(defn market-icon-key
  [{:keys [coin base]}]
  (let [coin* (non-blank-text coin)
        base* (non-blank-text base)
        candidate (or (spot-icon-key coin*)
                      (when-not (str/starts-with? (or coin* "") "@")
                        coin*)
                      base*)
        normalized (normalize-icon-key candidate)]
    (when (and normalized
               (not (str/starts-with? normalized "@")))
      normalized)))

(defn market-icon-url
  [market]
  (when-let [icon-key (market-icon-key market)]
    (str hyperliquid-coin-icon-base-url icon-key ".svg")))
