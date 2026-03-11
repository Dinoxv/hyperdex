(ns hyperopen.views.chart.tooltip-core
  (:require [clojure.string :as str]))

(def ^:private default-benchmark-stroke
  "#e6edf2")

(def ^:private positive-value-classes
  ["text-[#16d6a1]"])

(def ^:private negative-value-classes
  ["text-[#ff7b72]"])

(def ^:private neutral-value-classes
  ["text-[#e6edf2]"])

(defn- finite-number?
  [value]
  (and (number? value)
       (js/isFinite value)))

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn returns-metric?
  [metric-kind]
  (= metric-kind :returns))

(defn metric-label
  [metric-kind]
  (case metric-kind
    :account-value "Account Value"
    :pnl "PNL"
    :returns "Returns"
    "Value"))

(defn metric-value-classes
  [metric-kind value]
  (let [n (if (finite-number? value) value 0)]
    (case metric-kind
      :account-value ["text-[#ff9f1a]"]
      :pnl (cond
             (pos? n) positive-value-classes
             (neg? n) negative-value-classes
             :else neutral-value-classes)
      :returns (cond
                 (pos? n) positive-value-classes
                 (neg? n) negative-value-classes
                 :else neutral-value-classes)
      neutral-value-classes)))

(defn benchmark-rows
  [{:keys [metric-kind hovered-index series]} {:keys [benchmark-enabled?
                                                      format-benchmark-value
                                                      fallback-stroke]
                                               :or {benchmark-enabled? returns-metric?
                                                    fallback-stroke default-benchmark-stroke}}]
  (if (and (benchmark-enabled? metric-kind)
           (number? hovered-index))
    (->> (or series [])
         (keep (fn [{:keys [id coin label stroke points]}]
                 (when (and (keyword? id)
                            (not= id :strategy))
                   (let [point (get (or points []) hovered-index)
                         value (:value point)
                         coin* (or (non-blank-text coin)
                                   (when (keyword? id)
                                     (name id))
                                   "benchmark")
                         label* (or (non-blank-text label)
                                    (non-blank-text coin)
                                    (when (keyword? id)
                                      (name id))
                                    "Benchmark")
                         stroke* (or (non-blank-text stroke)
                                     fallback-stroke)]
                     (when (finite-number? value)
                       {:coin coin*
                        :label label*
                        :value (format-benchmark-value value)
                        :stroke stroke*})))))
         vec)
    []))

(defn build-hover-tooltip
  [{:keys [time-range metric-kind hover-point hovered-index series]}
   {:keys [format-date
           format-time
           format-metric-value
           format-benchmark-value
           metric-label-fn
           metric-value-classes-fn
           benchmark-enabled?]
    :or {metric-label-fn metric-label
         metric-value-classes-fn metric-value-classes
         benchmark-enabled? returns-metric?}}]
  (when (map? hover-point)
    (let [time-ms (:time-ms hover-point)
          value (:value hover-point)]
      {:timestamp (if (= time-range :day)
                    (format-time time-ms)
                    (format-date time-ms))
       :metric-label (metric-label-fn metric-kind)
       :metric-value (format-metric-value metric-kind value)
       :value-classes (metric-value-classes-fn metric-kind value)
       :benchmark-values (benchmark-rows {:metric-kind metric-kind
                                          :hovered-index hovered-index
                                          :series series}
                                         {:benchmark-enabled? benchmark-enabled?
                                          :format-benchmark-value format-benchmark-value})})))
