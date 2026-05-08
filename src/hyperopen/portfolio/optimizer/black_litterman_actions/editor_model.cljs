(ns hyperopen.portfolio.optimizer.black-litterman-actions.editor-model
  (:require [hyperopen.portfolio.optimizer.application.return-inputs :as return-inputs]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.black-litterman-actions.common :as common]
            [hyperopen.portfolio.optimizer.black-litterman-actions.views :as views]))

(def missing-view-error-message
  "Add a view before running Use my views.")

(defn draft->view
  [kind draft view-id]
  (let [return-value (common/parse-percent-text (:return-text draft))
        confidence-level (common/normalize-confidence-level (:confidence draft))
        confidence (common/confidence-weight confidence-level)
        horizon (common/normalize-horizon (:horizon draft))
        notes (common/non-blank-text (:notes draft))]
    (case kind
      :relative
      (let [instrument-id (common/non-blank-text (:instrument-id draft))
            comparator-id (common/non-blank-text (:comparator-instrument-id draft))
            direction (common/normalize-direction (:direction draft))]
        (cond-> {:id view-id
                 :kind :relative
                 :instrument-id instrument-id
                 :comparator-instrument-id comparator-id
                 :direction direction
                 :return return-value
                 :confidence-level confidence-level
                 :confidence confidence
                 :confidence-variance (common/confidence-variance confidence)
                 :horizon horizon
                 :weights (when (and instrument-id comparator-id)
                            (common/relative-weights instrument-id comparator-id direction))}
          notes (assoc :notes notes)))

      (let [instrument-id (common/non-blank-text (:instrument-id draft))]
        (cond-> {:id view-id
                 :kind :absolute
                 :instrument-id instrument-id
                 :return return-value
                 :confidence-level confidence-level
                 :confidence confidence
                 :confidence-variance (common/confidence-variance confidence)
                 :horizon horizon
                 :weights (when instrument-id {instrument-id 1})}
          notes (assoc :notes notes))))))

(defn validate-draft
  [state kind draft editing?]
  (let [return-value (common/parse-percent-text (:return-text draft))
        instrument-id (common/non-blank-text (:instrument-id draft))
        comparator-id (common/non-blank-text (:comparator-instrument-id draft))
        views (common/black-litterman-views state)]
    (cond-> {}
      (not (common/black-litterman-return-model? state))
      (assoc :model "Use My Views must be selected.")

      (and (not editing?) (>= (count views) common/max-active-views))
      (assoc :max "Maximum of 10 active views reached.")

      (not (common/valid-instrument-id? state instrument-id))
      (assoc :instrument-id "Select an asset.")

      (nil? return-value)
      (assoc :return-text "Enter a valid percentage.")

      (and (= :relative kind)
           (not (common/valid-instrument-id? state comparator-id)))
      (assoc :comparator-instrument-id "Select a comparator asset.")

      (and (= :relative kind)
           instrument-id
           comparator-id
           (= instrument-id comparator-id))
      (assoc :comparator-instrument-id "Choose a different comparator asset.")

      (and (= :relative kind)
           (some? return-value)
           (neg? return-value))
      (assoc :return-text "Spread must be positive. Use direction to express underperformance."))))

(defn- automatic-return-inputs
  [state]
  (return-inputs/readiness-inputs-by-instrument
   (setup-readiness/build-readiness state)))

(defn with-automatic-absolute-return-text
  [state kind draft editing?]
  (let [instrument-id (common/non-blank-text (:instrument-id draft))]
    (if (and (= :absolute kind)
             (not editing?)
             instrument-id
             (not (:return-text-touched? draft))
             (nil? (common/parse-percent-text (:return-text draft))))
      (if-let [return-input (get (automatic-return-inputs state) instrument-id)]
        (assoc draft :return-text (common/decimal->percent-text return-input))
        draft)
      draft)))

(defn reset-draft-after-save
  [draft]
  (assoc draft
         :return-text ""
         :return-text-touched? false
         :notes ""))

(defn view->draft
  [view]
  (let [kind (:kind view)]
    (case kind
      :relative {:instrument-id (views/view-primary-instrument-id view)
                 :comparator-instrument-id (views/view-comparator-instrument-id view)
                 :direction (common/normalize-direction (:direction view))
                 :return-text (common/decimal->percent-text (:return view))
                 :return-text-touched? true
                 :confidence (common/normalize-confidence-level
                              (common/confidence-level-from-view view))
                 :horizon (common/normalize-horizon (:horizon view))
                 :notes (or (:notes view) "")}
      {:instrument-id (:instrument-id view)
       :return-text (common/decimal->percent-text (:return view))
       :return-text-touched? true
       :confidence (common/normalize-confidence-level
                    (common/confidence-level-from-view view))
       :horizon (common/normalize-horizon (:horizon view))
       :notes (or (:notes view) "")})))

(defn- editing-view-id
  [state]
  (common/non-blank-text
   (get-in state (conj common/editor-path :editing-view-id))))

(defn- pending-draft?
  [draft editing?]
  (boolean
   (or editing?
       (:return-text-touched? draft)
       (common/non-blank-text (:return-text draft))
       (common/non-blank-text (:notes draft)))))

(defn editor-view-result
  [state]
  (let [kind (common/selected-kind state)
        editing-view-id* (editing-view-id state)
        editing? (boolean editing-view-id*)
        draft (with-automatic-absolute-return-text
                state
                kind
                (common/editor-draft state kind)
                editing?)
        errors (validate-draft state kind draft editing?)
        views* (common/black-litterman-views state)]
    (if (seq errors)
      {:status :invalid
       :kind kind
       :draft draft
       :errors errors}
      (let [view-id (or editing-view-id* (common/next-view-id views*))
            view (draft->view kind draft view-id)
            saved-views (if editing?
                          (mapv (fn [existing]
                                  (if (= view-id (:id existing))
                                    view
                                    existing))
                                views*)
                          (conj views* view))]
        {:status :valid
         :kind kind
         :draft draft
         :view view
         :views saved-views
         :editing-view-id editing-view-id*}))))

(defn pending-editor-view-result
  [state]
  (if (not (common/black-litterman-return-model? state))
    {:status :none}
    (let [kind (common/selected-kind state)
          editing? (boolean (editing-view-id state))
          draft (common/editor-draft state kind)]
      (if (pending-draft? draft editing?)
        (editor-view-result state)
        {:status :none
         :kind kind
         :draft draft}))))

(defn materialized-view-path-values
  [{:keys [kind draft views]}]
  [[[:portfolio :optimizer :draft :return-model :views] views]
   [(conj common/editor-path :drafts kind) (reset-draft-after-save draft)]
   [(conj common/editor-path :editing-view-id) nil]
   [(conj common/editor-path :errors) {}]
   [(conj common/editor-path :clear-confirmation-open?) false]])

(defn error-path-values
  [errors]
  (into [[(conj common/editor-path :errors) errors]]
        (map (fn [[field message]]
               [(conj common/editor-path :errors field) message])
             errors)))

(defn missing-view-error-path-values
  []
  (error-path-values {:return-text missing-view-error-message}))
