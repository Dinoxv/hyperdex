(ns hyperopen.portfolio.optimizer.application.return-inputs
  (:require [hyperopen.portfolio.optimizer.application.engine.context :as engine-context]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def decimal->percent-text coercion/decimal->percent-text)

(defn readiness-inputs-by-instrument
  [readiness]
  (let [request (:request readiness)]
    (if (= :black-litterman (get-in request [:return-model :kind]))
      (engine-context/baseline-expected-return-inputs-by-instrument request)
      {})))
