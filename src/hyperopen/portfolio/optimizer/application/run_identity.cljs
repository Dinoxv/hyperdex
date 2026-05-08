(ns hyperopen.portfolio.optimizer.application.run-identity)

(def ^:private optimizer-input-keys
  [:requested-universe
   :universe
   :current-portfolio
   :return-model
   :risk-model
   :objective
   :constraints
   :execution-assumptions
   :history
   :black-litterman-prior])

(defn- stable-execution-assumptions
  [execution-assumptions]
  (when (map? execution-assumptions)
    (dissoc execution-assumptions :cost-contexts-by-id)))

(defn- stable-history
  [history]
  (when (map? history)
    (dissoc history :freshness)))

(defn build-request-signature
  [request]
  {:scenario-id (:scenario-id request)
   :as-of-ms (:as-of-ms request)
   :request request})

(defn optimizer-input-signature
  [request]
  (when (map? request)
    (-> (select-keys request optimizer-input-keys)
        (update :execution-assumptions stable-execution-assumptions)
        (update :history stable-history))))

(defn matching-request?
  [request last-successful-run]
  (let [run-request (get-in last-successful-run [:request-signature :request])
        current-signature (optimizer-input-signature request)
        run-signature (optimizer-input-signature run-request)]
    (and (some? current-signature)
         (some? run-signature)
         (= current-signature run-signature))))

(defn solved-run?
  [last-successful-run]
  (= :solved (get-in last-successful-run [:result :status])))

(defn current-solved-run?
  [{:keys [draft readiness running? last-successful-run]}]
  (and (solved-run? last-successful-run)
       (not running?)
       (not (true? (get-in draft [:metadata :dirty?])))
       (matching-request? (:request readiness) last-successful-run)))

(defn stale-run?
  [{:keys [draft readiness last-successful-run]}]
  (and (some? last-successful-run)
       (or (true? (get-in draft [:metadata :dirty?]))
           (not (matching-request? (:request readiness) last-successful-run)))))
