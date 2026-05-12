(ns hyperopen.portfolio.optimizer.black-litterman-actions.common
  (:require [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def view-kinds
  #{:absolute
    :relative})

(def max-active-views 10)

(def editor-path
  contracts/ui-black-litterman-editor-path)

(def numeric-parameter-keys
  #{:return
    :confidence})

(def instrument-parameter-keys
  #{:instrument-id
    :comparator-instrument-id
    :long-instrument-id
    :short-instrument-id})

(def confidence-weight-by-level
  {:low 0.25
   :medium 0.5
   :high 0.75})

(def horizons
  #{:1m :3m :6m :1y})

(def relative-directions
  #{:outperform :underperform})

(def normalize-keyword-like coercion/normalize-keyword-like)

(def non-blank-text coercion/non-blank-text)

(def finite-number? coercion/finite-number?)

(def parse-number-value coercion/parse-number)

(def parse-percent-text coercion/parse-percent-text)

(def decimal->percent-text coercion/decimal->percent-text)

(defn save-draft-path-values
  [path-values]
  [[:effects/save-many
    (conj (vec path-values)
          [contracts/draft-dirty-path true])]])

(defn save-ui-path-values
  [path-values]
  [[:effects/save-many (vec path-values)]])

(defn draft-universe
  [state]
  (vec (or (get-in state contracts/draft-universe-path)
           [])))

(defn black-litterman-return-model?
  [state]
  (= :black-litterman
     (get-in state (conj contracts/draft-return-model-path :kind))))

(defn black-litterman-views
  [state]
  (vec (or (get-in state contracts/draft-return-model-views-path)
           [])))

(defn universe-instrument-ids
  [state]
  (vec (keep :instrument-id (draft-universe state))))

(defn instrument-present?
  [universe instrument-id]
  (boolean
   (some #(= instrument-id (:instrument-id %)) universe)))

(defn valid-instrument-id?
  [state instrument-id]
  (and (non-blank-text instrument-id)
       (instrument-present? (draft-universe state) instrument-id)))

(defn confidence-variance
  [confidence]
  (let [confidence* (-> (or confidence 0.5)
                        (max 0.0)
                        (min 1.0))]
    (max 0.000001 (- 1.0 confidence*))))

(defn next-view-id
  [views]
  (let [existing (set (keep :id views))]
    (loop [idx (inc (count views))]
      (let [id (str "bl_view_" idx)]
        (if (contains? existing id)
          (recur (inc idx))
          id)))))

(defn normalize-confidence-level
  [value]
  (let [level (normalize-keyword-like value)]
    (if (contains? confidence-weight-by-level level)
      level
      :medium)))

(defn confidence-weight
  [value]
  (cond
    (contains? confidence-weight-by-level value)
    (get confidence-weight-by-level value)

    (keyword? value)
    (get confidence-weight-by-level (normalize-confidence-level value))

    (string? value)
    (if-let [parsed (parse-number-value value)]
      parsed
      (get confidence-weight-by-level (normalize-confidence-level value)))

    (finite-number? value)
    value

    :else
    (get confidence-weight-by-level :medium)))

(defn confidence-level-from-view
  [view]
  (or (:confidence-level view)
      (let [confidence (or (:confidence view) 0.5)]
        (cond
          (<= confidence 0.25) :low
          (<= confidence 0.5) :medium
          :else :high))))

(defn normalize-horizon
  [value]
  (let [horizon (normalize-keyword-like value)]
    (if (contains? horizons horizon)
      horizon
      :3m)))

(defn normalize-direction
  [value]
  (let [direction (normalize-keyword-like value)]
    (if (contains? relative-directions direction)
      direction
      :outperform)))

(defn relative-weights
  [instrument-id comparator-id direction]
  (case (normalize-direction direction)
    :underperform {instrument-id -1
                   comparator-id 1}
    {instrument-id 1
     comparator-id -1}))

(defn selected-kind
  [state]
  (let [kind (normalize-keyword-like (get-in state (conj editor-path :selected-kind)))]
    (if (contains? view-kinds kind)
      kind
      :absolute)))

(defn draft-defaults
  [state kind]
  (let [ids (universe-instrument-ids state)
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

(defn- drop-nil-values
  [m]
  (into {}
        (remove (fn [[_ value]]
                  (nil? value)))
        m))

(defn editor-draft
  [state kind]
  (merge (draft-defaults state kind)
         (drop-nil-values (get-in state (conj editor-path :drafts kind)))))

(defn editor-draft-path
  [kind field]
  (conj editor-path :drafts kind field))

(defn trim-notes
  [value]
  (let [text (str (or value ""))]
    (subs text 0 (min 280 (count text)))))

(defn normalized-draft-field
  [field value]
  (case field
    :confidence (normalize-confidence-level value)
    :horizon (normalize-horizon value)
    :direction (normalize-direction value)
    :notes (trim-notes value)
    value))

(defn view-by-id
  [views view-id]
  (some #(when (= view-id (:id %)) %) views))
