(ns hyperopen.utils.parse)

(defn parse-int-value
  [value]
  (let [num (cond
              (number? value) value
              (string? value) (js/parseInt value 10)
              :else js/NaN)]
    (when (and (number? num)
               (not (js/isNaN num)))
      (js/Math.floor num))))
