(ns hyperopen.views.portfolio.montecarlo.panel
  "Top-level layout for the portfolio Monte Carlo tab. Assembles the intro,
  controls, equity-paths chart, probability/percentile rail, distribution
  histograms, and method footnote from the `montecarlo-model` view model, and
  renders explanatory states when there is too little realized history to
  resample."
  (:require [hyperopen.views.portfolio.montecarlo.chart :as chart]
            [hyperopen.views.portfolio.montecarlo.controls :as controls]
            [hyperopen.views.portfolio.montecarlo.distributions :as distributions]
            [hyperopen.views.portfolio.montecarlo.summary :as summary]))

(defn- intro
  [{:keys [method-tag]}]
  [:div {:class ["mc-intro"]}
   [:div
    [:div {:class ["mc-eyebrow"]} "Probabilistic Forecast"]
    [:h2 {:class ["mc-title"]} "Monte Carlo Simulation"]
    [:p {:class ["mc-lede"]}
     "Resamples your realized daily returns thousands of times to map the range of outcomes the same strategy could produce. Preserves your return distribution while reshuffling the path — isolating luck from skill across drawdowns, Sharpe and terminal value."]]
   [:span {:class ["mc-method-tag"]}
    [:i {:class ["mc-dot"]}]
    method-tag]])

(defn- chart-card
  [{:keys [result run-key controls]}]
  [:div {:class ["mc-card" "mc-chart-card"]}
   [:div {:class ["mc-chart-head"]}
    [:h3 {:class ["mc-chart-title"]}
     (str "Simulated equity paths · " (:horizon controls) "-day forecast")]
    [:div {:class ["mc-legend"]}
     [:span [:i {:class ["mc-swatch" "mc-swatch-accent"]}] "Median (P50)"]
     [:span [:i {:class ["mc-swatch" "mc-swatch-band"]}] "P5–P95 band"]
     [:span [:i {:class ["mc-swatch" "mc-swatch-gold"]}] "Goal"]
     [:span [:i {:class ["mc-swatch" "mc-swatch-red"]}] "Bust"]]]
   [:div {:class ["mc-canvas-wrap"]}
    [:div {:class ["mc-canvas-host"]
           :data-role "portfolio-monte-carlo-equity-canvas"
           :replicant/on-render (chart/spaghetti-on-render
                                 {:result result
                                  :show-paths? true
                                  :path-count 120
                                  :height 420
                                  :update-key run-key})}]]])

(defn- footnote
  [{:keys [controls sample-size]}]
  (let [{:keys [horizon bust goal]} controls]
    [:div {:class ["mc-foot"]}
     [:b "Method. "]
     (str "Each path draws " horizon
          " daily returns at random (with replacement) from your realized history of "
          sample-size
          " trading days — preserving the empirical return distribution while breaking time-ordering. ")
     [:b "Bust "]
     (str "counts paths whose worst peak-to-trough drawdown breaches " bust "%; ")
     [:b "goal "]
     (str "counts paths ending at or above " goal
          "%. Past distribution is not a guarantee of future results — this is a range-of-outcomes tool, not a prediction.")]))

(defn- notice
  [{:keys [title body]}]
  [:div {:class ["mc-card" "mc-notice"]
         :data-role "portfolio-monte-carlo-notice"}
   [:div {:class ["mc-notice-title"]} title]
   [:p {:class ["mc-notice-body"]} body]])

(defn monte-carlo-card
  [{:keys [status sample-size min-sample] :as model}]
  [:div {:class ["portfolio-monte-carlo"]
         :data-role "portfolio-monte-carlo"}
   (intro model)
   (controls/controls-bar model)
   (case status
     :ready
     (let [{:keys [result run-key controls live-equity]} model]
       [:div {:class ["mc-body"]}
        [:div {:class ["mc-grid"]}
         (chart-card {:result result :run-key run-key :controls controls})
         [:div {:class ["mc-rail"]}
          (summary/prob-card {:result result :controls controls})
          (summary/percentile-table {:result result :controls controls :live-equity live-equity})]]
        (distributions/distributions {:result result :controls controls :run-key run-key})
        (footnote model)])

     :insufficient-history
     (notice {:title "Not enough history yet"
              :body (str "Monte Carlo needs at least " min-sample
                         " days of realized returns to resample from. This portfolio has "
                         sample-size
                         " so far — check back as more daily history accrues, or widen the time range.")})

     (notice {:title "No realized returns yet"
              :body "There is no realized daily-return history for the selected scope and time range, so there is nothing to resample. Once this portfolio has trading history, the forecast will populate here."}))])
