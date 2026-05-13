(ns hyperopen.views.portfolio.optimize.black-litterman-views-model
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.return-inputs :as return-inputs]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
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

(def finite-number? coercion/finite-number?)

(def parse-percent-text coercion/parse-percent-text)

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

(def max-active-views 10)

(defn- draft-defaults
  [universe kind]
  (let [ids (mapv :instrument-id universe)
        [first-id second-id] ids]
    (case kind
      :relative {:instrument-id first-id
                 :comparator-instrument-id (or second-id first-id)
                 :direction :outperform
                 :return-text ""
                 :return-text-touched? false
                 :confidence :medium
                 :horizon :3m
                 :notes ""}
      {:instrument-id first-id
       :return-text ""
       :return-text-touched? false
       :confidence :medium
       :horizon :3m
       :notes ""})))

(defn selected-kind
  [editor-state]
  (let [kind (normalize-kind (:selected-kind editor-state) :absolute)]
    (if (contains? #{:absolute :relative} kind)
      kind
      :absolute)))

(defn- drop-nil-values
  [m]
  (into {}
        (remove (fn [[_ value]]
                  (nil? value)))
        m))

(defn- with-automatic-absolute-return-text
  [draft kind return-inputs-by-instrument editing?]
  (let [instrument-id (:instrument-id draft)]
    (if (and (= :absolute kind)
             (not editing?)
             instrument-id
             (not (:return-text-touched? draft))
             (nil? (parse-percent-text (:return-text draft))))
      (if-let [return-input (get return-inputs-by-instrument instrument-id)]
        (assoc draft :return-text (return-inputs/decimal->percent-text return-input))
        draft)
      draft)))

(defn selected-draft
  [universe editor-state kind return-inputs-by-instrument editing?]
  (-> (merge (draft-defaults universe kind)
             (drop-nil-values (get-in editor-state [:drafts kind])))
      (with-automatic-absolute-return-text kind return-inputs-by-instrument editing?)))

(defn pending-draft?
  [draft editing?]
  (boolean
   (or editing?
       (:return-text-touched? draft)
       (some-> (:return-text draft) str str/trim seq)
       (some-> (:notes draft) str str/trim seq))))

(defn draft-valid?
  [universe kind draft active-count editing?]
  (let [ids (set (keep :instrument-id universe))
        instrument-id (:instrument-id draft)
        comparator-id (:comparator-instrument-id draft)
        return-value (parse-percent-text (:return-text draft))]
    (and (or editing? (< active-count max-active-views))
         (contains? ids instrument-id)
         (some? return-value)
         (if (= :relative kind)
           (and (contains? ids comparator-id)
                (not= instrument-id comparator-id)
                (not (neg? return-value)))
           true))))

(defn preview-text
  [universe kind draft]
  (let [value (parse-percent-text (:return-text draft))
        asset (instrument-label universe (:instrument-id draft))]
    (if (and (:instrument-id draft) value)
      (case kind
        :relative
        (let [comparator-id (:comparator-instrument-id draft)]
          (if (and comparator-id (not= comparator-id (:instrument-id draft)))
            (str asset " "
                 (if (= :underperform (:direction draft)) "<" ">")
                 " " (instrument-label universe comparator-id)
                 " by " (pct-label value) " annualized")
            "Select a comparator asset to preview this view."))
        (str asset " expected return " (pct-label value true) " annualized"))
      "Select an asset and enter a value to preview this view.")))

(defn editor-view-model
  [draft readiness editor-state]
  (when (= :black-litterman (get-in draft [:return-model :kind]))
    (let [universe (vec (:universe draft))
          views (vec (get-in draft [:return-model :views]))
          kind (selected-kind editor-state)
          errors (or (:errors editor-state) {})
          editing-view-id (:editing-view-id editor-state)
          editing? (boolean editing-view-id)
          return-inputs-by-instrument (return-inputs/readiness-inputs-by-instrument readiness)
          draft* (selected-draft universe editor-state kind return-inputs-by-instrument editing?)
          valid? (draft-valid? universe kind draft* (count views) editing?)
          pending? (pending-draft? draft* editing?)]
      {:universe universe
       :views views
       :kind kind
       :errors errors
       :editing? editing?
       :draft draft*
       :valid? valid?
       :pending? pending?
       :clear-open? (true? (:clear-confirmation-open? editor-state))})))
