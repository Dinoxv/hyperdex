(ns hyperopen.portfolio.metrics.catalog)

(def ^:private performance-metric-groups
  [{:id :overview
    :rows [{:key :time-in-market
            :label "Time in Market"
            :kind :percent}
           {:key :cumulative-return
            :label "Cumulative Return"
            :kind :percent}
           {:key :cagr
            :label "CAGR"
            :kind :percent}]}
   {:id :risk-adjusted
    :rows [{:key :sharpe
            :label "Sharpe"
            :kind :ratio}
           {:key :prob-sharpe-ratio
            :label "Prob. Sharpe Ratio"
            :kind :ratio}
           {:key :smart-sharpe
            :label "Smart Sharpe"
            :kind :ratio}
           {:key :sortino
            :label "Sortino"
            :kind :ratio}
           {:key :smart-sortino
            :label "Smart Sortino"
            :kind :ratio}
           {:key :sortino-sqrt2
            :label "Sortino/sqrt(2)"
            :kind :ratio}
           {:key :smart-sortino-sqrt2
            :label "Smart Sortino/sqrt(2)"
            :kind :ratio}
           {:key :omega
            :label "Omega"
            :kind :ratio}]}
   {:id :drawdown-and-risk
    :rows [{:key :max-drawdown
            :label "Max Drawdown"
            :kind :percent}
           {:key :max-dd-date
            :label "Max DD Date"
            :kind :date}
           {:key :max-dd-period-start
            :label "Max DD Period Start"
            :kind :date}
           {:key :max-dd-period-end
            :label "Max DD Period End"
            :kind :date}
           {:key :longest-dd-days
            :label "Longest DD Days"
            :kind :integer}
           {:key :volatility-ann
            :label "Volatility (ann.)"
            :kind :percent}
           {:key :r2
            :label "R^2"
            :kind :ratio}
           {:key :information-ratio
            :label "Information Ratio"
            :kind :ratio}
           {:key :calmar
            :label "Calmar"
            :kind :ratio}
           {:key :skew
            :label "Skew"
            :kind :ratio}
           {:key :kurtosis
            :label "Kurtosis"
            :kind :ratio}]}
   {:id :expectation-and-var
    :rows [{:key :expected-daily
            :label "Expected Daily"
            :kind :percent}
           {:key :expected-monthly
            :label "Expected Monthly"
            :kind :percent}
           {:key :expected-yearly
            :label "Expected Yearly"
            :kind :percent}
           {:key :kelly-criterion
            :label "Kelly Criterion"
            :kind :percent}
           {:key :risk-of-ruin
            :label "Risk of Ruin"
            :kind :ratio}
           {:key :daily-var
            :label "Daily Value-at-Risk"
            :kind :percent}
           {:key :expected-shortfall
            :label "Expected Shortfall (cVaR)"
            :kind :percent}]}
   {:id :streaks-and-pain
    :rows [{:key :max-consecutive-wins
            :label "Max Consecutive Wins"
            :kind :integer}
           {:key :max-consecutive-losses
            :label "Max Consecutive Losses"
            :kind :integer}
           {:key :gain-pain-ratio
            :label "Gain/Pain Ratio"
            :kind :ratio}
           {:key :gain-pain-1m
            :label "Gain/Pain (1M)"
            :kind :ratio}]}
   {:id :trade-shape
    :rows [{:key :payoff-ratio
            :label "Payoff Ratio"
            :kind :ratio}
           {:key :profit-factor
            :label "Profit Factor"
            :kind :ratio}
           {:key :common-sense-ratio
            :label "Common Sense Ratio"
            :kind :ratio}
           {:key :cpc-index
            :label "CPC Index"
            :kind :ratio}
           {:key :tail-ratio
            :label "Tail Ratio"
            :kind :ratio}
           {:key :outlier-win-ratio
            :label "Outlier Win Ratio"
            :kind :ratio}
           {:key :outlier-loss-ratio
            :label "Outlier Loss Ratio"
            :kind :ratio}]}
   {:id :period-returns
    :rows [{:key :mtd
            :label "MTD"
            :kind :percent}
           {:key :m3
            :label "3M"
            :kind :percent}
           {:key :m6
            :label "6M"
            :kind :percent}
           {:key :ytd
            :label "YTD"
            :kind :percent}
           {:key :y1
            :label "1Y"
            :kind :percent}
           {:key :y3-ann
            :label "3Y (ann.)"
            :kind :percent}
           {:key :y5-ann
            :label "5Y (ann.)"
            :kind :percent}
           {:key :y10-ann
            :label "10Y (ann.)"
            :kind :percent}
           {:key :all-time-ann
            :label "All-time (ann.)"
            :kind :percent}]}])

(defn metric-rows
  [metric-values]
  (let [metric-status (or (:metric-status metric-values)
                          {})
        metric-reason (or (:metric-reason metric-values)
                          {})]
    (mapv (fn [{:keys [rows] :as group}]
            (assoc group
                   :rows (mapv (fn [{:keys [key] :as row}]
                                 (assoc row
                                        :value (get metric-values key)
                                        :status (get metric-status key)
                                        :reason (get metric-reason key)))
                               rows)))
          performance-metric-groups)))
