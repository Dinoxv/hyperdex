(ns hyperopen.portfolio.optimizer.application.history-loader.calendar
  (:require [clojure.set :as set]
            [hyperopen.portfolio.metrics.history :as metrics-history]))

(defn row-by-time
  [rows]
  (into {}
        (map (juxt :time-ms identity))
        rows))

(defn common-calendar
  [histories]
  (let [sets (map #(set (keep :time-ms %)) histories)]
    (if (seq sets)
      (->> (apply set/intersection sets)
           sort
           vec)
      [])))

(defn return-intervals
  [calendar]
  (mapv (fn [[start-ms end-ms]]
          (let [dt-ms (- end-ms start-ms)
                dt-days (/ dt-ms metrics-history/day-ms)]
            {:start-ms start-ms
             :end-ms end-ms
             :dt-days dt-days
             :dt-years (/ dt-days 365.2425)}))
        (partition 2 1 calendar)))

(defn return-intervals-for-calendar
  [calendar return-calendar]
  (let [previous-by-end (into {}
                              (map (fn [[start-ms end-ms]]
                                     [end-ms start-ms]))
                              (partition 2 1 calendar))]
    (mapv (fn [end-ms]
            (let [start-ms (get previous-by-end end-ms)
                  dt-ms (when (and (number? start-ms)
                                   (number? end-ms))
                          (- end-ms start-ms))
                  dt-days (when (number? dt-ms)
                            (/ dt-ms metrics-history/day-ms))]
              {:start-ms start-ms
               :end-ms end-ms
               :dt-days dt-days
               :dt-years (when (number? dt-days)
                           (/ dt-days 365.2425))}))
          return-calendar)))

(defn freshness
  [calendar as-of-ms stale-after-ms]
  (let [latest-common-ms (last calendar)
        oldest-common-ms (first calendar)
        age-ms (when (and (number? as-of-ms)
                          (number? latest-common-ms))
                 (- as-of-ms latest-common-ms))
        stale? (if (number? latest-common-ms)
                 (and (number? stale-after-ms)
                      (number? age-ms)
                      (> age-ms stale-after-ms))
                 true)]
    {:as-of-ms as-of-ms
     :latest-common-ms latest-common-ms
     :oldest-common-ms oldest-common-ms
     :age-ms age-ms
     :stale? (boolean stale?)}))
