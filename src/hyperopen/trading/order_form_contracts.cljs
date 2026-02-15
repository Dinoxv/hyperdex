(ns hyperopen.trading.order-form-contracts)

(def ^:private required-vm-keys
  #{:form
    :side
    :type
    :entry-mode
    :pro-dropdown-options
    :order-type-sections
    :spot?
    :hip3?
    :read-only?
    :price
    :display
    :submit})

(def ^:private required-price-keys
  #{:raw :display :focused? :fallback :context})

(def ^:private required-price-context-keys
  #{:label :mid-available?})

(def ^:private required-submit-keys
  #{:errors
    :required-fields
    :reason
    :error-message
    :tooltip
    :market-price-missing?
    :disabled?})

(def ^:private allowed-transition-keys
  #{:order-form :order-form-ui :order-form-runtime})

(defn- map-with-required-keys? [value required-keys]
  (and (map? value)
       (every? #(contains? value %) required-keys)))

(defn- runtime-shape? [runtime]
  (and (map? runtime)
       (boolean? (:submitting? runtime))
       (or (nil? (:error runtime))
           (string? (:error runtime)))))

(defn order-form-vm-valid?
  [vm]
  (and (map-with-required-keys? vm required-vm-keys)
       (map-with-required-keys? (:price vm) required-price-keys)
       (map-with-required-keys? (get-in vm [:price :context]) required-price-context-keys)
       (map-with-required-keys? (:submit vm) required-submit-keys)
       (vector? (:pro-dropdown-options vm))
       (vector? (:order-type-sections vm))
       (boolean? (:spot? vm))
       (boolean? (:hip3? vm))
       (boolean? (:read-only? vm))
       (boolean? (get-in vm [:submit :disabled?]))))

(defn transition-valid?
  [transition]
  (and (map? transition)
       (seq transition)
       (every? #(contains? allowed-transition-keys %) (keys transition))
       (or (not (contains? transition :order-form))
           (map? (:order-form transition)))
       (or (not (contains? transition :order-form-ui))
           (map? (:order-form-ui transition)))
       (or (not (contains? transition :order-form-runtime))
           (runtime-shape? (:order-form-runtime transition)))))
