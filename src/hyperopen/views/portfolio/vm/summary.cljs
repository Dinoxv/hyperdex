(ns hyperopen.views.portfolio.vm.summary
  (:require [clojure.string :as str]
            [hyperopen.views.portfolio.vm.history :as vm-history]))

(defn canonical-summary-key
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      (let [token (-> text
                      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
                      str/lower-case
                      (str/replace #"[^a-z0-9]+" "-")
                      (str/replace #"(^-+)|(-+$)" ""))]
        (case token
          "day" :day
          "week" :week
          "month" :month
          "3m" :three-month
          "3-m" :three-month
          "3month" :three-month
          "3-month" :three-month
          "threemonth" :three-month
          "three-month" :three-month
          "three-months" :three-month
          "quarter" :three-month
          "6m" :six-month
          "6-m" :six-month
          "6month" :six-month
          "6-month" :six-month
          "sixmonth" :six-month
          "six-month" :six-month
          "six-months" :six-month
          "halfyear" :six-month
          "half-year" :six-month
          "1y" :one-year
          "1-y" :one-year
          "1year" :one-year
          "1-year" :one-year
          "oneyear" :one-year
          "one-year" :one-year
          "one-years" :one-year
          "year" :one-year
          "2y" :two-year
          "2-y" :two-year
          "2year" :two-year
          "2-year" :two-year
          "twoyear" :two-year
          "two-year" :two-year
          "two-years" :two-year
          "alltime" :all-time
          "all-time" :all-time
          "perpday" :perp-day
          "perp-day" :perp-day
          "perpweek" :perp-week
          "perp-week" :perp-week
          "perpmonth" :perp-month
          "perp-month" :perp-month
          "perp3m" :perp-three-month
          "perp3-m" :perp-three-month
          "perp3month" :perp-three-month
          "perp3-month" :perp-three-month
          "perpthreemonth" :perp-three-month
          "perp-three-month" :perp-three-month
          "perp-three-months" :perp-three-month
          "perpquarter" :perp-three-month
          "perp6m" :perp-six-month
          "perp6-m" :perp-six-month
          "perp6month" :perp-six-month
          "perp6-month" :perp-six-month
          "perpsixmonth" :perp-six-month
          "perp-six-month" :perp-six-month
          "perp-six-months" :perp-six-month
          "perphalfyear" :perp-six-month
          "perp-half-year" :perp-six-month
          "perp1y" :perp-one-year
          "perp1-y" :perp-one-year
          "perp1year" :perp-one-year
          "perp1-year" :perp-one-year
          "perponeyear" :perp-one-year
          "perp-one-year" :perp-one-year
          "perp-one-years" :perp-one-year
          "perpyear" :perp-one-year
          "perp2y" :perp-two-year
          "perp2-y" :perp-two-year
          "perp2year" :perp-two-year
          "perp2-year" :perp-two-year
          "perptwoyear" :perp-two-year
          "perp-two-year" :perp-two-year
          "perp-two-years" :perp-two-year
          "perpalltime" :perp-all-time
          "perp-all-time" :perp-all-time
          (keyword token))))))

(defn normalize-summary-by-key
  [summary-by-key]
  (reduce-kv (fn [acc key value]
               (let [summary-key (canonical-summary-key key)]
                 (if (and summary-key
                          (map? value))
                   (assoc acc summary-key value)
                   acc)))
             {}
             (or summary-by-key {})))

(defn selected-summary-key
  [scope time-range]
  (if (= scope :perps)
    (case time-range
      :day :perp-day
      :week :perp-week
      :month :perp-month
      :three-month :perp-three-month
      :six-month :perp-six-month
      :one-year :perp-one-year
      :two-year :perp-two-year
      :all-time :perp-all-time
      :perp-month)
    (case time-range
      :day :day
      :week :week
      :month :month
      :three-month :three-month
      :six-month :six-month
      :one-year :one-year
      :two-year :two-year
      :all-time :all-time
      :month)))

(defn summary-key-candidates
  [scope time-range]
  (let [primary (selected-summary-key scope time-range)]
    (case primary
      :day [:day :week :month :three-month :six-month :one-year :two-year :all-time]
      :week [:week :month :three-month :six-month :one-year :two-year :all-time :day]
      :month [:month :three-month :six-month :one-year :two-year :all-time :week :day]
      :three-month [:three-month :six-month :one-year :two-year :all-time :month :week :day]
      :six-month [:six-month :one-year :two-year :all-time :three-month :month :week :day]
      :one-year [:one-year :two-year :all-time :six-month :three-month :month :week :day]
      :two-year [:two-year :all-time :one-year :six-month :three-month :month :week :day]
      :all-time [:all-time :two-year :one-year :six-month :three-month :month :week :day]
      :perp-day [:perp-day :perp-week :perp-month :perp-three-month :perp-six-month :perp-one-year :perp-two-year :perp-all-time]
      :perp-week [:perp-week :perp-month :perp-three-month :perp-six-month :perp-one-year :perp-two-year :perp-all-time :perp-day]
      :perp-month [:perp-month :perp-three-month :perp-six-month :perp-one-year :perp-two-year :perp-all-time :perp-week :perp-day]
      :perp-three-month [:perp-three-month :perp-six-month :perp-one-year :perp-two-year :perp-all-time :perp-month :perp-week :perp-day]
      :perp-six-month [:perp-six-month :perp-one-year :perp-two-year :perp-all-time :perp-three-month :perp-month :perp-week :perp-day]
      :perp-one-year [:perp-one-year :perp-two-year :perp-all-time :perp-six-month :perp-three-month :perp-month :perp-week :perp-day]
      :perp-two-year [:perp-two-year :perp-all-time :perp-one-year :perp-six-month :perp-three-month :perp-month :perp-week :perp-day]
      :perp-all-time [:perp-all-time :perp-two-year :perp-one-year :perp-six-month :perp-three-month :perp-month :perp-week :perp-day]
      [primary])))

(defn- scope-all-time-key
  [scope]
  (if (= scope :perps)
    :perp-all-time
    :all-time))

(defn- derived-summary-cutoff-ms
  [summary-time-range end-time-ms]
  (case summary-time-range
    :three-month (vm-history/summary-window-cutoff-ms :three-month end-time-ms)
    :six-month (vm-history/summary-window-cutoff-ms :six-month end-time-ms)
    :one-year (vm-history/summary-window-cutoff-ms :one-year end-time-ms)
    :two-year (vm-history/summary-window-cutoff-ms :two-year end-time-ms)
    nil))

(defn derived-summary-entry
  [summary-by-key scope summary-time-range]
  (when-let [base-summary (get summary-by-key (scope-all-time-key scope))]
    (let [account-rows (vm-history/normalized-history-rows
                        (vm-history/account-value-history-rows base-summary))
          pnl-rows (vm-history/normalized-history-rows
                    (vm-history/pnl-history-rows base-summary))
          end-time-ms (or (some-> account-rows last :time-ms)
                          (some-> pnl-rows last :time-ms))
          cutoff-ms (derived-summary-cutoff-ms summary-time-range end-time-ms)]
      (when (number? cutoff-ms)
        (let [account-window (vm-history/history-window-rows account-rows cutoff-ms)
              pnl-window (vm-history/history-window-rows pnl-rows cutoff-ms)
              base-pnl (some-> pnl-window first :value)
              pnl-window* (vm-history/rebase-history-rows pnl-window (or base-pnl 0))]
          (when (or (seq account-window)
                    (seq pnl-window*))
            {:accountValueHistory account-window
             :pnlHistory pnl-window*}))))))

(defn selected-summary-entry
  [summary-by-key scope time-range]
  (let [summary-by-key* (normalize-summary-by-key summary-by-key)]
    (or (get summary-by-key* (selected-summary-key scope time-range))
        (derived-summary-entry summary-by-key* scope time-range)
        (some #(get summary-by-key* %) (summary-key-candidates scope time-range))
        (some-> summary-by-key* vals first))))

(defn pnl-delta
  [summary]
  (let [values (keep vm-history/history-point-value (or (:pnlHistory summary) []))]
    (when (seq values)
      (- (last values) (first values)))))

(defn max-drawdown-ratio
  [summary]
  (let [pnl-history (vec (or (:pnlHistory summary) []))
        account-history (vec (or (:accountValueHistory summary) []))]
    (when (and (seq pnl-history)
               (seq account-history))
      (loop [idx 0
             peak-pnl 0
             peak-account-value 0
             max-ratio 0]
        (if (>= idx (count pnl-history))
          max-ratio
          (let [pnl (vm-history/history-point-value (nth pnl-history idx))
                max-ratio* (if (and (number? pnl)
                                    (number? peak-account-value)
                                    (pos? peak-account-value))
                             (max max-ratio (/ (- peak-pnl pnl) peak-account-value))
                             max-ratio)
                account-value-at-index (vm-history/history-point-value (nth account-history idx nil))
                [peak-pnl* peak-account-value*]
                (if (and (number? pnl)
                         (>= pnl peak-pnl))
                  [pnl (if (number? account-value-at-index)
                         account-value-at-index
                         peak-account-value)]
                  [peak-pnl peak-account-value])]
            (recur (inc idx)
                   peak-pnl*
                   peak-account-value*
                   max-ratio*)))))))
