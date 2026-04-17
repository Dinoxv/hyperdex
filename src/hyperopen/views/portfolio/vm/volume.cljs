(ns hyperopen.views.portfolio.vm.volume
  (:require [hyperopen.views.account-info.projections :as projections]
            [hyperopen.views.portfolio.vm.constants :as constants]))

(defn- optional-number
  [value]
  (projections/parse-optional-num value))

(defn- number-or-zero
  [value]
  (if-let [n (optional-number value)]
    n
    0))

(defn- fills-source
  [state]
  (or (get-in state [:orders :fills])
      (get-in state [:webdata2 :fills])
      []))

(defn- trade-values
  [rows]
  (keep (fn [row]
          (let [value (projections/trade-history-value-number row)
                time-ms (projections/trade-history-time-ms row)]
            (when (number? value)
              {:value value
               :time-ms time-ms})))
        rows))

(defonce fills-volume-cache (atom nil))

(defn volume-14d-usd
  [state]
  (let [fills (fills-source state)
        cache @fills-volume-cache
        values (if (and cache
                        (identical? fills (:fills cache)))
                 (:values cache)
                 (let [new-values (trade-values fills)]
                   (reset! fills-volume-cache {:fills fills
                                               :values new-values})
                   new-values))
        cutoff (- (.now js/Date) constants/fourteen-days-ms)
        recent-values (let [timed-values (keep :time-ms values)]
                        (if (seq timed-values)
                          (filter (fn [{:keys [time-ms]}]
                                    (and (number? time-ms)
                                         (>= time-ms cutoff)))
                                  values)
                          values))]
    (reduce (fn [acc {:keys [value]}]
              (+ acc value))
            0
            recent-values)))

(defn daily-user-vlm-rows
  [state]
  (let [rows (or (get-in state [:portfolio :user-fees :dailyUserVlm])
                 (get-in state [:portfolio :user-fees :daily-user-vlm]))]
    (if (sequential? rows)
      rows
      [])))

(defn- first-present
  [row keys]
  (some (fn [k]
          (let [value (get row k)]
            (when (some? value)
              value)))
        keys))

(defn- row-number
  [row keys fallback]
  (cond
    (map? row)
    (number-or-zero (first-present row keys))

    (and (sequential? row)
         (number? fallback)
         (< fallback (count row)))
    (number-or-zero (nth row fallback))

    :else
    0))

(defn daily-user-vlm-row-volume
  [row]
  (cond
    (map? row)
    (let [exchange (optional-number (first-present row [:exchange :exchange-volume]))
          user-cross (optional-number (first-present row [:userCross :user-cross :user_cross]))
          user-add (optional-number (first-present row [:userAdd :user-add :user_add]))]
      (if (or (number? user-cross)
              (number? user-add))
        (+ (or user-cross 0)
           (or user-add 0))
        (or exchange 0)))

    (and (sequential? row)
         (>= (count row) 2))
    (number-or-zero (second row))

    :else
    0))

(defn- completed-daily-user-vlm-rows
  [state]
  (let [rows (daily-user-vlm-rows state)]
    (if (seq rows)
      (butlast rows)
      [])))

(defn- volume-history-row-values
  [row]
  {:exchange-volume (row-number row [:exchange :exchange-volume] 1)
   :weighted-maker-volume (row-number row [:userAdd :user-add :user_add] 3)
   :weighted-taker-volume (row-number row [:userCross :user-cross :user_cross] 2)})

(defn- sum-history-values
  [rows value-key]
  (reduce (fn [acc row]
            (+ acc (get (volume-history-row-values row) value-key 0)))
          0
          rows))

(defn volume-history-model
  [state]
  (let [completed-rows (completed-daily-user-vlm-rows state)
        totals {:exchange-volume (sum-history-values completed-rows :exchange-volume)
                :weighted-maker-volume (sum-history-values completed-rows :weighted-maker-volume)
                :weighted-taker-volume (sum-history-values completed-rows :weighted-taker-volume)}]
    {:open? (true? (get-in state [:portfolio-ui :volume-history-open?]))
     :loading? (true? (get-in state [:portfolio :user-fees-loading?]))
     :error (get-in state [:portfolio :user-fees-error])
     :rows [(assoc totals
                   :id :total
                   :date-label "Total")]
     :totals totals}))

(defn volume-14d-usd-from-user-fees
  [state]
  (let [rows (completed-daily-user-vlm-rows state)]
    (when (seq (daily-user-vlm-rows state))
      (reduce (fn [acc row]
                (+ acc (daily-user-vlm-row-volume row)))
              0
              rows))))

(defn fees-from-user-fees
  [user-fees]
  (let [referral-discount (number-or-zero (:activeReferralDiscount user-fees))
        cross-rate (optional-number (:userCrossRate user-fees))
        add-rate (optional-number (:userAddRate user-fees))
        adjusted-cross-rate (when (number? cross-rate)
                              (* cross-rate (- 1 referral-discount)))
        adjusted-add-rate (when (number? add-rate)
                            (if (pos? add-rate)
                              (* add-rate (- 1 referral-discount))
                              add-rate))]
    (when (and (number? adjusted-cross-rate)
               (number? adjusted-add-rate))
      {:taker (* 100 adjusted-cross-rate)
       :maker (* 100 adjusted-add-rate)})))
