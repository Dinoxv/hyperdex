(ns hyperopen.portfolio.optimizer.domain.constraints
  (:require [hyperopen.portfolio.optimizer.domain.history-series :as history-series]))

(def default-max-asset-weight
  1)

(def unknown-sparse-history-max-weight
  0.2)

(defn- sparse-safety-max-weight
  [interval-count]
  (cond
    (not (number? interval-count)) nil
    (< interval-count 2) 0
    (< interval-count 8) 0.05
    (< interval-count 30) 0.1
    (< interval-count 60) 0.2
    :else nil))

(defn- instrument-id
  [instrument]
  (:instrument-id instrument))

(defn- vault-like-instrument?
  [instrument]
  (let [id (instrument-id instrument)
        market-type (or (:market-type instrument)
                        (:instrument-type instrument))]
    (or (= :vault market-type)
        (= "vault" market-type)
        (some? (:vault-address instrument))
        (and (string? id)
             (.startsWith id "vault:")))))

(defn- id-set
  [values]
  (cond
    (nil? values) nil
    (set? values) values
    (sequential? values) (set values)
    :else #{values}))

(defn normalize-universe
  [universe constraints]
  (let [allowlist (id-set (:allowlist constraints))
        blocklist (or (id-set (:blocklist constraints)) #{})]
    (->> universe
         (filter (fn [instrument]
                   (let [id (instrument-id instrument)]
                     (and (or (nil? allowlist)
                              (contains? allowlist id))
                          (not (contains? blocklist id))))))
         vec)))

(defn- max-weight-for
  [constraints instrument-id]
  (min (or (:max-asset-weight constraints) default-max-asset-weight)
      (or (get-in constraints [:per-asset-overrides instrument-id :max-weight])
          default-max-asset-weight)
      (or (get-in constraints [:per-perp-leverage-caps instrument-id :max-weight])
          default-max-asset-weight)))

(defn- native-cadence-for
  [history instrument-id]
  (or (get-in history [:cadence-by-instrument instrument-id])
      (when-let [rows (seq (get-in history [:raw-price-series-by-instrument instrument-id]))]
        (history-series/cadence-summary rows))))

(defn- aligned-cadence-for
  [history instrument-id]
  (when-let [rows (seq (get-in history [:price-series-by-instrument instrument-id]))]
    (history-series/cadence-summary rows)))

(defn- cadence-for
  [history instrument]
  (let [instrument-id (instrument-id instrument)]
    (or (native-cadence-for history instrument-id)
        (when (vault-like-instrument? instrument)
          {:kind :sparse
           :sparse? true
           :reason :missing-native-history-metadata})
        (aligned-cadence-for history instrument-id))))

(defn- merge-max-weight-override
  [override max-weight]
  (let [existing-max-weight (:max-weight override)]
    (assoc (or override {})
           :max-weight (if (number? existing-max-weight)
                         (min existing-max-weight max-weight)
                         max-weight))))

(defn- sparse-cap-warning
  [{:keys [instrument-id interval-count max-weight reason]}]
  {:code :sparse-history-weight-cap-applied
   :instrument-id instrument-id
   :interval-count interval-count
   :max-weight max-weight
   :message (if (= :missing-native-history-metadata reason)
              (str "sparse history weight cap applied at "
                   (js/Math.round (* 100 max-weight))
                   "% because native cadence metadata is unavailable.")
              (str "sparse history weight cap applied at "
                   (js/Math.round (* 100 max-weight))
                   "% based on "
                   interval-count
                   " native intervals."))})

(defn- runtime-sparse-caps
  [history universe]
  (->> universe
       (keep (fn [instrument]
               (let [instrument-id (instrument-id instrument)
                     cadence (cadence-for history instrument)
                     max-weight (when (:sparse? cadence)
                                  (if (= :missing-native-history-metadata
                                         (:reason cadence))
                                    unknown-sparse-history-max-weight
                                    (sparse-safety-max-weight
                                     (:interval-count cadence))))]
                 (when (number? max-weight)
                   {:instrument-id instrument-id
                    :interval-count (:interval-count cadence)
                    :max-weight max-weight
                    :reason (:reason cadence)}))))
       vec))

(defn- locked?
  [constraints instrument-id]
  (contains? (or (id-set (:held-position-locks constraints)) #{})
             instrument-id))

(defn- current-weight
  [current-weights instrument-id]
  (or (get current-weights instrument-id) 0))

(defn- bounds-for
  [constraints current-weights instrument]
  (let [id (instrument-id instrument)
        max-weight (max-weight-for constraints id)]
    (if (locked? constraints id)
      (let [weight (current-weight current-weights id)]
        {:lower weight
         :upper weight
         :locked {:instrument-id id
                  :weight weight}})
      (if (:long-only? constraints)
        {:lower 0
         :upper max-weight}
        {:lower (- max-weight)
         :upper max-weight}))))

(defn- target-net
  [constraints]
  (if (:long-only? constraints)
    1
    nil))

(defn- violations
  [lower-bounds upper-bounds constraints]
  (let [target-net* (target-net constraints)
        sum-lower (reduce + 0 lower-bounds)
        sum-upper (reduce + 0 upper-bounds)]
    (vec (concat
          (when (and (number? target-net*)
                     (> sum-lower target-net*))
            [{:code :sum-lower-above-target
              :sum-lower sum-lower
              :target-net target-net*}])
          (when (and (number? target-net*)
                     (< sum-upper target-net*))
            [{:code :sum-upper-below-target
              :sum-upper sum-upper
              :target-net target-net*}])))))

(defn- apply-runtime-caps
  [constraints sparse-caps]
  (if (seq sparse-caps)
    (update constraints
            :per-asset-overrides
            (fn [overrides]
              (reduce (fn [acc {:keys [instrument-id max-weight]}]
                        (update acc
                                instrument-id
                                merge-max-weight-override
                                max-weight))
                      (or overrides {})
                      sparse-caps)))
    constraints))

(defn- encoded-result
  [constraints universe current-weights]
  (let [ids (mapv instrument-id universe)
        bounds (mapv (partial bounds-for constraints current-weights)
                     universe)
        lower-bounds (mapv :lower bounds)
        upper-bounds (mapv :upper bounds)
        violations* (violations lower-bounds upper-bounds constraints)]
    {:status (if (seq violations*) :infeasible :ok)
     :long-only? (:long-only? constraints)
     :net-target (target-net constraints)
     :instrument-ids ids
     :current-weights (mapv #(current-weight current-weights %) ids)
     :lower-bounds lower-bounds
     :upper-bounds upper-bounds
     :locked-weights (vec (keep :locked bounds))
     :gross-exposure {:max (:gross-leverage constraints)}
     :net-exposure (:net-exposure constraints)
     :max-turnover (:max-turnover constraints)
     :rebalance-tolerance (:rebalance-tolerance constraints)
     :violations violations*}))

(defn encode-constraints
  [{:keys [universe current-weights constraints history]}]
  (let [base-constraints (merge {:long-only? true}
                                (or constraints {}))
        universe* (normalize-universe (or universe []) base-constraints)
        current-weights* (or current-weights {})
        sparse-caps (runtime-sparse-caps history universe*)
        base-encoded (encoded-result base-constraints
                                     universe*
                                     current-weights*)
        capped-constraints (apply-runtime-caps base-constraints sparse-caps)
        capped-encoded (encoded-result capped-constraints
                                       universe*
                                       current-weights*)]
    (cond
      (empty? sparse-caps)
      (assoc base-encoded :warnings [])

      :else
      (assoc capped-encoded
             :warnings (mapv sparse-cap-warning sparse-caps)))))
