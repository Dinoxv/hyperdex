(ns hyperopen.trading.order-form-state
  (:require [hyperopen.domain.trading :as trading-domain]))

(def default-scale-order-count 5)
(def default-scale-skew "1.00")
(def default-ui-leverage 20)
(def default-slippage 0.5)
(def default-twap-minutes 5)

(defn default-order-form []
  {:entry-mode :limit
   :type :limit
   :side :buy
   :ui-leverage default-ui-leverage
   :size-percent 0
   :pro-order-type-dropdown-open? false
   :tpsl-panel-open? false
   :size-display ""
   :size ""
   :price ""
   :price-input-focused? false
   :trigger-px ""
   :reduce-only false
   :post-only false
   :tif :gtc
   :slippage default-slippage
   :scale {:start ""
           :end ""
           :count default-scale-order-count
           :skew default-scale-skew}
   :twap {:minutes default-twap-minutes
          :randomize true}
   :tp {:enabled? false
        :trigger ""
        :is-market true
        :limit ""}
   :sl {:enabled? false
        :trigger ""
        :is-market true
        :limit ""}
   :submitting? false
   :error nil})

(defn normalize-scale-form [scale]
  (let [raw-scale (or scale {})
        raw-skew (:skew raw-scale)
        normalized-skew (cond
                          (string? raw-skew) raw-skew
                          (number? raw-skew) (trading-domain/number->clean-string
                                               (trading-domain/normalize-scale-skew-number raw-skew)
                                               2)
                          (keyword? raw-skew) (trading-domain/number->clean-string
                                                (trading-domain/normalize-scale-skew-number raw-skew)
                                                2)
                          :else default-scale-skew)]
    {:start (or (:start raw-scale) "")
     :end (or (:end raw-scale) "")
     :count (or (:count raw-scale) default-scale-order-count)
     :skew normalized-skew}))

(defn normalize-ui-flags [form]
  (-> form
      (assoc :pro-order-type-dropdown-open? (boolean (:pro-order-type-dropdown-open? form))
             :price-input-focused? (boolean (:price-input-focused? form))
             :tpsl-panel-open? (boolean (:tpsl-panel-open? form)))
      (assoc :scale (normalize-scale-form (:scale form)))))
