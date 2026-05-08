(ns hyperopen.views.portfolio.optimize.black-litterman-views-model
  (:require [clojure.string :as str]
            [hyperopen.views.portfolio.optimize.instrument-display :as instrument-display]))

(defn normalize-kind
  [value fallback]
  (cond
    (keyword? value) value
    (string? value) (keyword value)
    :else fallback))

(defn instrument-label
  [universe instrument-id]
  (or (some (fn [instrument]
              (when (= instrument-id (:instrument-id instrument))
                (or (when (instrument-display/vault-instrument? instrument)
                      (instrument-display/primary-label instrument))
                    (:coin instrument)
                    (:symbol instrument)
                    (:name instrument))))
            universe)
      (some-> instrument-id
              (str/split #":")
              last)
      instrument-id
      "Select"))

(defn finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn parse-percent-text
  [value]
  (let [text (some-> value
                     str
                     str/trim
                     (str/replace #"," "")
                     (str/replace #"%" "")
                     str/trim)]
    (when (seq text)
      (let [parsed (js/Number text)]
        (when (finite-number? parsed)
          (/ parsed 100))))))

(defn pct-label
  ([value]
   (pct-label value false))
  ([value signed?]
   (if (finite-number? value)
     (let [pct (* value 100)
           abs-pct (js/Math.abs pct)
           fixed (.toFixed abs-pct 2)
           trimmed (str/replace fixed #"\.?0+$" "")
           sign (cond
                  (not signed?) ""
                  (pos? pct) "+"
                  (neg? pct) "-"
                  :else "")]
       (str sign trimmed "%"))
     "--")))

(defn display-confidence
  [value]
  (name (normalize-kind value :medium)))

(defn display-horizon
  [value]
  (-> (name (normalize-kind value :3m))
      str/upper-case))

(defn view-primary-id
  [view]
  (or (:instrument-id view)
      (:long-instrument-id view)))

(defn view-comparator-id
  [view]
  (or (:comparator-instrument-id view)
      (:short-instrument-id view)))

(defn view-direction
  [view]
  (normalize-kind (:direction view) :outperform))

(defn view-summary
  [universe view]
  (let [kind (:kind view)
        primary (instrument-label universe (view-primary-id view))
        comparator (instrument-label universe (view-comparator-id view))
        direction (view-direction view)
        return-label (pct-label (:return view) (= :absolute kind))]
    (case kind
      :relative
      (str primary " "
           (if (= :underperform direction) "<" ">")
           " " comparator " by " (pct-label (:return view)) " annualized")
      (str primary " expected return " return-label " annualized"))))
