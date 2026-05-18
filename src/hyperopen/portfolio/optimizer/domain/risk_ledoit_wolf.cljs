(ns hyperopen.portfolio.optimizer.domain.risk-ledoit-wolf
  (:require [hyperopen.portfolio.optimizer.domain.math :as math]))

(defn- zero-matrix
  [size]
  (vec (repeat size
               (vec (repeat size 0)))))

(defn- rectangular-series?
  [series]
  (or (empty? series)
      (let [sample-count (count (first series))]
        (every? #(= sample-count (count %)) series))))

(defn- centered-observations
  [series]
  (let [means (mapv #(or (math/mean %) 0) series)]
    (mapv (fn [row]
            (mapv - row means))
          (apply mapv vector series))))

(defn- sample-covariance
  [centered]
  (let [sample-count (count centered)
        feature-count (count (first centered))]
    (if (pos? sample-count)
      (math/scalar-matrix (/ 1 sample-count)
                          (math/mat-mul (math/transpose centered)
                                        centered))
      (zero-matrix feature-count))))

(defn- scaled-identity-target
  [sample]
  (let [feature-count (count sample)
        trace (reduce + 0 (math/diagonal sample))
        mu (if (pos? feature-count)
             (/ trace feature-count)
             0)]
    (math/scalar-matrix mu
                        (math/identity-matrix feature-count))))

(defn- outer-product
  [values]
  (mapv (fn [left]
          (mapv (fn [right]
                  (* left right))
                values))
        values))

(defn- frobenius-squared
  [matrix]
  (reduce + 0
          (mapcat (fn [row]
                    (map #(* % %) row))
                  matrix)))

(defn- matrix-difference
  [left right]
  (math/matrix-add left
                   (math/scalar-matrix -1 right)))

(defn estimate
  [{:keys [series periods-per-year]}]
  (let [feature-count (count series)
        sample-count (if (seq series)
                       (count (first series))
                       0)
        periods-per-year* (or periods-per-year 1)]
    (if (and (pos? feature-count)
             (pos? sample-count)
             (rectangular-series? series))
      (let [centered (centered-observations series)
            sample (sample-covariance centered)
            target (scaled-identity-target sample)
            beta-sample (mapv #(frobenius-squared
                                (matrix-difference (outer-product %)
                                                   sample))
                              centered)
            beta-hat (/ (or (math/mean beta-sample) 0)
                        sample-count)
            delta-hat (frobenius-squared
                       (matrix-difference sample target))
            shrinkage (if (pos? delta-hat)
                        (-> (/ beta-hat delta-hat)
                            (max 0)
                            (min 1))
                        0)
            covariance (math/matrix-add
                        (math/scalar-matrix shrinkage target)
                        (math/scalar-matrix (- 1 shrinkage) sample))]
        {:covariance (math/scalar-matrix periods-per-year* covariance)
         :shrinkage {:kind :ledoit-wolf
                     :target :scaled-identity
                     :shrinkage shrinkage}
         :sample-count sample-count
         :feature-count feature-count})
      {:covariance (zero-matrix feature-count)
       :shrinkage {:kind :ledoit-wolf
                   :target :scaled-identity
                   :shrinkage 0}
       :sample-count sample-count
       :feature-count feature-count})))
