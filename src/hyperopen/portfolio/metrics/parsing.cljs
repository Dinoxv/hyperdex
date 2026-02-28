(ns hyperopen.portfolio.metrics.parsing)

(defn optional-number [value]
  (let [num (cond
              (number? value) value
              (string? value) (js/parseFloat value)
              :else js/NaN)]
    (when (and (number? num)
               (not (js/isNaN num)))
      num)))

(defn finite-number? [value]
  (and (number? value)
       (js/isFinite value)))

(defn history-point-value [row]
  (cond
    (and (sequential? row)
         (>= (count row) 2))
    (optional-number (second row))

    (map? row)
    (or (optional-number (:value row))
        (optional-number (:pnl row))
        (optional-number (:account-value row))
        (optional-number (:accountValue row)))

    :else
    nil))

(defn history-point-time-ms [row]
  (cond
    (and (sequential? row)
         (seq row))
    (optional-number (first row))

    (map? row)
    (or (optional-number (:time row))
        (optional-number (:timestamp row))
        (optional-number (:time-ms row))
        (optional-number (:timeMs row))
        (optional-number (:ts row))
        (optional-number (:t row)))

    :else
    nil))

(defn day-string-from-ms [time-ms]
  (subs (.toISOString (js/Date. time-ms)) 0 10))

(defn parse-day-ms [day]
  (when (string? day)
    (let [ms (.getTime (js/Date. (str day "T00:00:00.000Z")))]
      (when (and (number? ms)
                 (not (js/isNaN ms)))
        ms))))
