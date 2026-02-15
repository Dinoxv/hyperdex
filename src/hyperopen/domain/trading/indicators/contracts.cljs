(ns hyperopen.domain.trading.indicators.contracts
  (:require [hyperopen.domain.trading.indicators.schema :as schema]))

(def ^:private valid-panes #{:overlay :separate})
(def ^:private valid-series-types #{:line :histogram})
(def ^:private valid-marker-kinds #{:fractal-high :fractal-low})
(def ^:private required-ohlc-fields #{:time :open :high :low :close})

(def ^:private volume-required-indicators
  #{:accumulation-distribution
    :chaikin-money-flow
    :chaikin-oscillator
    :ease-of-movement
    :elders-force-index
    :klinger-oscillator
    :money-flow-index
    :net-volume
    :on-balance-volume
    :price-volume-trend
    :volume
    :volume-oscillator
    :vwap
    :vwma})

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn- numeric-like?
  [value]
  (or (finite-number? value)
      (and (string? value)
           (let [parsed (js/parseFloat value)]
             (and (not (js/isNaN parsed))
                  (js/isFinite parsed))))))

(defn- numeric-like->number
  [value]
  (cond
    (finite-number? value) value
    (and (string? value)
         (numeric-like? value)) (js/parseFloat value)
    :else nil))

(defn- required-candle-fields
  [indicator-type]
  (cond-> required-ohlc-fields
    (contains? volume-required-indicators indicator-type) (conj :volume)))

(defn- valid-candle?
  [indicator-type candle]
  (and (map? candle)
       (every? (fn [field]
                 (let [value (get candle field)]
                   (if (= field :time)
                     (numeric-like? value)
                     (numeric-like? value))))
               (required-candle-fields indicator-type))))

(defn- valid-params?
  [indicator-type params]
  (let [specs (schema/indicator-param-specs indicator-type)
        valid-value?
        (fn [{:keys [kind min max]} value]
          (case kind
            :number (let [parsed (numeric-like->number value)]
                      (and (some? parsed)
                           (if (some? min) (<= min parsed) true)
                           (if (some? max) (<= parsed max) true)))
            :number-vector (and (sequential? value)
                                (every? numeric-like? value))
            true))]
    (and (map? params)
         (every? (fn [[key value]]
                   (if-let [spec (get specs key)]
                     (valid-value? spec value)
                     true))
                 params)
         (every? (fn [[key {:keys [required?]}]]
                   (if required?
                     (contains? params key)
                     true))
                 specs))))

(defn valid-indicator-input?
  [indicator-type data params]
  (and (keyword? indicator-type)
       (sequential? data)
       (every? #(valid-candle? indicator-type %) data)
       (valid-params? indicator-type params)))

(defn- valid-series?
  [series expected-length]
  (and (map? series)
       (keyword? (:id series))
       (contains? valid-series-types (:series-type series))
       (vector? (:values series))
       (= expected-length (count (:values series)))))

(defn- valid-marker?
  [marker]
  (and (map? marker)
       (string? (:id marker))
       (numeric-like? (:time marker))
       (keyword? (:kind marker))
       (contains? valid-marker-kinds (:kind marker))
       (or (nil? (:price marker))
           (numeric-like? (:price marker)))))

(defn valid-indicator-result?
  [result indicator-type expected-length]
  (and (map? result)
       (= indicator-type (:type result))
       (contains? valid-panes (:pane result))
       (vector? (:series result))
       (every? #(valid-series? % expected-length) (:series result))
       (or (nil? (:markers result))
           (and (vector? (:markers result))
                (every? valid-marker? (:markers result))))))

(defn enforce-indicator-result
  [indicator-type expected-length result]
  (when (valid-indicator-result? result indicator-type expected-length)
    result))
