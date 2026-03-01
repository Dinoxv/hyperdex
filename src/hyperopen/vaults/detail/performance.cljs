(ns hyperopen.vaults.detail.performance
  (:require [clojure.string :as str]
            [hyperopen.portfolio.metrics :as portfolio-metrics]))

(def ^:private performance-periods-per-year
  365)

(defn- optional-number
  [value]
  (cond
    (number? value)
    (when (js/isFinite value)
      value)

    (string? value)
    (let [trimmed (str/trim value)]
      (when (seq trimmed)
        (let [parsed (js/Number trimmed)]
          (when (js/isFinite parsed)
            parsed))))

    :else nil))

(defn- normalize-percent-value
  [value]
  (when-let [n (optional-number value)]
    (if (<= (js/Math.abs n) 1)
      (* 100 n)
      n)))

(defn- snapshot-point-value
  [entry]
  (cond
    (number? entry) entry

    (and (sequential? entry)
         (>= (count entry) 2))
    (optional-number (second entry))

    (map? entry)
    (or (optional-number (:value entry))
        (optional-number (:pnl entry))
        (optional-number (:account-value entry))
        (optional-number (:accountValue entry)))

    :else
    nil))

(defn- last-snapshot-value
  [snapshot-values]
  (when (sequential? snapshot-values)
    (some->> snapshot-values
             (keep snapshot-point-value)
             seq
             last)))

(defn snapshot-value-by-range
  [row snapshot-range tvl]
  (let [raw (some-> (get-in row [:snapshot-by-key snapshot-range])
                    last-snapshot-value
                    optional-number)]
    (cond
      (nil? raw) nil
      (and (number? tvl)
           (pos? tvl)
           (> (js/Math.abs raw) 1000))
      (* 100 (/ raw tvl))
      :else
      (normalize-percent-value raw))))

(defn- with-utc-months-offset
  [time-ms months]
  (let [date (js/Date. time-ms)]
    (.setUTCMonth date (+ (.getUTCMonth date) months))
    (.getTime date)))

(defn- with-utc-years-offset
  [time-ms years]
  (let [date (js/Date. time-ms)]
    (.setUTCFullYear date (+ (.getUTCFullYear date) years))
    (.getTime date)))

(defn- summary-window-cutoff-ms
  [snapshot-range end-time-ms]
  (when (number? end-time-ms)
    (case snapshot-range
      :three-month (with-utc-months-offset end-time-ms -3)
      :six-month (with-utc-months-offset end-time-ms -6)
      :one-year (with-utc-years-offset end-time-ms -1)
      :two-year (with-utc-years-offset end-time-ms -2)
      nil)))

(defn- normalized-history-rows
  [rows]
  (->> (or rows [])
       (keep (fn [row]
               (let [time-ms (portfolio-metrics/history-point-time-ms row)
                     value (portfolio-metrics/history-point-value row)]
                 (when (and (number? time-ms)
                            (number? value))
                   [time-ms value]))))
       (sort-by first)
       vec))

(defn- history-window-rows
  [rows cutoff-ms]
  (if (number? cutoff-ms)
    (->> rows
         (filter (fn [[time-ms _value]]
                   (>= time-ms cutoff-ms)))
         vec)
    []))

(defn- rebase-history-rows
  [rows]
  (if-let [baseline (some-> rows first second)]
    (mapv (fn [[time-ms value]]
            [time-ms (- value baseline)])
          rows)
    []))

(defn- derived-portfolio-summary
  [all-time-summary snapshot-range]
  (let [account-rows (normalized-history-rows (:accountValueHistory all-time-summary))
        pnl-rows (normalized-history-rows (:pnlHistory all-time-summary))
        end-time-ms (or (some-> account-rows last first)
                        (some-> pnl-rows last first))
        cutoff-ms (summary-window-cutoff-ms snapshot-range end-time-ms)]
    (when (number? cutoff-ms)
      (let [account-window (history-window-rows account-rows cutoff-ms)
            pnl-window (history-window-rows pnl-rows cutoff-ms)
            pnl-window* (rebase-history-rows pnl-window)]
        (when (or (seq account-window)
                  (seq pnl-window*))
          (assoc all-time-summary
                 :accountValueHistory account-window
                 :pnlHistory pnl-window*))))))

(defn portfolio-summary
  [details snapshot-range]
  (let [portfolio (or (:portfolio details) {})
        all-time-summary (get portfolio :all-time)]
    (or (get portfolio snapshot-range)
        (derived-portfolio-summary all-time-summary snapshot-range)
        (get portfolio :month)
        (get portfolio :week)
        (get portfolio :day)
        all-time-summary
        {})))

(defn- history-point
  [row]
  (cond
    (and (sequential? row)
         (>= (count row) 2))
    {:time-ms (optional-number (first row))
     :value (optional-number (second row))}

    (map? row)
    {:time-ms (or (optional-number (:time row))
                  (optional-number (:timestamp row))
                  (optional-number (:time-ms row))
                  (optional-number (:timeMs row))
                  (optional-number (:ts row))
                  (optional-number (:t row)))
     :value (or (optional-number (:value row))
                (optional-number (:account-value row))
                (optional-number (:accountValue row))
                (optional-number (:pnl row)))}

    :else
    nil))

(defn- history-points
  [rows]
  (->> (if (sequential? rows) rows [])
       (keep history-point)
       (filter (fn [{:keys [time-ms value]}]
                 (and (number? time-ms)
                      (number? value))))
       (sort-by :time-ms)
       vec))

(defn- normalize-chart-point-value
  [series value]
  (when (number? value)
    (if (= series :returns)
      (let [rounded (/ (js/Math.round (* value 100)) 100)]
        (if (== rounded -0)
          0
          rounded))
      value)))

(defn- rows->chart-points
  [rows series]
  (->> rows
       (map-indexed (fn [idx row]
                      (let [{:keys [time-ms value]} (history-point row)
                            value* (normalize-chart-point-value series value)]
                        (when (and (number? time-ms)
                                   (number? value*))
                          {:index idx
                           :time-ms time-ms
                           :value value*}))))
       (keep identity)
       vec))

(defn- returns-history-points
  [state summary]
  (rows->chart-points (portfolio-metrics/returns-history-rows state summary :all)
                      :returns))

(defn chart-series-data
  [state summary]
  {:account-value (history-points (:accountValueHistory summary))
   :pnl (history-points (:pnlHistory summary))
   :returns (returns-history-points state summary)})

(defn cumulative-rows
  [points]
  (mapv (fn [{:keys [time-ms value]}]
          [time-ms value])
        (or points [])))

(defn- benchmark-performance-column
  [benchmark-cumulative-rows label-by-coin coin]
  (let [benchmark-daily-rows (portfolio-metrics/daily-compounded-returns benchmark-cumulative-rows)
        values (if (seq benchmark-daily-rows)
                 (portfolio-metrics/compute-performance-metrics {:strategy-daily-rows benchmark-daily-rows
                                                                 :rf 0
                                                                 :periods-per-year performance-periods-per-year
                                                                 :compounded true})
                 {})]
    {:coin coin
     :label (or (get label-by-coin coin)
                coin)
     :daily-rows benchmark-daily-rows
     :values values}))

(defn- with-performance-metric-columns
  [groups portfolio-values benchmark-columns]
  (let [primary-benchmark-values (or (some-> benchmark-columns first :values)
                                     {})
        benchmark-values-by-coin (into {}
                                       (map (fn [{:keys [coin values]}]
                                              [coin values]))
                                       benchmark-columns)]
    (mapv (fn [{:keys [rows] :as group}]
            (assoc group
                   :rows (mapv (fn [{:keys [key] :as row}]
                                 (assoc row
                                        :portfolio-value (get portfolio-values key)
                                        :benchmark-value (get primary-benchmark-values key)
                                        :benchmark-values (into {}
                                                               (map (fn [{:keys [coin]}]
                                                                      [coin (get-in benchmark-values-by-coin [coin key])]))
                                                               benchmark-columns)))
                               (or rows []))))
          (or groups []))))

(defn performance-metrics-model
  [returns-benchmark-selector strategy-cumulative-rows benchmark-cumulative-rows-by-coin]
  (let [strategy-daily-rows (portfolio-metrics/daily-compounded-returns strategy-cumulative-rows)
        selected-benchmark-coins (vec (or (:selected-coins returns-benchmark-selector)
                                          []))
        benchmark-label-by-coin (or (:label-by-coin returns-benchmark-selector)
                                    {})
        benchmark-columns (mapv (fn [coin]
                                  (benchmark-performance-column (or (get benchmark-cumulative-rows-by-coin coin)
                                                                    [])
                                                                benchmark-label-by-coin
                                                                coin))
                                selected-benchmark-coins)
        primary-benchmark-column (first benchmark-columns)
        benchmark-coin (:coin primary-benchmark-column)
        benchmark-daily-rows (or (:daily-rows primary-benchmark-column)
                                 [])
        portfolio-values (portfolio-metrics/compute-performance-metrics {:strategy-daily-rows strategy-daily-rows
                                                                         :benchmark-daily-rows benchmark-daily-rows
                                                                         :rf 0
                                                                         :periods-per-year performance-periods-per-year
                                                                         :compounded true})
        benchmark-values (or (:values primary-benchmark-column)
                             {})
        groups (with-performance-metric-columns (portfolio-metrics/metric-rows portfolio-values)
                 portfolio-values
                 benchmark-columns)
        benchmark-label (:label primary-benchmark-column)]
    {:benchmark-selected? (boolean (seq benchmark-columns))
     :benchmark-coin benchmark-coin
     :benchmark-label benchmark-label
     :benchmark-coins (mapv :coin benchmark-columns)
     :benchmark-columns (mapv (fn [{:keys [coin label]}]
                                {:coin coin
                                 :label label})
                              benchmark-columns)
     :values portfolio-values
     :benchmark-values benchmark-values
     :groups groups}))
