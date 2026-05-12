(ns hyperopen.portfolio.optimizer.application.instrument-labels
  (:require [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def ^:private non-blank-text coercion/non-blank-text)

(defn- universe-by-id
  [universe]
  (into {}
        (map (fn [instrument]
               [(:instrument-id instrument) instrument]))
        universe))

(def ^:private vault-instrument-id? ids/vault-instrument-id?)

(defn- vault-instrument?
  [instrument instrument-id]
  (or (= :vault (ids/normalize-market-type (:market-type instrument)))
      (vault-instrument-id? instrument-id)))

(defn- label-for-instrument
  [instrument-id instrument]
  (if (vault-instrument? instrument instrument-id)
    (or (non-blank-text (:name instrument))
        (non-blank-text (:symbol instrument))
        (non-blank-text (:coin instrument))
        instrument-id)
    (or (non-blank-text (:coin instrument))
        instrument-id)))

(defn labels-by-instrument
  [universe instrument-ids]
  (let [by-id (universe-by-id universe)]
    (into {}
          (map (fn [instrument-id]
                 [instrument-id
                  (label-for-instrument instrument-id (get by-id instrument-id))]))
          instrument-ids)))
