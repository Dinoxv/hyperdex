(ns hyperopen.api.projections.portfolio
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.api.errors :as api-errors]))

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

(defn- benchmark-address
  [address]
  (account-context/normalize-address address))

(defn begin-trader-benchmark-portfolio-load
  [state address]
  (if-let [address* (benchmark-address address)]
    (-> state
        (assoc-in [:portfolio :loading :trader-benchmarks-by-address address*] true)
        (assoc-in [:portfolio :errors :trader-benchmarks-by-address address*] nil))
    state))

(defn apply-trader-benchmark-portfolio-success
  [state address summary-by-key]
  (if-let [address* (benchmark-address address)]
    (-> state
        (assoc-in [:portfolio :trader-benchmarks-by-address address* :summary-by-key]
                  (or summary-by-key {}))
        (assoc-in [:portfolio :trader-benchmarks-by-address address* :loaded-at-ms] (.now js/Date))
        (assoc-in [:portfolio :loading :trader-benchmarks-by-address address*] false)
        (assoc-in [:portfolio :errors :trader-benchmarks-by-address address*] nil))
    state))

(defn apply-trader-benchmark-portfolio-error
  [state address err]
  (if-let [address* (benchmark-address address)]
    (let [{:keys [message]} (normalized-error err)]
      (-> state
          (assoc-in [:portfolio :loading :trader-benchmarks-by-address address*] false)
          (assoc-in [:portfolio :errors :trader-benchmarks-by-address address*] message)))
    state))
