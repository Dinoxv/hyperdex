(ns hyperopen.views.portfolio.optimize.scenario-detail-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions.common :as action-common]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]
            [hyperopen.views.portfolio-view :as portfolio-view]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [click-actions collect-nodes collect-strings node-by-role]]))

(defn- ready-scenario-state
  [scenario-id return-model]
  {:router {:path (str "/portfolio/optimize/" scenario-id)}
   :portfolio {:optimizer
               {:active-scenario {:loaded-id scenario-id
                                  :name "BTC View"
                                  :status :computed
                                  :read-only? false}
                :draft {:id scenario-id
                        :name "BTC View"
                        :universe [{:instrument-id "perp:BTC"
                                    :market-type :perp
                                    :coin "BTC"}]
                        :objective {:kind :max-sharpe}
                        :return-model return-model
                        :risk-model {:kind :sample-covariance}
                        :constraints {:long-only? true
                                      :max-asset-weight 1.0}
                        :metadata {:dirty? false}}
                :history-data {:candle-history-by-coin
                               {"BTC" [{:time 1000 :close "100"}
                                       {:time 2000 :close "110"}]}
                               :funding-history-by-coin {}}
                :market-cap-by-coin {}
                :runtime {:as-of-ms 2500
                          :stale-after-ms 60000}
                :run-state {:status :succeeded
                            :run-id "run-1"
                            :completed-at-ms 2600}}}
   :webdata2 {:clearinghouseState
              {:marginSummary {:accountValue "1000"}
               :assetPositions []}}})

(defn- request-signature-for-state
  [state]
  (let [{:keys [request runnable?]} (setup-readiness/build-readiness state)]
    (is runnable?)
    (action-common/build-request-signature request)))

(defn- solved-run-for-state
  [state]
  (fixtures/sample-last-successful-run
   {:computed-at-ms 2600
    :request-signature (request-signature-for-state state)
    :result {:status :solved
             :as-of-ms 2600
             :instrument-ids ["perp:BTC"]
             :target-weights [1.0]
             :current-weights [0.0]
             :target-weights-by-instrument {"perp:BTC" 1.0}
             :current-weights-by-instrument {"perp:BTC" 0.0}
             :expected-returns-by-instrument {"perp:BTC" 0.2}
             :expected-return 0.2
             :volatility 1.0
             :performance {:shrunk-sharpe 0.2}
             :history-summary {:return-observations 2 :stale? false}
             :return-model :historical-mean
             :risk-model :sample-covariance
             :diagnostics {:turnover 1.0}
             :rebalance-preview {:status :ready
                                 :capital-usd 1000
                                 :summary {:ready-count 1 :blocked-count 0}
                                 :rows []}}}))

(defn- scenario-kpi-delta-classes
  [current-return target-return current-vol target-vol]
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/scn_tone"}
                    :portfolio {:optimizer
                                {:active-scenario {:loaded-id "scn_tone"
                                                   :name "Tone Check"
                                                   :status :computed}
                                 :draft {:id "scn_tone"
                                         :name "Tone Check"
                                         :universe [{:instrument-id "perp:BTC"
                                                     :market-type :perp
                                                     :coin "BTC"}]
                                         :objective {:kind :max-sharpe}
                                         :return-model {:kind :historical-mean}
                                         :risk-model {:kind :sample-covariance}
                                         :constraints {:max-asset-weight 1.0
                                                       :gross-max 1.0}
                                         :metadata {:dirty? false}}
                                 :last-successful-run
                                 (fixtures/sample-last-successful-run
                                  {:computed-at-ms 1714137600000
                                   :request-signature {:seed 1}
                                   :result {:as-of-ms 1714137600000
                                            :instrument-ids ["perp:BTC"]
                                            :current-weights [1.0]
                                            :target-weights [1.0]
                                            :target-weights-by-instrument {"perp:BTC" 1.0}
                                            :current-weights-by-instrument {"perp:BTC" 1.0}
                                            :current-expected-return current-return
                                            :expected-return target-return
                                            :current-volatility current-vol
                                            :volatility target-vol
                                            :performance {:shrunk-sharpe 0.44}
                                            :history-summary {:return-observations 12 :stale? false}
                                            :return-model :historical-mean
                                            :risk-model :sample-covariance
                                            :diagnostics {:turnover 0.0
                                                          :gross-exposure 1.0
                                                          :net-exposure 1.0}
                                            :rebalance-preview {:status :ready
                                                                :capital-usd 100000
                                                                :summary {:ready-count 1 :blocked-count 0}
                                                                :rows []}}})}}})]
    {:volatility (set (get-in (node-by-role view-node
                                             "portfolio-optimizer-scenario-kpi-volatility")
                              [4 1 :class]))
     :expected-return (set (get-in (node-by-role view-node
                                                 "portfolio-optimizer-scenario-kpi-expected-return")
                                  [4 1 :class]))}))

(deftest portfolio-view-delegates-optimizer-scenario-route-to-detail-surface-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/scn_01"}})
        detail-surface (node-by-role view-node "portfolio-optimizer-scenario-detail-surface")
        strings (set (collect-strings view-node))]
    (is (some? detail-surface))
    (is (= "scn_01" (get-in detail-surface [1 :data-scenario-id])))
    (is (some? (node-by-role view-node "portfolio-optimizer-provenance-strip")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-tabs")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-tab-recommendation")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-tab-rebalance")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-tab-tracking")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-tab-inputs")))
    (is (some? (node-by-role view-node "portfolio-optimizer-recommendation-tab")))
    (is (= [[:actions/set-portfolio-optimizer-results-tab :tracking]]
           (click-actions
            (node-by-role view-node "portfolio-optimizer-scenario-tab-tracking"))))
    (is (nil? (node-by-role view-node "portfolio-optimizer-setup-surface")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-workspace")))
    (is (contains? strings "Scenario scn_01"))
    (is (contains? strings "Recommendation"))
    (is (contains? strings "Rebalance preview"))
    (is (contains? strings "Tracking"))
    (is (contains? strings "Inputs"))))

(deftest portfolio-optimizer-scenario-detail-renders-header-kpis-and-provenance-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/scn_01"}
                    :portfolio {:optimizer
                                {:active-scenario {:loaded-id "scn_01"
                                                   :name "Capital Rotation"
                                                   :status :computed}
                                 :draft {:name "Capital Rotation"
                                         :metadata {:dirty? true}
                                         :universe [{:instrument-id "perp:BTC"
                                                     :market-type :perp
                                                     :coin "BTC"}
                                                    {:instrument-id "perp:ETH"
                                                     :market-type :perp
                                                     :coin "ETH"}]
                                         :objective {:kind :max-sharpe}
                                         :return-model {:kind :historical-mean}
                                         :risk-model {:kind :diagonal-shrink}
                                         :constraints {:max-asset-weight 0.4
                                                       :gross-max 1.5}}
                                 :last-successful-run
                                 (fixtures/sample-last-successful-run
                                  {:computed-at-ms 1714137600000
                                   :request-signature {:seed 1}
                                   :result {:as-of-ms 1714137600000
                                            :instrument-ids ["perp:BTC" "perp:ETH"]
                                            :current-weights [0.1 0.2]
                                            :target-weights [0.35 0.15]
                                            :target-weights-by-instrument {"perp:BTC" 0.35 "perp:ETH" 0.15}
                                            :current-weights-by-instrument {"perp:BTC" 0.1 "perp:ETH" 0.2}
                                            :expected-return 0.14
                                            :volatility 0.32
                                            :performance {:shrunk-sharpe 0.44}
                                            :history-summary {:return-observations 12 :stale? false}
                                            :return-model :historical-mean
                                            :risk-model :diagonal-shrink
                                            :return-decomposition-by-instrument
                                            {"perp:BTC" {:return-component 0.1 :funding-component 0.02}
                                             "perp:ETH" {:return-component 0.08 :funding-component 0.01}}
                                            :diagnostics {:turnover 0.2}
                                            :rebalance-preview {:status :ready
                                                                :capital-usd 100000
                                                                :summary {:ready-count 2 :blocked-count 0}
                                                                :rows []}}})}}})
        strings (set (collect-strings view-node))]
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-header")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-status-tag")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-kpi-strip")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-kpi-expected-return")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-kpi-volatility")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-kpi-sharpe")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-kpi-turnover")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-kpi-rebalance")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-results-surface")))
    (is (some? (node-by-role view-node "portfolio-optimizer-recommendation-stale-blocked")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-rebalance-preview")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-tracking-panel")))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-stale-banner")))
    (is (= [[:actions/run-portfolio-optimizer-from-draft]]
           (click-actions
            (node-by-role view-node "portfolio-optimizer-scenario-rerun-stale"))))
    (is (= true
           (get-in (node-by-role view-node "portfolio-optimizer-scenario-save")
                   [1 :disabled])))
    (is (nil?
         (click-actions
          (node-by-role view-node "portfolio-optimizer-scenario-save"))))
    (is (= [[:actions/run-portfolio-optimizer-from-draft]]
           (click-actions
            (node-by-role view-node "portfolio-optimizer-scenario-rerun"))))
    (is (contains? strings "Capital Rotation"))
    (is (contains? strings "14.00%"))
    (is (contains? strings "32.00%"))
    (is (contains? strings "0.44"))
    (is (contains? strings "20.00%"))
    (is (contains? strings "data as of "))
    (is (contains? strings "gross ≤ 1.5 · cap 40.00%"))
    (is (contains? strings "Draft inputs differ from the last successful run. Rerun before using recommendation or rebalance output."))))

(deftest portfolio-optimizer-scenario-kpi-delta-classes-reflect-metric-semantics-test
  (let [improved-risk (scenario-kpi-delta-classes 0.1 0.14 0.32 0.24)
        worse-risk (scenario-kpi-delta-classes 0.1 0.14 0.24 0.32)
        lower-return (scenario-kpi-delta-classes 0.14 0.1 0.32 0.24)]
    (is (contains? (:volatility improved-risk) "text-trading-green"))
    (is (contains? (:volatility worse-risk) "text-trading-red"))
    (is (contains? (:expected-return improved-risk) "text-trading-green"))
    (is (contains? (:expected-return lower-return) "text-warning"))))

(deftest portfolio-optimizer-scenario-detail-marks-clean-mismatched-result-stale-test
  (let [scenario-id "scn_bl"
        black-litterman-state
        (ready-scenario-state
         scenario-id
         {:kind :black-litterman
          :views [{:kind :absolute
                   :instrument-id "perp:BTC"
                   :return 0.2
                   :confidence 0.75
                   :weights {"perp:BTC" 1}}]})
        historical-state
        (assoc-in black-litterman-state
                  [:portfolio :optimizer :draft :return-model]
                  {:kind :historical-mean})
        view-node (portfolio-view/portfolio-view
                   (assoc-in black-litterman-state
                             [:portfolio :optimizer :last-successful-run]
                             (solved-run-for-state historical-state)))
        save-button (node-by-role view-node "portfolio-optimizer-scenario-save")]
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-stale-banner"))
        "A clean draft is still stale when the retained solved run was produced from different optimizer inputs.")
    (is (= true (get-in save-button [1 :disabled]))
        "Mismatched solved runs must not be saveable as the active scenario.")
    (is (nil?
           (click-actions save-button)))
    (is (some? (node-by-role view-node
                              "portfolio-optimizer-recommendation-stale-blocked"))
        "The recommendation tab should block stale actionable output.")
    (is (nil? (node-by-role view-node "portfolio-optimizer-frontier-panel"))
        "A stale retained result must not render an actionable frontier.")
    (is (nil? (node-by-role view-node "portfolio-optimizer-target-exposure-table"))
        "A stale retained result must not render actionable allocation weights.")
    (is (= [[:actions/run-portfolio-optimizer-from-draft]]
           (click-actions
            (node-by-role view-node
                          "portfolio-optimizer-recommendation-run-again"))))))

(deftest portfolio-optimizer-scenario-detail-keeps-completed-result-after-snapshot-drift-test
  (let [scenario-id "draft"
        state (ready-scenario-state scenario-id {:kind :historical-mean})
        solved-run (solved-run-for-state state)
        view-node (portfolio-view/portfolio-view
                   (-> state
                       (assoc-in [:portfolio :optimizer :run-state :request-signature]
                                 (:request-signature solved-run))
                       (assoc-in [:portfolio :optimizer :last-successful-run]
                                 solved-run)
                       (assoc-in [:webdata2 :clearinghouseState :marginSummary :accountValue]
                                 "2000")))
        save-button (node-by-role view-node "portfolio-optimizer-scenario-save")]
    (is (nil? (node-by-role view-node "portfolio-optimizer-scenario-stale-banner")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-recommendation-stale-blocked")))
    (is (some? (node-by-role view-node "portfolio-optimizer-results-surface")))
    (is (some? (node-by-role view-node "portfolio-optimizer-frontier-panel")))
    (is (= false (get-in save-button [1 :disabled])))))

(deftest portfolio-optimizer-scenario-detail-retains-recommendation-while-recomputing-test
  (let [scenario-id "scn_recompute"
        base-state (ready-scenario-state scenario-id {:kind :historical-mean})
        solved-run (solved-run-for-state base-state)
        state (-> base-state
                  (assoc-in [:portfolio :optimizer :last-successful-run] solved-run)
                  (assoc-in [:portfolio :optimizer :draft :universe]
                            [{:instrument-id "perp:BTC"
                              :market-type :perp
                              :coin "BTC"}
                             {:instrument-id "perp:ETH"
                              :market-type :perp
                              :coin "ETH"}])
                  (assoc-in [:portfolio :optimizer :draft :metadata :dirty?] true)
                  (assoc-in [:portfolio :optimizer :optimization-progress]
                            {:status :running
                             :run-id "run-recompute"
                             :started-at-ms 2600
                             :overall-percent 35
                             :steps [{:id :fetch-returns
                                      :label "Fetch returns"
                                      :status :running
                                      :percent 35}]}))
        view-node (portfolio-view/portfolio-view state)
        strings (set (collect-strings view-node))]
    (is (some? (node-by-role view-node "portfolio-optimizer-results-surface")))
    (is (some? (node-by-role view-node "portfolio-optimizer-recompute-banner")))
    (is (some? (node-by-role view-node "portfolio-optimizer-progress-panel")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-recommendation-stale-blocked")))
    (is (nil? (node-by-role view-node "portfolio-optimizer-scenario-stale-banner")))
    (is (contains? strings "Recomputing recommendation"))
    (is (contains? strings "Keeping the previous allocation visible until the new run finishes."))))

(deftest portfolio-optimizer-scenario-detail-objective-menu-renders-actions-test
  (let [scenario-id "draft"
        base-state (ready-scenario-state scenario-id {:kind :historical-mean})
        solved-run (solved-run-for-state base-state)
        closed-view (portfolio-view/portfolio-view
                     (assoc-in base-state
                               [:portfolio :optimizer :last-successful-run]
                               solved-run))
        open-view (portfolio-view/portfolio-view
                   (-> base-state
                       (assoc-in [:portfolio :optimizer :last-successful-run]
                                 solved-run)
                       (assoc-in [:portfolio-ui :optimizer :objective-menu-open?]
                                 true)
                       (assoc-in [:portfolio-ui :optimizer :objective-menu-selection]
                                 :max-sharpe)))
        changed-view (portfolio-view/portfolio-view
                      (-> base-state
                          (assoc-in [:portfolio :optimizer :last-successful-run]
                                    solved-run)
                          (assoc-in [:portfolio-ui :optimizer :objective-menu-open?]
                                    true)
                          (assoc-in [:portfolio-ui :optimizer :objective-menu-selection]
                                    :minimum-volatility)))
        use-my-views-view (portfolio-view/portfolio-view
                           (-> base-state
                               (assoc-in [:portfolio :optimizer :last-successful-run]
                                         solved-run)
                               (assoc-in [:portfolio-ui :optimizer :objective-menu-open?]
                                         true)
                               (assoc-in [:portfolio-ui :optimizer :objective-menu-selection]
                                         :use-my-views)
                               (assoc-in [:portfolio-ui :optimizer :objective-menu-view-drafts]
                                         {:perp:BTC {:return-text "18"
                                                     :confidence :medium}})))
        use-my-views-prefilled-view (portfolio-view/portfolio-view
                                     (-> base-state
                                         (assoc-in [:portfolio :optimizer :last-successful-run]
                                                   solved-run)
                                         (assoc-in [:portfolio-ui :optimizer :objective-menu-open?]
                                                   true)
                                         (assoc-in [:portfolio-ui :optimizer :objective-menu-selection]
                                                   :use-my-views)))
        trigger (node-by-role closed-view
                              "portfolio-optimizer-objective-menu-trigger")
        open-trigger (node-by-role open-view
                                   "portfolio-optimizer-objective-menu-trigger")
        menu (node-by-role open-view "portfolio-optimizer-objective-menu")
        apply-current (node-by-role open-view
                                    "portfolio-optimizer-objective-menu-apply")
        apply-changed (node-by-role changed-view
                                    "portfolio-optimizer-objective-menu-apply")
        minimum-row (node-by-role open-view
                                  "portfolio-optimizer-objective-menu-option-minimum-volatility")
        use-my-views-row (node-by-role open-view
                                       "portfolio-optimizer-objective-menu-option-use-my-views")
        inline-editor (node-by-role use-my-views-view
                                    "portfolio-optimizer-objective-menu-use-my-views-editor")
        btc-row (node-by-role use-my-views-view
                              "portfolio-optimizer-objective-menu-view-row-perp:BTC")
        btc-icon (node-by-role use-my-views-view
                               "portfolio-optimizer-objective-menu-view-perp:BTC-icon-img")
        btc-return (node-by-role use-my-views-view
                                 "portfolio-optimizer-objective-menu-view-perp:BTC-return")
        btc-step-up (node-by-role use-my-views-view
                                  "portfolio-optimizer-objective-menu-view-perp:BTC-step-up")
        btc-step-down (node-by-role use-my-views-view
                                    "portfolio-optimizer-objective-menu-view-perp:BTC-step-down")
        btc-prefilled-return (node-by-role use-my-views-prefilled-view
                                           "portfolio-optimizer-objective-menu-view-perp:BTC-return")
        btc-return-suffix (first (filter (fn [node]
                                           (contains? (set (get-in node [1 :class]))
                                                      "optimizer-objective-view-return-suffix"))
                                         (collect-nodes btc-row vector?)))
        btc-confidence-medium (node-by-role use-my-views-view
                                            "portfolio-optimizer-objective-menu-view-perp:BTC-confidence-medium")
        btc-remove (node-by-role use-my-views-view
                                 "portfolio-optimizer-objective-menu-view-perp:BTC-remove")
        strings (set (collect-strings open-view))]
    (is (some? trigger))
    (is (= [[:actions/open-portfolio-optimizer-objective-menu]]
           (click-actions trigger)))
    (is (= "true" (get-in trigger [1 :aria-haspopup])))
    (is (= "false" (get-in trigger [1 :aria-expanded])))
    (is (= "true" (get-in open-trigger [1 :aria-expanded])))
    (is (nil? (node-by-role closed-view "portfolio-optimizer-objective-menu")))
    (is (nil? (node-by-role open-view "portfolio-optimizer-objective-menu-backdrop")))
    (is (some? menu))
    (is (contains? (set (get-in menu [1 :class]))
                   "optimizer-objective-menu"))
    (is (contains? (set (get-in menu [1 :class]))
                   "optimizer-objective-popover"))
    (is (= "region" (get-in menu [1 :role])))
    (is (nil? (get-in menu [1 :aria-modal])))
    (is (= [[:actions/handle-portfolio-optimizer-objective-menu-keydown
             [:event/key]]]
           (get-in menu [1 :on :keydown])))
    (is (contains? strings "Change objective"))
    (is (contains? strings "Re-runs the solver with the same universe and constraints"))
    (is (contains? strings "Minimum volatility"))
    (is (contains? strings "Maximum Sharpe"))
    (is (contains? strings "Target volatility · 12%"))
    (is (contains? strings "Maximum return"))
    (is (contains? strings "Use my views"))
    (is (= [[:actions/select-portfolio-optimizer-objective-menu-option
             :minimum-volatility]]
           (click-actions minimum-row)))
    (is (= [[:actions/select-portfolio-optimizer-objective-menu-option
             :use-my-views]]
           (click-actions use-my-views-row)))
    (is (some? inline-editor))
    (is (contains? (set (collect-strings inline-editor)) "Your return views"))
    (is (= "https://app.hyperliquid.xyz/coins/BTC.svg"
           (get-in btc-icon [1 :src])))
    (is (= "18" (get-in btc-return [1 :value])))
    (is (= "20" (get-in btc-prefilled-return [1 :value])))
    (is (contains? (set (get-in btc-return [1 :class])) "pr-9"))
    (is (= ["%"] (collect-strings btc-return-suffix)))
    (is (= [[:actions/set-portfolio-optimizer-objective-menu-view-return
             "perp:BTC"
             [:event.target/value]]]
           (get-in btc-return [1 :on :input])))
    (is (= [[:actions/step-portfolio-optimizer-objective-menu-view-return
             "perp:BTC"
             [:event/key]]]
           (get-in btc-return [1 :on :keydown])))
    (is (= "Increase BTC return" (get-in btc-step-up [1 :aria-label])))
    (is (= "Decrease BTC return" (get-in btc-step-down [1 :aria-label])))
    (is (= [[:actions/step-portfolio-optimizer-objective-menu-view-return
             "perp:BTC"
             :up]]
           (click-actions btc-step-up)))
    (is (= [[:actions/step-portfolio-optimizer-objective-menu-view-return
             "perp:BTC"
             :down]]
           (click-actions btc-step-down)))
    (is (= "true" (get-in btc-confidence-medium [1 :data-selected])))
    (is (= "medium" (get-in btc-confidence-medium [1 :data-tooltip])))
    (is (= "medium" (get-in btc-confidence-medium [1 :title])))
    (is (= "Set medium confidence" (get-in btc-confidence-medium [1 :aria-label])))
    (is (= ["M"] (collect-strings btc-confidence-medium)))
    (is (= [[:actions/set-portfolio-optimizer-objective-menu-view-confidence
             "perp:BTC"
             :medium]]
           (click-actions btc-confidence-medium)))
    (is (= [[:actions/remove-portfolio-optimizer-objective-menu-view
             "perp:BTC"]]
           (click-actions btc-remove)))
    (is (= [[:actions/add-portfolio-optimizer-objective-menu-view]]
           (click-actions
            (node-by-role use-my-views-view
                          "portfolio-optimizer-objective-menu-add-view"))))
    (is (= true (get-in apply-current [1 :disabled])))
    (is (nil? (click-actions apply-current)))
    (is (= false (get-in apply-changed [1 :disabled])))
    (is (= [[:actions/apply-portfolio-optimizer-objective-menu-selection-and-run]]
           (click-actions apply-changed)))
    (is (= [[:actions/close-portfolio-optimizer-objective-menu]]
           (click-actions
            (node-by-role open-view "portfolio-optimizer-objective-menu-cancel"))))
    (is (= [[:actions/close-portfolio-optimizer-objective-menu]]
           (click-actions
            (node-by-role open-view "portfolio-optimizer-objective-menu-close"))))))

(deftest portfolio-optimizer-inputs-tab-renders-read-only-audit-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/scn_inputs"}
                    :portfolio-ui {:optimizer {:results-tab :inputs}}
                    :portfolio {:optimizer
                                {:active-scenario {:loaded-id "scn_inputs"
                                                   :status :saved}
                                 :draft {:id "scn_inputs"
                                         :universe [{:instrument-id "perp:BTC"
                                                     :market-type :perp
                                                     :coin "BTC"}]
                                         :objective {:kind :target-volatility}
                                         :return-model {:kind :black-litterman
                                                        :views [{:id "view-1"}]}
                                         :risk-model {:kind :diagonal-shrink}
                                         :constraints {:long-only? true
                                                       :max-asset-weight 0.4
                                                       :gross-max 1.2
                                                       :max-turnover 0.5
                                                       :rebalance-tolerance 0.01
                                                       :dust-usdc 15}
                                         :execution-assumptions {:manual-capital-usdc 25000
                                                                 :fallback-slippage-bps 35
                                                                 :default-order-type :market
                                                                 :fee-mode :taker}}}}})
        strings (set (collect-strings view-node))]
    (is (some? (node-by-role view-node "portfolio-optimizer-inputs-tab")))
    (is (some? (node-by-role view-node "portfolio-optimizer-inputs-audit-grid")))
    (is (some? (node-by-role view-node "portfolio-optimizer-inputs-universe")))
    (is (some? (node-by-role view-node "portfolio-optimizer-inputs-models")))
    (is (some? (node-by-role view-node "portfolio-optimizer-inputs-constraints")))
    (is (some? (node-by-role view-node "portfolio-optimizer-inputs-execution-assumptions")))
    (is (= [[:actions/duplicate-portfolio-optimizer-scenario "scn_inputs"]]
           (click-actions
            (node-by-role view-node "portfolio-optimizer-inputs-duplicate"))))
    (is (contains? strings "Read-only scenario input audit. Duplicate the scenario before editing inputs."))
    (is (contains? strings "perp:BTC"))
    (is (contains? strings "Black-Litterman views: 1"))
    (is (contains? strings "Manual capital: $25,000"))))

(deftest portfolio-optimizer-scenario-detail-does-not-render-stale-loaded-scenario-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/scn_new"}
                    :portfolio {:optimizer
                                {:active-scenario {:loaded-id "scn_old"
                                                   :status :executed}
                                 :draft {:name "Old scenario"
                                         :universe [{:instrument-id "perp:OLD"
                                                     :market-type :perp
                                                     :coin "OLD"}]}
                                 :last-successful-run (fixtures/sample-last-successful-run
                                                       {:result {:instrument-ids ["perp:OLD"]}})
                                 :tracking {:scenario-id "scn_old"
                                            :snapshots
                                            [{:scenario-id "scn_old"
                                              :rows [{:instrument-id "perp:OLD"
                                                      :current-weight 1
                                                      :target-weight 1
                                                      :weight-drift 0
                                                      :signed-notional-usdc 1000}]}]}}}})
        detail-surface (node-by-role view-node "portfolio-optimizer-scenario-detail-surface")
        strings (set (collect-strings view-node))]
    (is (= "scn_new" (get-in detail-surface [1 :data-scenario-id])))
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-loading-state")))
    (is (contains? strings "Scenario scn_new"))
    (is (not (contains? strings "Old scenario")))
    (is (not (contains? strings "perp:OLD")))))

(deftest portfolio-optimizer-scenario-detail-hides-unsaved-run-while-route-load-pending-test
  (let [view-node (portfolio-view/portfolio-view
                   {:router {:path "/portfolio/optimize/scn_loading"}
                    :portfolio {:optimizer
                                {:active-scenario {:loaded-id nil
                                                   :status :idle}
                                 :scenario-load-state {:status :loading
                                                       :scenario-id "scn_loading"}
                                 :draft {:name "Unsaved draft"
                                         :universe [{:instrument-id "perp:UNSAVED"
                                                     :market-type :perp
                                                     :coin "UNSAVED"}]}
                                 :last-successful-run (fixtures/sample-last-successful-run
                                                       {:result {:instrument-ids ["perp:UNSAVED"]}})}}})
        strings (set (collect-strings view-node))]
    (is (some? (node-by-role view-node "portfolio-optimizer-scenario-loading-state")))
    (is (contains? strings "Scenario scn_loading"))
    (is (not (contains? strings "Unsaved draft")))
    (is (not (contains? strings "perp:UNSAVED")))))
