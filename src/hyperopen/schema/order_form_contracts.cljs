(ns hyperopen.schema.order-form-contracts
  (:require [cljs.spec.alpha :as s]))

(def ^:private required-vm-keys
  #{:form
    :side
    :type
    :entry-mode
    :pro-dropdown-open?
    :tpsl-panel-open?
    :pro-dropdown-options
    :pro-tab-label
    :controls
    :spot?
    :hip3?
    :read-only?
    :display
    :ui-leverage
    :next-leverage
    :size-percent
    :display-size-percent
    :notch-overlap-threshold
    :size-display
    :price
    :quote-symbol
    :scale-preview-lines
    :error
    :submitting?
    :submit})

(def ^:private required-price-keys
  #{:raw :display :focused? :fallback :context})

(def ^:private required-price-context-keys
  #{:label :mid-available?})

(def ^:private required-display-keys
  #{:available-to-trade
    :current-position
    :liquidation-price
    :order-value
    :margin-required
    :slippage
    :fees})

(def ^:private required-scale-preview-keys
  #{:start :end})

(def ^:private required-submit-keys
  #{:form
    :errors
    :required-fields
    :reason
    :error-message
    :tooltip
    :market-price-missing?
    :disabled?})

(def ^:private required-controls-keys
  #{:limit-like?
    :show-limit-like-controls?
    :show-tpsl-toggle?
    :show-tpsl-panel?
    :show-post-only?
    :show-scale-preview?
    :show-liquidation-row?
    :show-slippage-row?})

(def ^:private allowed-transition-keys
  #{:order-form :order-form-ui :order-form-runtime})

(defn- map-with-exact-keys?
  [value exact-keys]
  (and (map? value)
       (= exact-keys (set (keys value)))))

(defn- map-with-required-keys?
  [value required-keys]
  (and (map? value)
       (every? #(contains? value %) required-keys)))

(defn- price-context-shape?
  [price-context]
  (and (map-with-exact-keys? price-context required-price-context-keys)
       (string? (:label price-context))
       (boolean? (:mid-available? price-context))))

(defn- price-shape?
  [price]
  (and (map-with-exact-keys? price required-price-keys)
       (string? (:raw price))
       (string? (:display price))
       (boolean? (:focused? price))
       (or (nil? (:fallback price)) (string? (:fallback price)))
       (price-context-shape? (:context price))))

(defn- submit-shape?
  [submit]
  (and (map-with-exact-keys? submit required-submit-keys)
       (map? (:form submit))
       (vector? (:errors submit))
       (vector? (:required-fields submit))
       (or (nil? (:reason submit)) (keyword? (:reason submit)))
       (or (nil? (:error-message submit)) (string? (:error-message submit)))
       (or (nil? (:tooltip submit)) (string? (:tooltip submit)))
       (boolean? (:market-price-missing? submit))
       (boolean? (:disabled? submit))))

(defn- controls-shape?
  [controls]
  (and (map-with-exact-keys? controls required-controls-keys)
       (every? true? (map boolean? (vals controls)))))

(defn- display-shape?
  [display]
  (and (map-with-exact-keys? display required-display-keys)
       (every? string? (vals display))))

(defn- scale-preview-shape?
  [preview]
  (and (map-with-exact-keys? preview required-scale-preview-keys)
       (string? (:start preview))
       (string? (:end preview))))

(defn- order-form-vm-shape?
  [vm]
  (and (map-with-exact-keys? vm required-vm-keys)
       (map? (:form vm))
       (keyword? (:side vm))
       (keyword? (:type vm))
       (keyword? (:entry-mode vm))
       (boolean? (:pro-dropdown-open? vm))
       (boolean? (:tpsl-panel-open? vm))
       (vector? (:pro-dropdown-options vm))
       (every? keyword? (:pro-dropdown-options vm))
       (string? (:pro-tab-label vm))
       (boolean? (:spot? vm))
       (boolean? (:hip3? vm))
       (boolean? (:read-only? vm))
       (number? (:ui-leverage vm))
       (number? (:next-leverage vm))
       (number? (:size-percent vm))
       (string? (:display-size-percent vm))
       (number? (:notch-overlap-threshold vm))
       (string? (:size-display vm))
       (string? (:quote-symbol vm))
       (or (nil? (:error vm)) (string? (:error vm)))
       (boolean? (:submitting? vm))
       (display-shape? (:display vm))
       (price-shape? (:price vm))
       (scale-preview-shape? (:scale-preview-lines vm))
       (controls-shape? (:controls vm))
       (submit-shape? (:submit vm))))

(defn- runtime-shape?
  [runtime]
  (and (map-with-exact-keys? runtime #{:submitting? :error})
       (boolean? (:submitting? runtime))
       (or (nil? (:error runtime))
           (string? (:error runtime)))))

(defn- transition-shape?
  [transition]
  (and (map-with-required-keys? transition #{})
       (seq transition)
       (every? #(contains? allowed-transition-keys %) (keys transition))
       (or (not (contains? transition :order-form))
           (map? (:order-form transition)))
       (or (not (contains? transition :order-form-ui))
           (map? (:order-form-ui transition)))
       (or (not (contains? transition :order-form-runtime))
           (runtime-shape? (:order-form-runtime transition)))))

(s/def ::order-form-vm order-form-vm-shape?)
(s/def ::order-form-transition transition-shape?)

(defn order-form-vm-valid?
  [vm]
  (s/valid? ::order-form-vm vm))

(defn order-form-transition-valid?
  [transition]
  (s/valid? ::order-form-transition transition))

(defn assert-order-form-vm!
  [vm context]
  (when-not (order-form-vm-valid? vm)
    (throw (js/Error.
            (str "order-form VM schema validation failed. "
                 "context=" (pr-str context)
                 " value=" (pr-str vm)
                 " explain=" (pr-str (s/explain-data ::order-form-vm vm))))))
  vm)

(defn assert-order-form-transition!
  [transition context]
  (when-not (order-form-transition-valid? transition)
    (throw (js/Error.
            (str "order-form transition schema validation failed. "
                 "context=" (pr-str context)
                 " value=" (pr-str transition)
                 " explain=" (pr-str (s/explain-data ::order-form-transition transition))))))
  transition)
