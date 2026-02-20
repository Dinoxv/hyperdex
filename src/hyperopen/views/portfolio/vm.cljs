(ns hyperopen.views.portfolio.vm
  (:require [clojure.string :as str]
            [hyperopen.domain.trading :as trading]
            [hyperopen.portfolio.actions :as portfolio-actions]
            [hyperopen.views.account-equity-view :as account-equity-view]
            [hyperopen.views.account-info.projections :as projections]))

(def ^:private fourteen-days-ms
  (* 14 24 60 60 1000))

(def ^:private summary-scope-options
  [{:value :all
    :label "Perps + Spot + Vaults"}
   {:value :perps
    :label "Perps"}])

(def ^:private summary-time-range-options
  [{:value :day
    :label "24H"}
   {:value :week
    :label "7D"}
   {:value :month
    :label "30D"}
   {:value :all-time
    :label "All-time"}])

(defn- optional-number [value]
  (projections/parse-optional-num value))

(defn- number-or-zero [value]
  (if-let [n (optional-number value)]
    n
    0))

(defn- fills-source [state]
  (or (get-in state [:orders :fills])
      (get-in state [:webdata2 :fills])
      []))

(defn- trade-values [rows]
  (keep (fn [row]
          (let [value (projections/trade-history-value-number row)
                time-ms (projections/trade-history-time-ms row)]
            (when (number? value)
              {:value value
               :time-ms time-ms})))
        rows))

(defn volume-14d-usd [state]
  (let [values (trade-values (fills-source state))
        cutoff (- (.now js/Date) fourteen-days-ms)
        in-window (filter (fn [{:keys [time-ms]}]
                            (and (number? time-ms)
                                 (>= time-ms cutoff)))
                          values)
        selected (if (seq in-window) in-window values)]
    (reduce (fn [acc {:keys [value]}]
              (+ acc value))
            0
            selected)))

(defn- selector-option-label [options selected-value]
  (or (some (fn [{:keys [value label]}]
              (when (= value selected-value)
                label))
            options)
      (some-> options first :label)
      ""))

(defn- canonical-summary-key [value]
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
          "alltime" :all-time
          "all-time" :all-time
          "perpday" :perp-day
          "perp-day" :perp-day
          "perpweek" :perp-week
          "perp-week" :perp-week
          "perpmonth" :perp-month
          "perp-month" :perp-month
          "perpalltime" :perp-all-time
          "perp-all-time" :perp-all-time
          (keyword token))))))

(defn- normalize-summary-by-key [summary-by-key]
  (reduce-kv (fn [acc key value]
               (let [summary-key (canonical-summary-key key)]
                 (if (and summary-key
                          (map? value))
                   (assoc acc summary-key value)
                   acc)))
             {}
             (or summary-by-key {})))

(defn- selected-summary-key [scope time-range]
  (if (= scope :perps)
    (case time-range
      :day :perp-day
      :week :perp-week
      :month :perp-month
      :all-time :perp-all-time
      :perp-month)
    (case time-range
      :day :day
      :week :week
      :month :month
      :all-time :all-time
      :month)))

(defn- summary-key-candidates [scope time-range]
  (let [primary (selected-summary-key scope time-range)]
    (case primary
      :day [:day :all-time :month :week]
      :week [:week :month :all-time :day]
      :month [:month :all-time :week :day]
      :all-time [:all-time :month :week :day]
      :perp-day [:perp-day :perp-all-time :perp-month :perp-week]
      :perp-week [:perp-week :perp-month :perp-all-time :perp-day]
      :perp-month [:perp-month :perp-all-time :perp-week :perp-day]
      :perp-all-time [:perp-all-time :perp-month :perp-week :perp-day]
      [primary])))

(defn- selected-summary-entry [summary-by-key scope time-range]
  (or (some #(get summary-by-key %) (summary-key-candidates scope time-range))
      (some-> summary-by-key vals first)))

(defn- history-point-value [row]
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

(defn- pnl-delta [summary]
  (let [values (keep history-point-value (or (:pnlHistory summary) []))]
    (when (seq values)
      (- (last values) (first values)))))

(defn- max-drawdown-ratio [summary]
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
          (let [pnl (history-point-value (nth pnl-history idx))
                max-ratio* (if (and (number? pnl)
                                    (number? peak-account-value)
                                    (pos? peak-account-value))
                             (max max-ratio (/ (- peak-pnl pnl) peak-account-value))
                             max-ratio)
                account-value-at-index (history-point-value (nth account-history idx nil))
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

(defn- daily-user-vlm-rows [state]
  (let [rows (or (get-in state [:portfolio :user-fees :dailyUserVlm])
                 (get-in state [:portfolio :user-fees :daily-user-vlm]))]
    (if (sequential? rows)
      rows
      [])))

(defn- daily-user-vlm-row-volume [row]
  (cond
    (map? row)
    (let [exchange (optional-number (:exchange row))
          user-cross (optional-number (:userCross row))
          user-add (optional-number (:userAdd row))]
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

(defn- volume-14d-usd-from-user-fees [state]
  (let [rows (daily-user-vlm-rows state)]
    (when (seq rows)
      (reduce (fn [acc row]
                (+ acc (daily-user-vlm-row-volume row)))
              0
              (butlast rows)))))

(defn- fees-from-user-fees [user-fees]
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

(defn- top-up-abstraction-enabled? [state]
  (= :unified (get-in state [:account :mode])))

(defn- earn-balance [state]
  (number-or-zero (get-in state [:borrow-lend :total-supplied-usd])))

(defn- vault-equity [state summary]
  (or (optional-number (get-in state [:webdata2 :totalVaultEquity]))
      (optional-number (:totalVaultEquity summary))
      0))

(defn- perp-account-equity [state metrics]
  (or (optional-number (get-in state [:webdata2 :clearinghouseState :marginSummary :accountValue]))
      (optional-number (get-in state [:webdata2 :clearinghouseState :crossMarginSummary :accountValue]))
      (optional-number (:cross-account-value metrics))
      (optional-number (:perps-value metrics))
      0))

(defn- spot-account-equity [metrics]
  (number-or-zero (:spot-equity metrics)))

(defn- staking-account-hype [state]
  (or (optional-number (get-in state [:staking :total-hype]))
      (optional-number (get-in state [:staking :total]))
      0))

(defn- staking-value-usd [_state _staking-hype]
  0)

(defn- compute-total-equity
  [{:keys [top-up-enabled?
           vault-equity
           spot-equity
           staking-value-usd
           perp-equity
           earn-equity]}]
  (let [base-total (+ (number-or-zero vault-equity)
                      (number-or-zero spot-equity)
                      (number-or-zero staking-value-usd))]
    (if top-up-enabled?
      base-total
      (+ base-total
         (number-or-zero perp-equity)
         (number-or-zero earn-equity)))))

(defn portfolio-vm [state]
  (let [metrics (account-equity-view/account-equity-metrics state)
        summary-by-key (normalize-summary-by-key (get-in state [:portfolio :summary-by-key]))
        summary-scope (portfolio-actions/normalize-summary-scope
                       (get-in state [:portfolio-ui :summary-scope]
                               portfolio-actions/default-summary-scope))
        summary-time-range (portfolio-actions/normalize-summary-time-range
                            (get-in state [:portfolio-ui :summary-time-range]
                                    portfolio-actions/default-summary-time-range))
        summary-entry (selected-summary-entry summary-by-key summary-scope summary-time-range)
        selected-key (selected-summary-key summary-scope summary-time-range)
        top-up-enabled? (top-up-abstraction-enabled? state)
        pnl (or (pnl-delta summary-entry)
                (optional-number (:unrealized-pnl metrics))
                0)
        volume-from-summary (or (optional-number (:vlm summary-entry))
                                (optional-number (:volume summary-entry)))
        volume-from-user-fees (volume-14d-usd-from-user-fees state)
        volume-14d (if (some? volume-from-user-fees)
                     volume-from-user-fees
                     (volume-14d-usd state))
        volume (or volume-from-summary
                   volume-14d
                   0)
        max-drawdown-pct (max-drawdown-ratio summary-entry)
        perps-equity (perp-account-equity state metrics)
        spot-equity (spot-account-equity metrics)
        vault-equity-value (vault-equity state summary-entry)
        staking-hype (staking-account-hype state)
        staking-usd (staking-value-usd state staking-hype)
        earn-equity (earn-balance state)
        total-equity (compute-total-equity {:top-up-enabled? top-up-enabled?
                                            :vault-equity vault-equity-value
                                            :spot-equity spot-equity
                                            :staking-value-usd staking-usd
                                            :perp-equity perps-equity
                                            :earn-equity earn-equity})
        fees-default {:taker (number-or-zero (:taker trading/default-fees))
                      :maker (number-or-zero (:maker trading/default-fees))}
        fees (or (fees-from-user-fees (get-in state [:portfolio :user-fees]))
                 fees-default)]
    {:volume-14d-usd volume-14d
     :fees fees
     :selectors {:summary-scope {:value summary-scope
                                 :label (selector-option-label summary-scope-options summary-scope)
                                 :open? (boolean (get-in state [:portfolio-ui :summary-scope-dropdown-open?]))
                                 :options summary-scope-options}
                 :summary-time-range {:value summary-time-range
                                      :label (selector-option-label summary-time-range-options summary-time-range)
                                      :open? (boolean (get-in state [:portfolio-ui :summary-time-range-dropdown-open?]))
                                      :options summary-time-range-options}}
     :summary {:selected-key selected-key
               :pnl pnl
               :volume volume
               :max-drawdown-pct max-drawdown-pct
               :total-equity total-equity
               :show-perps-account-equity? (not top-up-enabled?)
               :perps-account-equity perps-equity
               :spot-equity-label (if top-up-enabled?
                                    "Trading Equity"
                                    "Spot Account Equity")
               :spot-account-equity spot-equity
               :show-earn-balance? (not top-up-enabled?)
               :earn-balance earn-equity
               :show-vault-equity? true
               :vault-equity vault-equity-value
               :show-staking-account? true
               :staking-account-hype staking-hype}}))
