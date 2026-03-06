(ns hyperopen.views.portfolio.vm
  (:require [hyperopen.domain.trading :as trading]
            [hyperopen.portfolio.actions :as portfolio-actions]
            [hyperopen.views.portfolio.vm.benchmarks :as vm-benchmarks]
            [hyperopen.views.portfolio.vm.chart :as vm-chart]
            [hyperopen.views.portfolio.vm.chart-math :as vm-chart-math]
            [hyperopen.views.portfolio.vm.equity :as vm-equity]
            [hyperopen.views.portfolio.vm.metrics-bridge :as vm-metrics-bridge]
            [hyperopen.views.portfolio.vm.performance :as vm-performance]
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

(defn volume-14d-usd [state]
  (vm-volume/volume-14d-usd state))

(defn- selector-option-label [options selected-value]
  (or (some (fn [{:keys [value label]}]
              (when (= value selected-value)
                label))
            options)
      (some-> options first :label)
      ""))

(def ^:dynamic *build-benchmark-selector-options*
  vm-benchmarks/build-benchmark-selector-options)

(defn reset-portfolio-vm-cache!
  []
  (vm-benchmarks/reset-portfolio-vm-cache!))

(defn- returns-benchmark-selector-model [state]
  (binding [vm-benchmarks/*build-benchmark-selector-options* *build-benchmark-selector-options*]
    (vm-benchmarks/returns-benchmark-selector-model state)))

(defn- normalize-summary-by-key [summary-by-key]
  (vm-summary/normalize-summary-by-key summary-by-key))

(defn- selected-summary-key [scope time-range]
  (vm-summary/selected-summary-key scope time-range))

(defn- selected-summary-entry [summary-by-key scope time-range]
  (vm-summary/selected-summary-entry summary-by-key scope time-range))

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

(defn- build-metrics-request-data
  [strategy-cumulative-rows benchmark-cumulative-rows-by-coin selected-benchmark-coins]
  (vm-performance/build-metrics-request-data strategy-cumulative-rows
                                             benchmark-cumulative-rows-by-coin
                                             selected-benchmark-coins))

(defn- compute-metrics-sync [request-data]
  (vm-performance/compute-metrics-sync request-data))

(defn- performance-metrics-model
  [state summary-time-range returns-benchmark-selector benchmark-context]
  (binding [vm-performance/*metrics-worker* metrics-worker
            vm-performance/*last-metrics-request* last-metrics-request
            vm-performance/*metrics-request-signature* metrics-request-signature
            vm-performance/*build-metrics-request-data* build-metrics-request-data
            vm-performance/*request-metrics-computation!* request-metrics-computation!
            vm-performance/*compute-metrics-sync* compute-metrics-sync]
    (vm-performance/performance-metrics-model state
                                              summary-time-range
                                              returns-benchmark-selector
                                              benchmark-context)))

(defn- chart-line-path [points]
  (vm-chart-math/chart-line-path points))

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
        top-up-enabled? (vm-equity/top-up-abstraction-enabled? state)
        pnl (or (vm-summary/pnl-delta summary-entry)
                (optional-number (:unrealized-pnl metrics))
                0)
        volume-from-summary (or (optional-number (:vlm summary-entry))
                                (optional-number (:volume summary-entry)))
        volume-from-user-fees (vm-volume/volume-14d-usd-from-user-fees state)
        volume-14d (if (some? volume-from-user-fees)
                     volume-from-user-fees
                     (volume-14d-usd state))
        volume (or volume-from-summary
                   volume-14d
                   0)
        max-drawdown-pct (vm-summary/max-drawdown-ratio summary-entry)
        perps-equity (vm-equity/perp-account-equity state metrics)
        spot-equity (vm-equity/spot-account-equity metrics)
        vault-equity-value (vm-equity/vault-equity state summary-entry)
        staking-hype (vm-equity/staking-account-hype state)
        staking-usd (vm-equity/staking-value-usd state staking-hype)
        earn-equity (vm-equity/earn-balance state)
        total-equity (vm-equity/compute-total-equity {:top-up-enabled? top-up-enabled?
                                                      :vault-equity vault-equity-value
                                                      :spot-equity spot-equity
                                                      :staking-value-usd staking-usd
                                                      :perp-equity perps-equity
                                                      :earn-equity earn-equity})
        fees-default {:taker (number-or-zero (:taker trading/default-fees))
                      :maker (number-or-zero (:maker trading/default-fees))}
        fees (or (vm-volume/fees-from-user-fees (get-in state [:portfolio :user-fees]))
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
        chart (vm-chart/build-chart-model state
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
