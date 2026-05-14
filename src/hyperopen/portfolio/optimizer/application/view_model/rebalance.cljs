(ns hyperopen.portfolio.optimizer.application.view-model.rebalance
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.coercion :as coercion]
            [hyperopen.portfolio.optimizer.ids :as ids]))

(def ^:private finite-number? coercion/finite-number?)
(def ^:private vault-instrument? ids/vault-instrument-id?)

(defn- signed-label
  [value]
  (cond
    (and (finite-number? value) (neg? value)) "short"
    (and (finite-number? value) (pos? value)) "long"
    :else "flat"))

(defn- instrument-group-key
  [labels-by-instrument instrument-id]
  (let [value (or (get labels-by-instrument instrument-id)
                  (str instrument-id))
        unprefixed (last (str/split value #":"))
        base (first (str/split unprefixed #"[/-]"))]
    (if (seq base) base value)))

(defn- instrument-label
  [labels-by-instrument instrument-id]
  (or (get labels-by-instrument instrument-id)
      (str instrument-id)))

(defn- base-symbol
  [value]
  (some-> value
          str
          str/trim
          (str/replace #"^.*:" "")
          (str/split #"/|-" 2)
          first
          str/trim
          not-empty))

(defn- instrument-market
  [labels-by-instrument instrument-id]
  (let [instrument-id* (str instrument-id)
        label (instrument-label labels-by-instrument instrument-id)
        [kind raw-coin] (str/split instrument-id* #":" 2)
        market-type (case kind
                      "spot" :spot
                      "perp" :perp
                      nil)
        coin (or (not-empty raw-coin)
                 (base-symbol label))
        base (or (base-symbol coin)
                 (base-symbol label))]
    {:key instrument-id*
     :coin coin
     :symbol (or (when (= :spot market-type)
                   (when base
                     (str base "/USDC")))
                 base
                 label)
     :base base
     :market-type market-type}))

(defn- leg-label
  [labels-by-instrument instrument-id current-weight target-weight]
  (let [value (str instrument-id)
        market-type (first (str/split value #":"))]
    (case market-type
      "spot" "spot"
      "perp" (cond
               (neg? (or target-weight 0)) "perp short"
               (pos? (or target-weight 0)) "perp long"
               (neg? (or current-weight 0)) "perp short"
               :else "perp long")
      "vault" (instrument-label labels-by-instrument instrument-id)
      value)))

(defn- row-model
  [idx labels-by-instrument binding-instrument-ids capital-usd [instrument-id current-weight target-weight]]
  (let [current-weight* (or current-weight 0)
        target-weight* (or target-weight 0)
        current-notional (* (or capital-usd 0) current-weight*)
        target-notional (* (or capital-usd 0) target-weight*)
        delta (- target-weight* current-weight*)
        binding? (contains? binding-instrument-ids instrument-id)]
    {:idx idx
     :asset (instrument-group-key labels-by-instrument instrument-id)
     :instrument-id instrument-id
     :current-weight current-weight*
     :target-weight target-weight*
     :current-notional current-notional
     :target-notional target-notional
     :delta delta
     :delta-notional (- target-notional current-notional)
     :binding? binding?
     :current-sign (signed-label current-weight*)
     :target-sign (signed-label target-weight*)
     :leg-label (leg-label labels-by-instrument
                           instrument-id
                           current-weight*
                           target-weight*)
     :market (instrument-market labels-by-instrument instrument-id)}))

(defn- grouped-rows
  [rows]
  (reduce (fn [{:keys [order] :as acc} {:keys [asset] :as row}]
            (-> acc
                (update :order #(if (some #{asset} %) % (conj (or % []) asset)))
                (update-in [:by-asset asset] (fnil conj []) row)))
          {:order []
           :by-asset {}}
          rows))

(defn- group-icon-model
  [rows]
  (let [representative (or (some #(when-not (vault-instrument? (:instrument-id %)) %) rows)
                           (first rows))
        vault? (vault-instrument? (:instrument-id representative))]
    {:icon-kind (if vault? :vault :market)
     :market (when-not vault? (:market representative))}))

(defn- group-model
  [asset rows]
  (let [current-weight (reduce + 0 (map :current-weight rows))
        target-weight (reduce + 0 (map :target-weight rows))
        delta (- target-weight current-weight)
        binding? (boolean (some :binding? rows))
        expandable? (> (count rows) 1)
        rows* (mapv #(assoc % :hidden? (not expandable?)) rows)]
    (merge
     {:asset asset
      :current-weight current-weight
      :target-weight target-weight
      :delta delta
      :delta-notional (reduce + 0 (map :delta-notional rows))
      :binding? binding?
      :expandable? expandable?
      :target-sign (signed-label target-weight)
      :rows rows*}
     (group-icon-model rows*))))

(defn target-exposure-table-model
  [result]
  (let [capital-usd (get-in result [:rebalance-preview :capital-usd])
        instrument-ids (:instrument-ids result)
        current-weights (:current-weights result)
        target-weights (:target-weights result)
        labels-by-instrument (or (:labels-by-instrument result) {})
        binding-instrument-ids (set (keep :instrument-id
                                          (get-in result [:diagnostics :binding-constraints])))
        rows (mapv (fn [idx row]
                     (row-model idx
                                labels-by-instrument
                                binding-instrument-ids
                                capital-usd
                                row))
                   (range)
                   (map vector instrument-ids current-weights target-weights))
        {:keys [order by-asset]} (grouped-rows rows)
        groups (mapv #(group-model % (get by-asset %)) order)]
    {:capital-usd capital-usd
     :labels-by-instrument labels-by-instrument
     :binding-instrument-ids binding-instrument-ids
     :rows rows
     :groups groups}))
