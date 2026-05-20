(ns hyperopen.views.portfolio.optimize.results-panel-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.portfolio.optimize.results-panel :as results-panel]
            [hyperopen.views.portfolio.optimize.test-support
             :refer [click-actions collect-strings node-attr node-by-role solved-result]]))

(deftest results-panel-renders-canonical-results-workspace-shell-test
  (let [draft {:objective {:kind :target-volatility}
               :metadata {:dirty? true}}
        view-node (results-panel/results-panel
                   {:result solved-result
                    :computed-at-ms 2600}
                   draft
                   {:stale? true
                    :frontier-overlay-mode :standalone})
        btc-icon (node-by-role view-node
                               "portfolio-optimizer-target-exposure-asset-icon-img-BTC")
        purr-icon (node-by-role view-node
                                "portfolio-optimizer-target-exposure-asset-icon-img-PURR")
        strings (set (collect-strings view-node))]
    (is (some? (node-by-role view-node "portfolio-optimizer-results-surface")))
    (is (some? (node-by-role view-node "portfolio-optimizer-stale-result-banner")))
    (is (= [[:actions/run-portfolio-optimizer-from-draft]]
           (click-actions
            (node-by-role view-node "portfolio-optimizer-rerun-stale-result"))))
    (is (some? (node-by-role view-node "portfolio-optimizer-results-grid")))
    (is (some? (node-by-role view-node "portfolio-optimizer-results-left-panel")))
    (is (some? (node-by-role view-node "portfolio-optimizer-results-center-panel")))
    (is (some? (node-by-role view-node "portfolio-optimizer-results-right-panel")))
    (is (some? (node-by-role view-node "portfolio-optimizer-trust-caution-panel")))
    (is (some? (node-by-role view-node "portfolio-optimizer-frontier-panel")))
    (is (some? (node-by-role view-node "portfolio-optimizer-frontier-svg")))
    (is (some? (node-by-role view-node "portfolio-optimizer-frontier-path")))
    (is (some? (node-by-role view-node "portfolio-optimizer-target-exposure-table")))
    (is (= "https://app.hyperliquid.xyz/coins/BTC.svg"
           (node-attr btc-icon :src)))
    (is (= "https://app.hyperliquid.xyz/coins/PURR_spot.svg"
           (node-attr purr-icon :src)))
    (is (some? (node-by-role view-node "portfolio-optimizer-result-warnings")))
    (is (some? (node-by-role view-node "portfolio-optimizer-diagnostics-panel")))
    (is (some? (node-by-role view-node "portfolio-optimizer-rebalance-preview")))
    (is (contains? strings "Allocation"))
    (is (contains? strings "How much to trust this"))
    (is (contains? strings "low-invested-exposure"))
    (is (contains? strings "partially-blocked"))))

(deftest results-panel-allocation-add-asset-selector-renders-closed-and-open-states-test
  (let [draft {:universe [{:instrument-id "perp:BTC"
                           :market-type :perp
                           :coin "BTC"}
                          {:instrument-id "spot:PURR"
                           :market-type :spot
                           :coin "PURR"
                           :symbol "PURR/USDC"
                           :base "PURR"
                           :quote "USDC"}]
               :objective {:kind :minimum-variance}}
        base-state {:portfolio {:optimizer {:draft draft}}
                    :asset-selector
                    {:markets [{:key "perp:BTC"
                                :market-type :perp
                                :coin "BTC"
                                :symbol "BTC-USDC"}
                               {:key "perp:ETH"
                                :market-type :perp
                                :coin "ETH"
                                :symbol "ETH-USDC"
                                :base "ETH"
                                :quote "USDC"
                                :volume24h 84000000}
                               {:key "perp:TIA"
                                :market-type :perp
                                :coin "TIA"
                                :symbol "TIA-USDC"
                                :base "TIA"
                                :quote "USDC"
                                :volume24h 12000000}]}}
        closed-view (results-panel/results-panel
                     {:result solved-result
                      :computed-at-ms 2600}
                     draft
                     {:state base-state
                      :frontier-overlay-mode :standalone})
        open-state (-> base-state
                       (assoc-in [:portfolio-ui :optimizer :draft-add-asset-open?]
                                 true)
                       (assoc-in [:portfolio-ui :optimizer :universe-search-query]
                                 "eth"))
        open-view (results-panel/results-panel
                   {:result solved-result
                    :computed-at-ms 2600}
                   draft
                   {:state open-state
                    :frontier-overlay-mode :standalone})
        add-button (node-by-role closed-view "portfolio-optimizer-draft-add-asset")
        popover (node-by-role open-view "portfolio-optimizer-draft-add-asset-popover")
        search-input (node-by-role open-view
                                   "portfolio-optimizer-draft-add-asset-search-input")
        search-clear (node-by-role open-view
                                   "portfolio-optimizer-draft-add-asset-search-clear")
        search-hint (node-by-role open-view
                                  "portfolio-optimizer-draft-add-asset-search-add-hint")
        search-results (node-by-role open-view
                                     "portfolio-optimizer-draft-add-asset-search-results")
        eth-row (node-by-role open-view
                              "portfolio-optimizer-draft-add-asset-candidate-row-perp:ETH")
        eth-add (node-by-role open-view
                              "portfolio-optimizer-draft-add-asset-add-perp:ETH")
        strings (set (collect-strings open-view))]
    (is (some? add-button))
    (is (= [[:actions/set-portfolio-optimizer-draft-add-asset-open true]]
           (click-actions add-button)))
    (is (nil? (node-by-role closed-view
                            "portfolio-optimizer-draft-add-asset-popover")))
    (is (some? popover))
    (is (contains? (set (node-attr popover :class)) "fixed"))
    (is (contains? (set (node-attr popover :class)) "md:absolute"))
    (is (contains? (set (node-attr popover :class)) "overflow-hidden"))
    (is (not (contains? (set (node-attr popover :class)) "overflow-auto")))
    (is (contains? (set (node-attr popover :class)) "optimizer-draft-add-asset-popover--dark"))
    (is (some? search-input))
    (is (= "text" (node-attr search-input :type)))
    (is (not (contains? (set (node-attr search-input :class))
                        "focus:shadow-[0_0_0_1px_rgba(212,181,88,0.75)]")))
    (is (fn? (node-attr search-input :replicant/on-render)))
    (is (= [[:actions/set-portfolio-optimizer-universe-search-query
             [:event.target/value]]]
           (get-in search-input [1 :on :input])))
    (is (= [[:actions/handle-portfolio-optimizer-draft-add-asset-keydown
             [:event/key]
             ["perp:ETH"]]]
           (get-in search-input [1 :on :keydown])))
    (is (nil? search-clear))
    (is (nil? search-hint))
    (is (contains? (set (node-attr search-results :class))
                   "optimizer-draft-add-asset-results"))
    (is (some? eth-row))
    (is (= [[:actions/add-portfolio-optimizer-universe-instrument-and-run "perp:ETH"]]
           (click-actions eth-row)))
    (is (= [[:actions/add-portfolio-optimizer-universe-instrument-and-run "perp:ETH"]]
           (click-actions eth-add)))
    (is (contains? strings "Search a tradable asset"))
    (is (contains? strings "ETH-USDC"))
    (is (not (some? (node-by-role open-view
                                  "portfolio-optimizer-draft-add-asset-candidate-row-perp:BTC"))))))

(deftest results-panel-allocation-renders-excluded-draft-row-toggle-test
  (let [draft {:universe [{:instrument-id "perp:BTC"
                           :market-type :perp
                           :coin "BTC"}
                          {:instrument-id "spot:PURR"
                           :market-type :spot
                           :coin "PURR"}]
               :constraints {:blocklist ["spot:PURR"]}
               :objective {:kind :minimum-variance}}
        view-node (results-panel/results-panel
                   {:result solved-result
                    :computed-at-ms 2600}
                   draft
                   {:state {:portfolio {:optimizer {:draft draft}}}
                    :frontier-overlay-mode :standalone})
        purr-row (node-by-role view-node
                               "portfolio-optimizer-target-exposure-asset-PURR")
        purr-toggle (node-by-role view-node
                                  "portfolio-optimizer-target-exposure-exclude-spot-PURR")
        strings (set (collect-strings purr-row))]
    (is (some? purr-row))
    (is (= "true" (node-attr purr-row :data-excluded)))
    (is (contains? strings "excluded"))
    (is (contains? strings "sell to 0"))
    (is (contains? strings "0.00%"))
    (is (some? purr-toggle))
    (is (= "Include PURR and rerun" (node-attr purr-toggle :aria-label)))
    (is (= [[:actions/toggle-portfolio-optimizer-universe-instrument-exclusion-and-run
             "spot:PURR"]]
           (click-actions purr-toggle)))))

(deftest results-panel-renders-constrain-frontier-checkbox-above-chart-test
  (let [draft {:objective {:kind :minimum-variance}}
        result (assoc solved-result
                      :frontiers
                      {:unconstrained [{:id 0
                                        :expected-return 0.08
                                        :volatility 0.16
                                        :sharpe 0.5}
                                       {:id 1
                                        :expected-return 0.18
                                        :volatility 0.42
                                        :sharpe 0.43}
                                       {:id 2
                                        :expected-return 0.3
                                        :volatility 0.8
                                        :sharpe 0.375}]
                       :constrained (:frontier solved-result)}
                      :frontier-summaries
                      {:unconstrained {:source :display-sweep
                                       :constraint-mode :unconstrained
                                       :point-count 3}
                       :constrained {:source :display-sweep
                                     :constraint-mode :constrained
                                     :point-count 2}})
        default-view (results-panel/results-panel
                      {:result result
                       :computed-at-ms 2600}
                      draft
                      {:frontier-overlay-mode :standalone})
        constrained-view (results-panel/results-panel
                          {:result result
                           :computed-at-ms 2600}
                          draft
                          {:frontier-overlay-mode :standalone
                           :constrain-frontier? true})
        checkbox (node-by-role default-view
                               "portfolio-optimizer-constrain-frontier-checkbox")
        constrained-checkbox (node-by-role
                              constrained-view
                              "portfolio-optimizer-constrain-frontier-checkbox")]
    (is (some? checkbox))
    (is (= false (node-attr checkbox :checked)))
    (is (= true (node-attr constrained-checkbox :checked)))
    (is (= [[:actions/set-portfolio-optimizer-constrain-frontier
             :event.target/checked]]
           (get-in checkbox [1 :on :change])))
    (is (some #{"Constrain Frontier"} (collect-strings default-view)))
    (is (some #{"3 points"} (collect-strings default-view)))
    (is (some #{"2 points"} (collect-strings constrained-view)))))
