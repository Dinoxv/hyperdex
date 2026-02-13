(ns hyperopen.domain.trading.validation
  (:require [hyperopen.domain.trading.core :as core]))

(def ^:private validation-error-specs
  {:order/size-invalid {:message "Size must be greater than 0."
                        :fields [:size]}
   :order/price-required {:message "Price is required for limit orders."
                          :fields [:price]}
   :order/trigger-required {:message "Trigger price is required for stop/take orders."
                            :fields [:trigger-px]}
   :scale/inputs-invalid {:message "Scale orders need start/end prices and count between 2 and 100."
                          :fields [:scale-start :scale-end :scale-count]}
   :scale/skew-invalid {:message "Scale skew must be greater than 0 and at most 100."
                        :fields []}
   :scale/endpoint-notional-too-small {:message "Scale start/end orders must each be at least 10 in order value."
                                       :fields []}
   :twap/minutes-invalid {:message "TWAP minutes must be greater than 0."
                          :fields [:twap-minutes]}
   :tpsl/tp-trigger-required {:message "TP trigger price is required when TP is enabled."
                              :fields [:tp-trigger]}
   :tpsl/sl-trigger-required {:message "SL trigger price is required when SL is enabled."
                              :fields [:sl-trigger]}})

(defn- validation-error [code]
  (when-let [spec (get validation-error-specs code)]
    (assoc spec :code code)))

(defn validation-error-message [error]
  (let [code (cond
               (keyword? error) error
               (map? error) (:code error)
               :else nil)]
    (or (get-in validation-error-specs [code :message])
        "Invalid order form.")))

(defn validation-errors->messages [errors]
  (->> (or errors [])
       (map validation-error-message)
       vec))

(defn validate-order-form
  ([form]
   (validate-order-form nil form))
  ([context form]
   (let [size (core/parse-num (:size form))
         price (core/parse-num (:price form))
         trigger (core/parse-num (:trigger-px form))
         scale-start (core/parse-num (get-in form [:scale :start]))
         scale-end (core/parse-num (get-in form [:scale :end]))
         scale-count (core/parse-num (get-in form [:scale :count]))
         scale-skew (get-in form [:scale :skew])
         scale-sz-decimals (or (:sz-decimals context)
                               (:sz-decimals form)
                               (get-in form [:scale :sz-decimals]))
         scale-legs (when (= :scale (:type form))
                      (core/scale-order-legs size
                                             scale-count
                                             scale-skew
                                             scale-start
                                             scale-end
                                             {:sz-decimals scale-sz-decimals}))
         start-leg (first scale-legs)
         end-leg (last scale-legs)
         start-notional (when (and (map? start-leg)
                                   (number? (:price start-leg))
                                   (number? (:size start-leg)))
                          (* (:price start-leg) (:size start-leg)))
         end-notional (when (and (map? end-leg)
                                 (number? (:price end-leg))
                                 (number? (:size end-leg)))
                        (* (:price end-leg) (:size end-leg)))
         twap-min (core/parse-num (get-in form [:twap :minutes]))
         tp-enabled? (get-in form [:tp :enabled?])
         sl-enabled? (get-in form [:sl :enabled?])
         tp-trigger (core/parse-num (get-in form [:tp :trigger]))
         sl-trigger (core/parse-num (get-in form [:sl :trigger]))
         order-type (core/normalize-order-type (:type form))]
     (cond-> []
       (or (nil? size) (<= size 0))
       (conj (validation-error :order/size-invalid))

       (and (core/limit-like-type? order-type)
            (or (nil? price) (<= price 0)))
       (conj (validation-error :order/price-required))

       (and (core/trigger-type? order-type)
            (or (nil? trigger) (<= trigger 0)))
       (conj (validation-error :order/trigger-required))

       (and (= :scale order-type)
            (or (nil? scale-start)
                (nil? scale-end)
                (not (core/valid-scale-order-count? scale-count))))
       (conj (validation-error :scale/inputs-invalid))

       (and (= :scale order-type)
            (not (core/valid-scale-skew? scale-skew)))
       (conj (validation-error :scale/skew-invalid))

       (and (= :scale order-type)
            (or (nil? start-notional)
                (nil? end-notional)
                (< start-notional core/scale-min-endpoint-notional)
                (< end-notional core/scale-min-endpoint-notional)))
       (conj (validation-error :scale/endpoint-notional-too-small))

       (and (= :twap order-type)
            (or (nil? twap-min) (<= twap-min 0)))
       (conj (validation-error :twap/minutes-invalid))

       (and tp-enabled? (or (nil? tp-trigger) (<= tp-trigger 0)))
       (conj (validation-error :tpsl/tp-trigger-required))

       (and sl-enabled? (or (nil? sl-trigger) (<= sl-trigger 0)))
       (conj (validation-error :tpsl/sl-trigger-required))))))

(def ^:private required-field-rank
  {:price 0
   :size 1
   :trigger-px 2
   :scale-start 3
   :scale-end 4
   :scale-count 5
   :twap-minutes 6
   :tp-trigger 7
   :sl-trigger 8})

(def ^:private required-field-label
  {:price "Price"
   :size "Size"
   :trigger-px "Trigger Price"
   :scale-start "Start Price"
   :scale-end "End Price"
   :scale-count "Total Orders"
   :twap-minutes "Minutes"
   :tp-trigger "TP Trigger"
   :sl-trigger "SL Trigger"})

(defn submit-required-fields
  "Map validation errors to deterministic field labels for submit guidance UI."
  [errors]
  (->> (or errors [])
       (mapcat (fn [error]
                 (let [code (cond
                              (keyword? error) error
                              (map? error) (:code error)
                              :else nil)]
                   (or (get-in validation-error-specs [code :fields]) []))))
       distinct
       (sort-by #(get required-field-rank % 999))
       (map required-field-label)
       (remove nil?)
       vec))
