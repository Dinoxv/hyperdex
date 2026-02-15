(ns hyperopen.views.trade.order-form-vm-submit
  (:require [clojure.string :as str]))

(defn submit-tooltip-message [required-fields market-price-missing?]
  (cond
    (seq required-fields)
    (str "Fill required fields: " (str/join ", " required-fields) ".")

    market-price-missing?
    "Load order book data before placing a market order."

    :else nil))

(defn submit-tooltip-from-policy [submit-policy]
  (submit-tooltip-message (:required-fields submit-policy)
                          (:market-price-missing? submit-policy)))
