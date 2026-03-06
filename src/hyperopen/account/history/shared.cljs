(ns hyperopen.account.history.shared
  (:require [clojure.string :as str]
            [hyperopen.utils.parse :as parse-utils]))

(def ^:private order-history-page-size-options
  #{25 50 100})

(def default-order-history-page-size
  50)

(def ^:private account-info-coin-search-tabs
  #{:balances :positions :open-orders :trade-history :order-history})

(defn normalize-order-history-page-size
  ([value]
   (normalize-order-history-page-size value nil))
  ([value locale]
   (let [candidate (parse-utils/parse-localized-int-value value locale)]
     (if (contains? order-history-page-size-options candidate)
       candidate
       default-order-history-page-size))))

(defn normalize-order-history-page
  ([value]
   (normalize-order-history-page value nil nil))
  ([value max-page]
   (normalize-order-history-page value max-page nil))
  ([value max-page locale]
   (let [candidate (max 1 (or (parse-utils/parse-localized-int-value value locale) 1))
         max-page* (when (some? max-page)
                     (max 1 (or (parse-utils/parse-localized-int-value max-page locale) 1)))]
     (if max-page*
       (min candidate max-page*)
       candidate))))

(defn normalize-account-info-tab
  [tab]
  (let [tab* (cond
               (keyword? tab) tab
               (string? tab) (keyword (str/lower-case tab))
               :else :balances)]
    (if (contains? account-info-coin-search-tabs tab*)
      tab*
      :balances)))

(defn normalize-coin-search-value
  [value]
  (if (string? value)
    value
    (str (or value ""))))
