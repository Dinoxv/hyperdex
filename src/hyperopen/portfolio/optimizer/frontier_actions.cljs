(ns hyperopen.portfolio.optimizer.frontier-actions
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(def ^:private frontier-overlay-modes
  #{:standalone
    :contribution
    :none})

(defn- normalize-keyword-like
  [value]
  (let [text (cond
               (keyword? value) (name value)
               (string? value) (str/trim value)
               :else nil)]
    (when (seq text)
      (-> text
          (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
          (str/replace #"[_\s]+" "-")
          str/lower-case
          keyword))))

(defn normalize-frontier-overlay-mode
  [value]
  (let [mode (normalize-keyword-like value)]
    (if (contains? frontier-overlay-modes mode)
      mode
      :standalone)))

(defn set-portfolio-optimizer-frontier-overlay-mode
  [_state mode]
  [[:effects/save
    contracts/ui-frontier-overlay-mode-path
    (normalize-frontier-overlay-mode mode)]])

(defn set-portfolio-optimizer-constrain-frontier
  [_state constrained?]
  [[:effects/save
    contracts/ui-constrain-frontier-path
    (true? constrained?)]])
