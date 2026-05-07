(ns hyperopen.portfolio.optimizer.application.black-litterman-preview
  (:require [hyperopen.portfolio.optimizer.application.engine.context :as engine-context]))

(defn- instrument-ids
  [request]
  (mapv :instrument-id (:universe request)))

(defn- prior-returns-by-instrument
  [request]
  (engine-context/baseline-expected-return-inputs-by-instrument request))

(defn- posterior-returns-by-instrument
  [request]
  (engine-context/expected-return-inputs-by-instrument request))

(defn build-preview
  [readiness]
  (let [request (:request readiness)]
    (cond
      (not= :ready (:status readiness))
      {:status :unavailable
       :reason :no-eligible-request}

      (not= :black-litterman (get-in request [:return-model :kind]))
      {:status :unavailable
       :reason :not-black-litterman}

      (empty? (get-in request [:return-model :views]))
      {:status :empty
       :view-count 0}

      :else
      (let [prior (prior-returns-by-instrument request)
            posterior (posterior-returns-by-instrument request)
            ids (instrument-ids request)]
        {:status :ready
         :view-count (count (get-in request [:return-model :views]))
         :rows (mapv (fn [instrument-id]
                       {:instrument-id instrument-id
                        :prior-return (get prior instrument-id)
                        :posterior-return (get posterior instrument-id)})
                     ids)}))))
