(ns hyperopen.views.portfolio.montecarlo.distributions
  "The four distribution-histogram cards (total return, max drawdown, Sharpe,
  annualized vol) on the Monte Carlo tab. The data-role prefix arrives via the
  model's `:chrome` so the portfolio and vault surfaces share these cards."
  (:require [hyperopen.views.portfolio.montecarlo.chart :as chart]
            [hyperopen.views.portfolio.montecarlo.format :as fmt]))

(defn- dist-card
  [{:keys [title dist fmt-kind sign-by-value? threshold range-fmt update-key data-role-prefix]}]
  [:div {:class ["mc-card" "mc-dist-card"]
         :data-role (str data-role-prefix "-dist-" (name (first update-key)))}
   [:div {:class ["mc-dist-title"]} title]
   [:div {:class ["mc-dist-range"]}
    "P5 " [:b (range-fmt (:p5 dist))] " · P95 " [:b (range-fmt (:p95 dist))]]
   [:div {:class ["mc-dist-canvas"]
          :replicant/on-render (chart/histogram-on-render
                                {:dist dist
                                 :fmt fmt-kind
                                 :sign-by-value? sign-by-value?
                                 :threshold threshold
                                 :median (:median dist)
                                 :height 92
                                 :update-key update-key})}]
   [:div {:class ["mc-dist-foot"]}
    [:span (str "median " (range-fmt (:median dist)))]
    [:span (str "μ " (range-fmt (:mean dist)))]]])

(defn- realized-strip
  "Single-value chips for the metrics a shuffle leaves unchanged. Total return,
  Sharpe, and annualized vol are functions only of the *set* of daily returns,
  so reordering them does not move these values — their histograms would be a
  single spike. In shuffle mode we show the fixed values as chips instead."
  [{:keys [terminal sharpe vol pct0 pctp0 r2 data-role-prefix]}]
  [:div {:class ["mc-card" "mc-realized-strip"]
         :data-role (str data-role-prefix "-realized-stats")}
   [:div {:class ["mc-realized-strip-title"]} "Realized · fixed across orderings"]
   [:div {:class ["mc-chip-row"]}
    (for [[k v] [["Total return" (pct0 (:p50 terminal))]
                 ["Sharpe" (r2 (:p50 sharpe))]
                 ["Annualized vol" (pctp0 (:p50 vol))]]]
      ^{:key k}
      [:div {:class ["mc-chip"]}
       [:div {:class ["mc-chip-k"]} k]
       [:div {:class ["mc-chip-v"]} v]])]])

(defn distributions
  [{:keys [result controls run-key method chrome]}]
  (let [{:keys [terminal maxdd sharpe vol]} result
        prefix (:data-role-prefix chrome)
        bust-fraction (/ (:bust controls) 100)
        pct0 (fn [v] (fmt/signed-pct v 0))
        pctp0 (fn [v] (fmt/unsigned-pct v 0))
        r2 (fn [v] (fmt/ratio v 2))]
    (if (= method :shuffle)
      ;; Shuffle: only max drawdown genuinely varies; the rest are fixed chips.
      [:div {:class ["mc-dist-grid" "mc-dist-grid-shuffle"]
             :data-role (str prefix "-distributions")}
       (dist-card {:title "Max drawdown"
                   :dist maxdd
                   :fmt-kind :pct
                   :threshold bust-fraction
                   :range-fmt pct0
                   :update-key [:maxdd run-key]
                   :data-role-prefix prefix})
       (realized-strip {:terminal terminal :sharpe sharpe :vol vol
                        :pct0 pct0 :pctp0 pctp0 :r2 r2 :data-role-prefix prefix})]
      [:div {:class ["mc-dist-grid"]
             :data-role (str prefix "-distributions")}
       (dist-card {:title "Total return"
                   :dist terminal
                   :fmt-kind :pct
                   :sign-by-value? true
                   :range-fmt pct0
                   :update-key [:total run-key]
                   :data-role-prefix prefix})
       (dist-card {:title "Max drawdown"
                   :dist maxdd
                   :fmt-kind :pct
                   :threshold bust-fraction
                   :range-fmt pct0
                   :update-key [:maxdd run-key]
                   :data-role-prefix prefix})
       (dist-card {:title "Sharpe ratio"
                   :dist sharpe
                   :sign-by-value? true
                   :range-fmt r2
                   :update-key [:sharpe run-key]
                   :data-role-prefix prefix})
       (dist-card {:title "Annualized vol"
                   :dist vol
                   :fmt-kind :pct
                   :range-fmt pctp0
                   :update-key [:vol run-key]
                   :data-role-prefix prefix})])))
