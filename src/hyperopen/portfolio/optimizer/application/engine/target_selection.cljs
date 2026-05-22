(ns hyperopen.portfolio.optimizer.application.engine.target-selection
  (:require [hyperopen.portfolio.optimizer.domain.frontier :as frontier]
            [hyperopen.portfolio.optimizer.domain.math :as math]))

(def ^:private solution-tolerance
  1.0e-5)

(defn- sqrt
  [value]
  (js/Math.sqrt (max 0 value)))

(defn- finite-weights?
  [weights]
  (and (sequential? weights)
       (every? math/finite-number? weights)))

(defn- expected-weight-count?
  [problem weights]
  (= (count (:instrument-ids problem))
     (count weights)))

(defn- within-lower?
  [value lower]
  (or (not (math/finite-number? lower))
      (>= value (- lower solution-tolerance))))

(defn- within-upper?
  [value upper]
  (or (not (math/finite-number? upper))
      (<= value (+ upper solution-tolerance))))

(defn- bounds-satisfied?
  [problem weights]
  (let [lower-bounds (:lower-bounds problem)
        upper-bounds (:upper-bounds problem)]
    (and (= (count weights) (count lower-bounds) (count upper-bounds))
         (every? true?
                 (map (fn [weight lower upper]
                        (and (within-lower? weight lower)
                             (within-upper? weight upper)))
                      weights
                      lower-bounds
                      upper-bounds)))))

(defn- linear-constraint-value
  [weights constraint]
  (let [coefficients (:coefficients constraint)]
    (when (and (sequential? coefficients)
               (= (count weights) (count coefficients))
               (every? math/finite-number? coefficients))
      (math/dot coefficients weights))))

(defn- equality-satisfied?
  [weights equality]
  (let [target (:target equality)
        value (linear-constraint-value weights equality)]
    (and (math/finite-number? target)
         (math/finite-number? value)
         (<= (js/Math.abs (- value target))
             solution-tolerance))))

(defn- inequality-satisfied?
  [weights inequality]
  (let [value (linear-constraint-value weights inequality)]
    (and (math/finite-number? value)
         (within-lower? value (:lower inequality))
         (within-upper? value (:upper inequality)))))

(defn- abs-sum
  [values]
  (reduce + 0 (map js/Math.abs values)))

(defn- gross-exposure-satisfied?
  [weights constraint]
  (within-upper? (abs-sum weights) (:max constraint)))

(defn- turnover-satisfied?
  [weights constraint]
  (let [current-weights (:current-weights constraint)]
    (and (finite-weights? current-weights)
         (= (count weights) (count current-weights))
         (within-upper? (abs-sum (map - weights current-weights))
                        (:max constraint)))))

(defn- l1-constraint-satisfied?
  [weights constraint]
  (case (:code constraint)
    :gross-exposure (gross-exposure-satisfied? weights constraint)
    :turnover (turnover-satisfied? weights constraint)
    false))

(defn- feasible-solution?
  [problem weights]
  (and (bounds-satisfied? problem weights)
       (every? (partial equality-satisfied? weights)
               (or (:equalities problem) []))
       (every? (partial inequality-satisfied? weights)
               (or (:inequalities problem) []))
       (every? (partial l1-constraint-satisfied? weights)
               (or (:l1-constraints problem) []))))

(defn- solved?
  [result]
  (let [problem (:problem result)
        weights (:weights result)]
    (and (= :solved (:status result))
         (map? problem)
         (finite-weights? weights)
         (expected-weight-count? problem weights)
         (feasible-solution? problem weights))))

(defn- portfolio-point
  [expected-returns covariance risk-free-rate idx result]
  (let [weights (:weights result)
        expected-return (math/portfolio-return weights expected-returns)
        variance (math/portfolio-variance weights covariance)
        volatility (sqrt variance)]
    {:id idx
     :return-tilt (get-in result [:problem :return-tilt])
     :weights weights
     :expected-return expected-return
     :volatility volatility
     :sharpe (when (pos? volatility)
               (/ (- expected-return (or risk-free-rate 0))
                  volatility))
     :solver-status (:status result)
     :solver (:solver result)
     :iterations (:iterations result)
     :elapsed-ms (:elapsed-ms result)}))

(defn- solver-failure
  [solver-plan solver-results]
  {:status :infeasible
   :reason :solver-returned-no-solution
   :solver {:strategy (:strategy solver-plan)}
   :solver-results solver-results})

(defn solved-points
  [request solver-results expected-returns covariance]
  (->> solver-results
       (keep-indexed (fn [idx result]
                       (when (solved? result)
                         (portfolio-point expected-returns
                                          covariance
                                          (:risk-free-rate request)
                                          idx
                                          result))))
       vec))

(defn target-selection
  [request solver-plan solver-results expected-returns covariance]
  (let [points (solved-points request solver-results expected-returns covariance)]
    (if (empty? points)
      (solver-failure solver-plan solver-results)
      (let [frontier-points (frontier/efficient-frontier points)
            selected (or (when (= :frontier-sweep (:strategy solver-plan))
                           (frontier/select-frontier-point frontier-points (:objective request)))
                         (first frontier-points)
                         (first points))]
        {:status :solved
         :selected selected
         :target-frontier frontier-points}))))
