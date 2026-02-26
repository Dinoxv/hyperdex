(ns hyperopen.portfolio.metrics
  (:require [clojure.string :as str]
            [hyperopen.views.account-info.projections :as projections]))

(defn- optional-number [value]
  (projections/parse-optional-num value))

(defn- number-or-zero [value]
  (if-let [n (optional-number value)]
    n
    0))

(defn- finite-number? [value]
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

(defn- normalize-address [value]
  (some-> value str str/lower-case str/trim))

(defn- same-address?
  [left right]
  (let [left* (normalize-address left)
        right* (normalize-address right)]
    (and (seq left*)
         (seq right*)
         (= left* right*))))

(defn- canonical-ledger-delta-type
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      (let [token (-> text
                      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
                      str/lower-case
                      (str/replace #"[^a-z0-9]+" "-")
                      (str/replace #"(^-+)|(-+$)" ""))]
        (keyword token)))))

(defn- ledger-row-id
  [row]
  (let [hash* (some-> (:hash row) str str/lower-case str/trim)
        time-ms (history-point-time-ms row)
        delta (:delta row)]
    (when (or (seq hash*)
              (number? time-ms)
              (some? delta))
      ;; Dedupe exact duplicates across bootstrap/websocket sources without
      ;; collapsing distinct deltas that share a transaction hash.
      (str (or hash* "")
           "|"
           (or time-ms "")
           "|"
           (pr-str delta)))))

(defn- combined-ledger-rows
  [state]
  (let [rows (concat (or (get-in state [:portfolio :ledger-updates]) [])
                     (or (get-in state [:orders :ledger]) []))
        normalized (->> rows
                        (keep (fn [row]
                                (let [time-ms (history-point-time-ms row)]
                                  (when (number? time-ms)
                                    {:id (ledger-row-id row)
                                     :time-ms time-ms
                                     :row row}))))
                        (sort-by :time-ms))]
    (->> normalized
         (reduce (fn [{:keys [seen rows]} {:keys [id row]}]
                   (let [id* (or id (str "row-" (count seen)))]
                     (if (contains? seen id*)
                       {:seen seen
                        :rows rows}
                       {:seen (conj seen id*)
                        :rows (conj rows row)})))
                 {:seen #{}
                  :rows []})
         :rows
         vec)))

(defn- transfer-flow
  [amount sender destination current-user-address]
  (let [sender? (same-address? sender current-user-address)
        destination? (same-address? destination current-user-address)]
    (cond
      (and sender? (not destination?)) (- amount)
      (and destination? (not sender?)) amount
      :else 0)))

(defn- usdc-fee
  [delta]
  (let [fee (number-or-zero (:fee delta))
        fee-token (some-> (:feeToken delta) str str/upper-case)]
    (if (or (nil? fee-token)
            (= fee-token "")
            (= fee-token "USDC"))
      fee
      0)))

(defn- ledger-row-flow-usd
  [row summary-scope current-user-address]
  (let [delta (:delta row)]
    (cond
      (number? (optional-number delta))
      (optional-number delta)

      (map? delta)
      (case (canonical-ledger-delta-type (:type delta))
        :deposit
        (number-or-zero (:usdc delta))

        :withdraw
        (- (+ (number-or-zero (:usdc delta))
              (number-or-zero (:fee delta))))

        :account-class-transfer
        (if (= summary-scope :perps)
          (let [amount (number-or-zero (:usdc delta))]
            (if (true? (:toPerp delta))
              amount
              (- amount)))
          0)

        :sub-account-transfer
        (transfer-flow (number-or-zero (:usdc delta))
                       (:user delta)
                       (:destination delta)
                       current-user-address)

        :internal-transfer
        (let [sender? (same-address? (:user delta) current-user-address)
              amount (+ (number-or-zero (:usdc delta))
                        (if sender?
                          (number-or-zero (:fee delta))
                          0))]
          (transfer-flow amount
                         (:user delta)
                         (:destination delta)
                         current-user-address))

        :spot-transfer
        (let [sender? (same-address? (:user delta) current-user-address)
              amount (+ (number-or-zero (or (:usdcValue delta)
                                            (:usdc delta)))
                        (if sender?
                          (usdc-fee delta)
                          0))]
          (transfer-flow amount
                         (:user delta)
                         (:destination delta)
                         current-user-address))

        :send
        (let [sender? (same-address? (:user delta) current-user-address)
              amount (+ (number-or-zero (or (:usdcValue delta)
                                            (:usdc delta)))
                        (if sender?
                          (usdc-fee delta)
                          0))]
          (transfer-flow amount
                         (:user delta)
                         (:destination delta)
                         current-user-address))

        0)

      :else
      nil)))

(defn- ledger-flow-events
  [state summary-scope]
  (let [current-user-address (normalize-address (get-in state [:wallet :address]))]
    (->> (combined-ledger-rows state)
         (keep (fn [row]
                 (let [time-ms (history-point-time-ms row)
                       flow (ledger-row-flow-usd row summary-scope current-user-address)]
                   (when (and (number? time-ms)
                              (finite-number? flow)
                              (not (zero? flow)))
                     {:time-ms time-ms
                      :flow flow}))))
         (sort-by :time-ms)
         vec)))

(defn- account-history-points
  [summary]
  (->> (or (:accountValueHistory summary) [])
       (map-indexed (fn [idx row]
                      (let [value (history-point-value row)
                            time-ms (history-point-time-ms row)]
                        (when (finite-number? value)
                          {:index idx
                           :time-ms (or time-ms idx)
                           :value value}))))
       (keep identity)
       (sort-by :time-ms)
       vec))

(defn- interval-flow-stats
  [flows start-time-ms end-time-ms]
  (let [duration-ms (- end-time-ms start-time-ms)
        interval-flows (filter (fn [{:keys [time-ms]}]
                                 (and (number? time-ms)
                                      (> time-ms start-time-ms)
                                      (<= time-ms end-time-ms)))
                               flows)
        net-flow (reduce (fn [acc {:keys [flow]}]
                           (+ acc flow))
                         0
                         interval-flows)
        weighted-flow (if (pos? duration-ms)
                        (reduce (fn [acc {:keys [time-ms flow]}]
                                  (let [weight (/ (- end-time-ms time-ms) duration-ms)
                                        weight* (cond
                                                  (< weight 0) 0
                                                  (> weight 1) 1
                                                  :else weight)]
                                    (+ acc (* flow weight*))))
                                0
                                interval-flows)
                        0)]
    {:net-flow net-flow
     :weighted-flow weighted-flow}))

(defn returns-history-rows
  [state summary summary-scope]
  (let [points (account-history-points summary)
        anchor-index (first (keep-indexed (fn [idx {:keys [value]}]
                                            (when (pos? value)
                                              idx))
                                          points))]
    (if (number? anchor-index)
      (let [points* (subvec points anchor-index)
            flows (ledger-flow-events state summary-scope)]
        (if (seq points*)
          (loop [idx 1
                 previous (first points*)
                 cumulative-factor 1
                 output [[(:time-ms (first points*)) 0]]
                 point-count (count points*)]
            (if (>= idx point-count)
              output
              (let [current (nth points* idx)
                    start-time-ms (:time-ms previous)
                    end-time-ms (:time-ms current)
                    {:keys [net-flow weighted-flow]} (interval-flow-stats flows start-time-ms end-time-ms)
                    denominator (+ (:value previous) weighted-flow)
                    numerator (- (:value current) (:value previous) net-flow)
                    period-return (if (and (finite-number? denominator)
                                           (pos? denominator))
                                    (/ numerator denominator)
                                    0)
                    period-return* (if (finite-number? period-return)
                                     (max -0.999999 period-return)
                                     0)
                    cumulative-factor* (* cumulative-factor (+ 1 period-return*))
                    cumulative-percent (* 100 (- cumulative-factor* 1))
                    cumulative-percent* (if (finite-number? cumulative-percent)
                                          cumulative-percent
                                          (* 100 (- cumulative-factor 1)))]
                (recur (inc idx)
                       current
                       cumulative-factor*
                       (conj output [(:time-ms current) cumulative-percent*])
                       point-count))))
          []))
      [])))

(defn cumulative-percent-rows->interval-returns
  [cumulative-percent-rows]
  (let [rows (->> (or cumulative-percent-rows [])
                  (keep (fn [row]
                          (let [time-ms (history-point-time-ms row)
                                value (history-point-value row)]
                            (when (and (number? time-ms)
                                       (finite-number? value))
                              {:time-ms time-ms
                               :value value}))))
                  (sort-by :time-ms)
                  vec)
        count* (count rows)]
    (if (< count* 2)
      []
      (loop [idx 1
             previous (first rows)
             output []]
        (if (>= idx count*)
          output
          (let [current (nth rows idx)
                previous-ratio (/ (:value previous) 100)
                current-ratio (/ (:value current) 100)
                denominator (+ 1 previous-ratio)
                period-return (if (and (finite-number? denominator)
                                       (pos? denominator))
                                (- (/ (+ 1 current-ratio) denominator) 1)
                                0)
                period-return* (if (finite-number? period-return)
                                 period-return
                                 0)]
            (recur (inc idx)
                   current
                   (conj output
                         {:time-ms (:time-ms current)
                          :return period-return*}))))))))

(defn- utc-day-key [time-ms]
  (subs (.toISOString (js/Date. time-ms)) 0 10))

(defn daily-compounded-returns
  [cumulative-percent-rows]
  (let [rows (cumulative-percent-rows->interval-returns cumulative-percent-rows)]
    (if (empty? rows)
      []
      (loop [remaining rows
             current-day nil
             current-factor 1
             current-time-ms nil
             output []]
        (if (empty? remaining)
          (if (some? current-day)
            (conj output
                  {:day current-day
                   :time-ms current-time-ms
                   :return (- current-factor 1)})
            output)
          (let [{:keys [time-ms return]} (first remaining)
                day (utc-day-key time-ms)
                factor (+ 1 return)]
            (if (= day current-day)
              (recur (rest remaining)
                     current-day
                     (* current-factor factor)
                     time-ms
                     output)
              (recur (rest remaining)
                     day
                     factor
                     time-ms
                     (if (some? current-day)
                       (conj output
                             {:day current-day
                              :time-ms current-time-ms
                              :return (- current-factor 1)})
                       output)))))))))

(defn strategy-daily-compounded-returns
  [state summary summary-scope]
  (daily-compounded-returns (returns-history-rows state summary summary-scope)))
