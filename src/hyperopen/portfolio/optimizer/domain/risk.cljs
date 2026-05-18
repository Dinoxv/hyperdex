(ns hyperopen.portfolio.optimizer.domain.risk
  (:require [hyperopen.portfolio.optimizer.domain.math :as math]
            [hyperopen.portfolio.optimizer.domain.risk-ledoit-wolf :as risk-ledoit-wolf]
            [hyperopen.portfolio.optimizer.domain.risk-mixed-frequency :as mixed-frequency]))

(def default-periods-per-year
  365)

(def default-shrinkage
  0.1)

(def ^:private psd-epsilon
  1e-8)

(defn- sorted-instrument-ids
  [history]
  (->> [(keys (:return-series-by-instrument history))
        (keys (:raw-price-series-by-instrument history))]
       (apply concat)
       set
       sort))

(defn- series-by-id
  [history instrument-ids]
  (mapv #(vec (get-in history [:return-series-by-instrument %])) instrument-ids))

(defn- covariance-matrix
  [series periods-per-year]
  (mapv (fn [xs]
          (mapv (fn [ys]
                  (* periods-per-year
                     (or (math/sample-covariance xs ys) 0)))
                series))
        series))

(defn- diagonal-shrink
  [matrix shrinkage]
  (mapv (fn [row row-idx]
          (mapv (fn [value col-idx]
                  (if (= row-idx col-idx)
                    value
                    (* (- 1 shrinkage) value)))
                row
                (range)))
        matrix
        (range)))

(defn- normalize-risk-model-kind
  [kind]
  (case kind
    :ledoit-wolf :diagonal-shrink
    :ledoit-wolf-dense :ledoit-wolf-dense
    :diagonal-shrink :diagonal-shrink
    :sample-covariance :sample-covariance
    :mixed-frequency :mixed-frequency
    kind))

(defn- matrix->mutable-array
  [matrix]
  (let [n (count matrix)
        result (js/Array. n)]
    (doseq [row-idx (range n)]
      (let [source-row (nth matrix row-idx)
            row (js/Array. n)]
        (doseq [col-idx (range n)]
          (aset row col-idx (double (or (nth source-row col-idx) 0))))
        (aset result row-idx row)))
    result))

(defn- array-matrix-get
  [matrix row col]
  (aget (aget matrix row) col))

(defn- array-matrix-set!
  [matrix row col value]
  (aset (aget matrix row) col value))

(defn- mutable-diagonal
  [matrix]
  (mapv #(array-matrix-get matrix % %) (range (.-length matrix))))

(defn- symmetric-eigenvalues
  [matrix]
  (let [n (count matrix)
        mutable (matrix->mutable-array matrix)
        tolerance 1e-10
        max-sweeps (max 8 (min 16 n))]
    (loop [sweep 0]
      (if (>= sweep max-sweeps)
        (mutable-diagonal mutable)
        (let [rotated? (volatile! false)]
          (doseq [row (range n)
                  col (range (inc row) n)]
            (let [apq (array-matrix-get mutable row col)]
              (when (> (js/Math.abs apq) tolerance)
                (let [app (array-matrix-get mutable row row)
                      aqq (array-matrix-get mutable col col)
                      tau (/ (- aqq app) (* 2 apq))
                      signed (/ (if (neg? tau) -1 1)
                                (+ (js/Math.abs tau)
                                   (js/Math.sqrt (+ 1 (* tau tau)))))
                      cosine (/ 1 (js/Math.sqrt (+ 1 (* signed signed))))
                      sine (* signed cosine)]
                  (vreset! rotated? true)
                  (doseq [idx (range n)
                          :when (and (not= idx row)
                                     (not= idx col))]
                    (let [aip (array-matrix-get mutable idx row)
                          aiq (array-matrix-get mutable idx col)
                          aip* (- (* cosine aip) (* sine aiq))
                          aiq* (+ (* sine aip) (* cosine aiq))]
                      (array-matrix-set! mutable idx row aip*)
                      (array-matrix-set! mutable row idx aip*)
                      (array-matrix-set! mutable idx col aiq*)
                      (array-matrix-set! mutable col idx aiq*)))
                  (array-matrix-set! mutable row row (- app (* signed apq)))
                  (array-matrix-set! mutable col col (+ aqq (* signed apq)))
                  (array-matrix-set! mutable row col 0)
                  (array-matrix-set! mutable col row 0)))))
          (if @rotated?
            (recur (inc sweep))
            (mutable-diagonal mutable)))))))

(defn covariance-conditioning
  [covariance]
  (let [eigenvalues (filter math/finite-number? (symmetric-eigenvalues covariance))
        min-eigenvalue (when (seq eigenvalues) (apply min eigenvalues))
        max-eigenvalue (when (seq eigenvalues) (apply max eigenvalues))
        positive (filter #(> % 1e-12) eigenvalues)
        min-positive (when (seq positive) (apply min positive))
        condition-number (when (and (math/finite-number? min-positive)
                                    (math/finite-number? max-eigenvalue)
                                    (pos? min-positive))
                           (/ max-eigenvalue min-positive))]
    {:condition-number condition-number
     :min-eigenvalue min-eigenvalue
     :max-eigenvalue max-eigenvalue
     :status (cond
               (and (math/finite-number? min-eigenvalue)
                    (< min-eigenvalue -1e-8)) :not-positive-semidefinite
               (nil? condition-number) :unknown
               (> condition-number 1000000) :ill-conditioned
               (> condition-number 10000) :watch
               :else :ok)}))

(defn- diagonal-load
  [matrix amount]
  (mapv (fn [row idx]
          (update row idx #(+ (or % 0) amount)))
        matrix
        (range)))

(defn- repair-psd
  [covariance]
  (let [conditioning (covariance-conditioning covariance)
        min-eigenvalue (:min-eigenvalue conditioning)]
    (if (and (math/finite-number? min-eigenvalue)
             (< min-eigenvalue 0))
      (let [loading (+ (- min-eigenvalue) psd-epsilon)]
        {:covariance (diagonal-load covariance loading)
         :warning {:code :psd-repair-applied
                   :diagonal-loading loading
                   :min-eigenvalue min-eigenvalue
                   :message "Covariance matrix was repaired with diagonal loading to keep it positive semidefinite."}})
      {:covariance covariance
       :warning nil})))

(defn estimate-risk-model
  [{:keys [risk-model periods-per-year history]}]
  (let [risk-model* (or risk-model {:kind :diagonal-shrink})
        requested-kind (:kind risk-model*)
        model-kind (normalize-risk-model-kind requested-kind)
        periods-per-year* (or periods-per-year default-periods-per-year)
        instrument-ids (vec (sorted-instrument-ids history))
        cadence-by-instrument (mixed-frequency/cadence-by-instrument
                               history
                               instrument-ids)
        warnings* (mixed-frequency/warnings requested-kind
                                            cadence-by-instrument)
        mixed-frequency? (mixed-frequency/mixed-frequency? model-kind
                                                            history
                                                            instrument-ids)]
    (if mixed-frequency?
      (let [risk-instrument-ids (mixed-frequency/instrument-ids history
                                                                instrument-ids)
            missing-native-warnings (mixed-frequency/missing-native-risk-history-warnings
                                     history
                                     instrument-ids
                                     risk-instrument-ids)
            override-warning (mixed-frequency/override-warning model-kind)
            {:keys [covariance pair-metadata warnings]}
            (mixed-frequency/matrix history risk-instrument-ids)
            shrinkage (or (:shrinkage risk-model*) default-shrinkage)
            {covariance* :covariance psd-warning :warning}
            (repair-psd covariance)
            covariance** (if (= :diagonal-shrink model-kind)
                           (diagonal-shrink covariance* shrinkage)
                           covariance*)
            warnings** (vec (concat warnings*
                                    missing-native-warnings
                                    (when override-warning [override-warning])
                                    warnings
                                    (when psd-warning [psd-warning])))]
        (cond-> {:model :mixed-frequency
                 :requested-model model-kind
                 :instrument-ids risk-instrument-ids
                 :covariance covariance**
                 :pair-metadata pair-metadata
                 :risk-estimation (mixed-frequency/risk-estimation history)
                 :warnings warnings**}
          (= :diagonal-shrink model-kind)
          (assoc :shrinkage {:kind :diagonal
                             :shrinkage shrinkage})))
      (let [series (series-by-id history instrument-ids)
            sample (covariance-matrix series periods-per-year*)
            shrinkage (or (:shrinkage risk-model*) default-shrinkage)
            ledoit-wolf-result (when (= :ledoit-wolf-dense model-kind)
                                 (risk-ledoit-wolf/estimate
                                  {:series series
                                   :periods-per-year periods-per-year*}))
            covariance (case model-kind
                         :diagonal-shrink (diagonal-shrink sample shrinkage)
                         :ledoit-wolf-dense (:covariance ledoit-wolf-result)
                         :sample-covariance sample
                         sample)]
        (cond-> {:model model-kind
                 :instrument-ids instrument-ids
                 :covariance covariance
                 :warnings warnings*}
          (= :ledoit-wolf-dense model-kind)
          (merge (select-keys ledoit-wolf-result
                              [:shrinkage :sample-count :feature-count]))

          (= :diagonal-shrink model-kind)
          (assoc :shrinkage {:kind :diagonal
                             :shrinkage shrinkage}))))))
