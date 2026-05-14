(ns hyperopen.portfolio.optimizer.application.view-model.results
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.instrument-labels :as instrument-labels]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def ^:private vault-instrument-prefix
  ids/vault-instrument-prefix)

(def ^:private non-blank-text coercion/non-blank-text)

(def ^:private vault-address-from-instrument-id
  ids/vault-address-from-instrument-id)

(defn- raw-vault-label?
  [instrument-id label]
  (let [address (vault-address-from-instrument-id instrument-id)
        label* (some-> (non-blank-text label) str/lower-case)]
    (boolean
     (and address
          (or (nil? label*)
              (= label* address)
              (= label* (str vault-instrument-prefix address)))))))

(defn- display-label
  [instrument-id label]
  (when-not (raw-vault-label? instrument-id label)
    (non-blank-text label)))

(defn instrument-label
  [labels-by-instrument instrument-id]
  (if (vault-address-from-instrument-id instrument-id)
    (or (display-label instrument-id (get labels-by-instrument instrument-id))
        (str instrument-id))
    (str instrument-id)))

(defn enrich-result-labels
  [result draft]
  (if (map? result)
    (let [instrument-ids (vec (:instrument-ids result))
          result-labels (or (:labels-by-instrument result) {})
          draft-labels (when (seq (:universe draft))
                         (instrument-labels/labels-by-instrument (:universe draft)
                                                                 instrument-ids))
          merged-labels (into result-labels
                              (keep (fn [instrument-id]
                                      (let [existing-label (get result-labels instrument-id)
                                            draft-label (display-label
                                                         instrument-id
                                                         (get draft-labels instrument-id))]
                                        (when (and draft-label
                                                   (or (nil? (non-blank-text existing-label))
                                                       (raw-vault-label? instrument-id existing-label)))
                                          [instrument-id draft-label]))))
                              instrument-ids)]
      (assoc result :labels-by-instrument merged-labels))
    result))
