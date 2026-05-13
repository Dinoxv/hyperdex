(ns hyperopen.views.portfolio.optimize.black-litterman-views-model
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.black-litterman-editor-model :as editor-model]
            [hyperopen.views.portfolio.optimize.instrument-display :as instrument-display]))

(defn display-keyword
  [value fallback]
  (editor-model/display-keyword value fallback))

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

(def finite-number? editor-model/finite-number?)

(def parse-percent-text editor-model/parse-percent-text)

(def pct-label editor-model/pct-label)

(defn display-confidence
  [value]
  (editor-model/display-confidence value))

(defn display-horizon
  [value]
  (editor-model/display-horizon value))

(defn view-primary-id
  [view]
  (editor-model/view-primary-id view))

(defn view-comparator-id
  [view]
  (editor-model/view-comparator-id view))

(defn view-direction
  [view]
  (editor-model/view-direction view))

(defn view-summary
  [universe view]
  (editor-model/view-summary
   #(instrument-label universe %)
   view))

(def max-active-views editor-model/max-active-views)

(defn selected-kind
  [editor-state]
  (editor-model/selected-kind editor-state))

(defn selected-draft
  [universe editor-state kind return-inputs-by-instrument editing?]
  (editor-model/selected-draft universe
                               editor-state
                               kind
                               return-inputs-by-instrument
                               editing?))

(defn pending-draft?
  [draft editing?]
  (editor-model/pending-draft? draft editing?))

(defn draft-valid?
  [universe kind draft active-count editing?]
  (editor-model/draft-valid? universe kind draft active-count editing?))

(defn preview-text
  [universe kind draft]
  (editor-model/preview-text
   #(instrument-label universe %)
   kind
   draft))

(defn editor-view-model
  [draft readiness editor-state]
  (editor-model/editor-view-model draft readiness editor-state))
