(ns hyperopen.portfolio.optimizer.application.run-identity
  (:require [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn build-request-signature
  [request]
  (contracts/build-request-signature request))

(defn optimizer-input-signature
  [request]
  (contracts/optimizer-input-signature request))

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

(defn completed-run?
  [{:keys [draft run-state running? last-successful-run]}]
  (and (solved-run? last-successful-run)
       (= :succeeded (:status run-state))
       (not running?)
       (not (true? (get-in draft [:metadata :dirty?])))
       (some? (:request-signature run-state))
       (= (:request-signature run-state)
          (:request-signature last-successful-run))))

(defn current-solved-run?
  [{:keys [draft readiness running? last-successful-run] :as ctx}]
  (and (solved-run? last-successful-run)
       (not running?)
       (not (true? (get-in draft [:metadata :dirty?])))
       (or (completed-run? ctx)
           (matching-request? (:request readiness) last-successful-run))))

(defn stale-run?
  [{:keys [draft readiness last-successful-run] :as ctx}]
  (and (some? last-successful-run)
       (not (completed-run? ctx))
       (or (true? (get-in draft [:metadata :dirty?]))
           (not (matching-request? (:request readiness) last-successful-run)))))
