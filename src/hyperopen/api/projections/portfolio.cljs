(ns hyperopen.api.projections.portfolio
  (:require [hyperopen.api.errors :as api-errors]))

(defn- normalized-error
  [err]
  (api-errors/normalize-error err))

(defn begin-portfolio-load
  [state]
  (-> state
      (assoc-in [:portfolio :loading?] true)
      (assoc-in [:portfolio :error] nil)))

(defn apply-portfolio-success
  [state summary-by-key]
  (-> state
      (assoc-in [:portfolio :summary-by-key] (or summary-by-key {}))
      (assoc-in [:portfolio :loading?] false)
      (assoc-in [:portfolio :error] nil)
      (assoc-in [:portfolio :loaded-at-ms] (.now js/Date))))

(defn apply-portfolio-error
  [state err]
  (let [{:keys [message]} (normalized-error err)]
    (-> state
        (assoc-in [:portfolio :loading?] false)
        (assoc-in [:portfolio :error] message))))
