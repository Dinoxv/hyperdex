(ns hyperopen.views.portfolio.vm
  (:require [clojure.string :as str]
            [hyperopen.domain.trading :as trading]
            [hyperopen.portfolio.actions :as portfolio-actions]
            [hyperopen.portfolio.metrics :as portfolio-metrics]
            [hyperopen.views.portfolio.vm.benchmarks :as vm-benchmarks]
            [hyperopen.views.portfolio.vm.chart :as vm-chart]
            [hyperopen.views.portfolio.vm.chart-math :as vm-chart-math]
            [hyperopen.views.portfolio.vm.equity :as vm-equity]
            [hyperopen.views.portfolio.vm.metrics-bridge :as vm-metrics-bridge]
            [hyperopen.views.portfolio.vm.summary :as vm-summary]
            [hyperopen.views.portfolio.vm.volume :as vm-volume]
            [hyperopen.views.account-equity-view :as account-equity-view]
            [hyperopen.views.account-info.projections :as projections]))

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
   {:value :three-month
    :label "3M"}
   {:value :six-month
    :label "6M"}
   {:value :one-year
    :label "1Y"}
   {:value :two-year
    :label "2Y"}
   {:value :all-time
    :label "All-time"}])

(def ^:private chart-tab-options
  [{:value :returns
    :label "Returns"}
   {:value :account-value
    :label "Account Value"}
   {:value :pnl
    :label "PNL"}])

(def ^:private performance-periods-per-year
  365)

(defn- normalize-worker-metrics-result
  [payload]
  (vm-metrics-bridge/normalize-worker-metrics-result payload))

(def metrics-worker
  vm-metrics-bridge/metrics-worker)

(def last-metrics-request
  vm-metrics-bridge/last-metrics-request)

(defn- request-metrics-computation!
  [request-data request-signature]
  (binding [vm-metrics-bridge/metrics-worker metrics-worker]
    (vm-metrics-bridge/request-metrics-computation! request-data request-signature)))

(defn- optional-number [value]
  (projections/parse-optional-num value))

(defn- number-or-zero [value]
  (if-let [n (optional-number value)]
    n
    0))

(defn- finite-number? [value]
  (and (number? value)
       (js/isFinite value)))

(defn volume-14d-usd [state]
  (vm-volume/volume-14d-usd state))

(defn- selector-option-label [options selected-value]
  (or (some (fn [{:keys [value label]}]
              (when (= value selected-value)
                label))
            options)
      (some-> options first :label)
      ""))

(defn- parse-cache-order [value]
  (let [parsed (cond
                 (number? value) value
                 (string? value) (js/parseInt value 10)
                 :else js/NaN)]
    (when (and (number? parsed)
               (not (js/isNaN parsed)))
      (js/Math.floor parsed))))

(defn- market-type-token [value]
  (cond
    (keyword? value) value
    (string? value) (some-> value str/trim str/lower-case keyword)
    :else nil))

(defn- benchmark-open-interest [market]
  (let [open-interest (optional-number (:openInterest market))]
    (if (finite-number? open-interest)
      open-interest
      0)))

(defn- benchmark-option-label [market]
  (let [symbol (some-> (:symbol market) str str/trim)
        coin (some-> (:coin market) str str/trim)
        dex (some-> (:dex market) str str/trim str/upper-case)
        market-type (market-type-token (:market-type market))
        type-label (case market-type
                     :spot "SPOT"
                     :perp "PERP"
                     nil)
        primary-label (or symbol coin "")]
    (cond
      (and (seq dex) (seq type-label)) (str primary-label " (" dex " " type-label ")")
      (seq type-label) (str primary-label " (" type-label ")")
      :else primary-label)))

(defn- benchmark-option-rank [market]
  [(- (benchmark-open-interest market))
   (or (parse-cache-order (:cache-order market))
       js/Number.MAX_SAFE_INTEGER)
   (str/lower-case (or (some-> (:symbol market) str str/trim) ""))
   (str/lower-case (or (some-> (:coin market) str str/trim) ""))
   (str/lower-case (or (some-> (:key market) str str/trim) ""))])

(def ^:dynamic *build-benchmark-selector-options*
  vm-benchmarks/build-benchmark-selector-options)

(defn reset-portfolio-vm-cache!
  []
  (vm-benchmarks/reset-portfolio-vm-cache!))

(defn- benchmark-selector-options
  [state]
  (binding [vm-benchmarks/*build-benchmark-selector-options* *build-benchmark-selector-options*]
    (vm-benchmarks/benchmark-selector-options state)))

(defn- returns-benchmark-selector-model [state]
  (binding [vm-benchmarks/*build-benchmark-selector-options* *build-benchmark-selector-options*]
    (vm-benchmarks/returns-benchmark-selector-model state)))

(defn- normalize-summary-by-key [summary-by-key]
  (vm-summary/normalize-summary-by-key summary-by-key))

(defn- selected-summary-key [scope time-range]
  (vm-summary/selected-summary-key scope time-range))

(defn- selected-summary-entry [summary-by-key scope time-range]
  (vm-summary/selected-summary-entry summary-by-key scope time-range))


(def ^:private empty-source-version-counter
  0)

(defn- metrics-request-signature
  [summary-time-range selected-benchmark-coins strategy-source-version benchmark-source-version-map]
  (vm-metrics-bridge/metrics-request-signature summary-time-range
                                               selected-benchmark-coins
                                               strategy-source-version
                                               benchmark-source-version-map))

(defn- benchmark-computation-context
  [state summary-entry summary-scope summary-time-range returns-benchmark-selector]
  (vm-benchmarks/benchmark-computation-context state
                                               summary-entry
                                               summary-scope
                                               summary-time-range
                                               returns-benchmark-selector))

(defn- benchmark-performance-column
  [benchmark-cumulative-rows label-by-coin coin]
  (let [benchmark-daily-rows (portfolio-metrics/daily-compounded-returns benchmark-cumulative-rows)
        values (if (seq benchmark-cumulative-rows)
                 (portfolio-metrics/compute-performance-metrics {:strategy-cumulative-rows benchmark-cumulative-rows
                                                                 :strategy-daily-rows benchmark-daily-rows
                                                                 :rf 0
                                                                 :periods-per-year performance-periods-per-year})
                 {})]
    {:coin coin
     :label (or (get label-by-coin coin)
                coin)
     :cumulative-rows benchmark-cumulative-rows
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
                                        :portfolio-status (get-in portfolio-values [:metric-status key])
                                        :portfolio-reason (get-in portfolio-values [:metric-reason key])
                                        :benchmark-value (get primary-benchmark-values key)
                                        :benchmark-status (get-in primary-benchmark-values [:metric-status key])
                                        :benchmark-reason (get-in primary-benchmark-values [:metric-reason key])
                                        :benchmark-values (into {}
                                                               (map (fn [{:keys [coin]}]
                                                                      [coin (get-in benchmark-values-by-coin [coin key])]))
                                                               benchmark-columns)
                                        :benchmark-statuses (into {}
                                                                 (map (fn [{:keys [coin]}]
                                                                        [coin (get-in benchmark-values-by-coin [coin :metric-status key])]))
                                                                 benchmark-columns)
                                        :benchmark-reasons (into {}
                                                                (map (fn [{:keys [coin]}]
                                                                       [coin (get-in benchmark-values-by-coin [coin :metric-reason key])]))
                                                                benchmark-columns)))
                               (or rows []))))
          (or groups []))))

(def ^:private hidden-portfolio-metric-keys
  #{:time-in-market})

(defn- remove-hidden-portfolio-metric-rows
  [groups]
  (->> (or groups [])
       (keep (fn [{:keys [rows] :as group}]
               (let [rows* (->> (or rows [])
                                (remove (fn [{:keys [key]}]
                                          (contains? hidden-portfolio-metric-keys key)))
                                vec)]
                 (when (seq rows*)
                   (assoc group :rows rows*)))))
       vec))

(defn- build-metrics-request-data
  [strategy-cumulative-rows benchmark-cumulative-rows-by-coin selected-benchmark-coins]
  (let [benchmark-requests (mapv (fn [coin]
                                   {:coin coin
                                    :request {:strategy-cumulative-rows (or (get benchmark-cumulative-rows-by-coin coin)
                                                                            [])}})
                                 selected-benchmark-coins)
        portfolio-request {:strategy-cumulative-rows strategy-cumulative-rows
                           :strategy-daily-rows (portfolio-metrics/daily-compounded-returns strategy-cumulative-rows)
                           :benchmark-cumulative-rows (or (some-> benchmark-requests first :request :strategy-cumulative-rows)
                                                          [])}]
    {:portfolio-request portfolio-request
     :benchmark-requests benchmark-requests}))

(defn- request-benchmark-daily-rows
  [portfolio-request]
  (vm-metrics-bridge/request-benchmark-daily-rows portfolio-request))

(defn- request-strategy-daily-rows
  [request]
  (vm-metrics-bridge/request-strategy-daily-rows request))

(defn- compute-metrics-sync [request-data]
  (vm-metrics-bridge/compute-metrics-sync request-data))

(defn- performance-metrics-model
  [state summary-time-range returns-benchmark-selector benchmark-context]
  (let [strategy-cumulative-rows (or (:strategy-cumulative-rows benchmark-context)
                                     [])
        benchmark-cumulative-rows-by-coin (or (:benchmark-cumulative-rows-by-coin benchmark-context)
                                              {})
        strategy-source-version (or (:strategy-source-version benchmark-context)
                                    empty-source-version-counter)
        benchmark-source-version-map (or (:benchmark-source-version-map benchmark-context)
                                         {})
        selected-benchmark-coins (vec (or (:selected-coins returns-benchmark-selector)
                                          []))
        benchmark-label-by-coin (or (:label-by-coin returns-benchmark-selector)
                                    {})
        request-signature (metrics-request-signature summary-time-range
                                                     selected-benchmark-coins
                                                     strategy-source-version
                                                     benchmark-source-version-map)
        worker @metrics-worker
        request-signature-changed? (not= request-signature
                                        (:signature @last-metrics-request))
        request-data (when (or (nil? worker)
                               request-signature-changed?)
                       (build-metrics-request-data strategy-cumulative-rows
                                                   benchmark-cumulative-rows-by-coin
                                                   selected-benchmark-coins))
        _ (when (and worker
                     request-signature-changed?
                     request-data)
            (request-metrics-computation! request-data request-signature))
        metrics-result (if worker
                         (get-in state [:portfolio-ui :metrics-result])
                         (compute-metrics-sync request-data))
        loading? (if worker
                   (boolean (get-in state [:portfolio-ui :metrics-loading?]))
                   false)
        portfolio-values (or (:portfolio-values metrics-result) {})
        benchmark-values-by-coin-result (or (:benchmark-values-by-coin metrics-result) {})
        benchmark-columns (mapv (fn [coin]
                                  {:coin coin
                                   :label (or (get benchmark-label-by-coin coin) coin)
                                   :cumulative-rows (or (get benchmark-cumulative-rows-by-coin coin)
                                                        [])
                                   :values (or (get benchmark-values-by-coin-result coin) {})})
                                selected-benchmark-coins)
        primary-benchmark-column (first benchmark-columns)
        benchmark-coin (:coin primary-benchmark-column)
        benchmark-values (or (:values primary-benchmark-column)
                             {})
        groups (with-performance-metric-columns
                 (remove-hidden-portfolio-metric-rows
                  (portfolio-metrics/metric-rows portfolio-values))
                 portfolio-values
                 benchmark-columns)
        benchmark-label (:label primary-benchmark-column)]
    {:loading? loading?
     :benchmark-selected? (boolean (seq benchmark-columns))
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

(defn- chart-line-path [points]
  (vm-chart-math/chart-line-path points))

(defn- build-chart-model
  [state summary-entry summary-scope returns-benchmark-selector benchmark-context]
  (vm-chart/build-chart-model state
                              summary-entry
                              summary-scope
                              returns-benchmark-selector
                              benchmark-context))

(defn- pnl-delta [summary]
  (vm-summary/pnl-delta summary))

(defn- max-drawdown-ratio [summary]
  (vm-summary/max-drawdown-ratio summary))

(defn- daily-user-vlm-rows [state]
  (vm-volume/daily-user-vlm-rows state))

(defn- daily-user-vlm-row-volume [row]
  (vm-volume/daily-user-vlm-row-volume row))

(defn- volume-14d-usd-from-user-fees [state]
  (vm-volume/volume-14d-usd-from-user-fees state))

(defn- fees-from-user-fees [user-fees]
  (vm-volume/fees-from-user-fees user-fees))

(defn- top-up-abstraction-enabled? [state]
  (vm-equity/top-up-abstraction-enabled? state))

(defn- earn-balance [state]
  (vm-equity/earn-balance state))

(defn- vault-equity [state summary]
  (vm-equity/vault-equity state summary))

(defn- perp-account-equity [state metrics]
  (vm-equity/perp-account-equity state metrics))

(defn- spot-account-equity [metrics]
  (vm-equity/spot-account-equity metrics))

(defn- staking-account-hype [state]
  (vm-equity/staking-account-hype state))

(defn- staking-value-usd [state staking-hype]
  (vm-equity/staking-value-usd state staking-hype))

(defn- compute-total-equity [values]
  (vm-equity/compute-total-equity values))

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
                 fees-default)
        returns-benchmark-selector (returns-benchmark-selector-model state)
        benchmark-context (benchmark-computation-context state
                                                         summary-entry
                                                         summary-scope
                                                         summary-time-range
                                                         returns-benchmark-selector)
        performance-metrics (performance-metrics-model state
                                                       summary-time-range
                                                       returns-benchmark-selector
                                                       benchmark-context)
        chart (build-chart-model state
                                 summary-entry
                                 summary-scope
                                 returns-benchmark-selector
                                 benchmark-context)]
    {:volume-14d-usd volume-14d
     :fees fees
     :performance-metrics performance-metrics
     :chart chart
     :selectors {:summary-scope {:value summary-scope
                                 :label (selector-option-label summary-scope-options summary-scope)
                                 :open? (boolean (get-in state [:portfolio-ui :summary-scope-dropdown-open?]))
                                 :options summary-scope-options}
                 :summary-time-range {:value summary-time-range
                                      :label (selector-option-label summary-time-range-options summary-time-range)
                                      :open? (boolean (get-in state [:portfolio-ui :summary-time-range-dropdown-open?]))
                                      :options summary-time-range-options}
                 :performance-metrics-time-range {:value summary-time-range
                                                  :label (selector-option-label summary-time-range-options summary-time-range)
                                                  :open? (boolean (get-in state [:portfolio-ui :performance-metrics-time-range-dropdown-open?]))
                                                  :options summary-time-range-options}
                 :returns-benchmark returns-benchmark-selector}
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
