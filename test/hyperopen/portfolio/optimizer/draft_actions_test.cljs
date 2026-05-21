(ns hyperopen.portfolio.optimizer.draft-actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.actions :as actions]))

(deftest apply-portfolio-optimizer-setup-preset-updates-only-model-layer-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :target-return
                                                           :target-return 0.2}
                                               :return-model {:kind :ew-mean}
                                               :risk-model {:kind :sample-covariance}
                                               :constraints {:max-asset-weight 0.4
                                                             :gross-max 2.0}}}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective] {:kind :minimum-variance}]
              [[:portfolio :optimizer :draft :return-model] {:kind :historical-mean}]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]]
           (actions/apply-portfolio-optimizer-setup-preset state :conservative)))
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective] {:kind :max-sharpe}]
              [[:portfolio :optimizer :draft :return-model] {:kind :historical-mean}]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]]
           (actions/apply-portfolio-optimizer-setup-preset state :risk-adjusted)))
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective] {:kind :max-sharpe}]
              [[:portfolio :optimizer :draft :return-model] {:kind :black-litterman
                                                             :views []}]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]]
           (actions/apply-portfolio-optimizer-setup-preset state :use-my-views)))
    (is (= []
           (actions/apply-portfolio-optimizer-setup-preset state :unknown)))))

(deftest set-draft-model-layer-actions-update-draft-and-mark-dirty-test
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective]
                                {:kind :max-sharpe}]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-kind
          {}
          "maxSharpe")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :return-model]
                                {:kind :black-litterman
                                 :views []}]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-return-model-kind
          {}
          :black-litterman)))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :return-model]
                                {:kind :ew-mean
                                 :alpha 0.015159678336035098}]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-return-model-kind
          {}
          :ew-mean)))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :risk-model]
                                {:kind :sample-covariance}]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-risk-model-kind
          {}
          "sampleCovariance")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :risk-model]
                                {:kind :mixed-frequency}]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-risk-model-kind
          {}
          :mixed-frequency))))

(deftest set-draft-model-layer-actions-ignore-invalid-kinds-test
  (is (= []
         (actions/set-portfolio-optimizer-objective-kind {} "not-real")))
  (is (= []
         (actions/set-portfolio-optimizer-return-model-kind {} "not-real")))
  (is (= []
         (actions/set-portfolio-optimizer-risk-model-kind {} "not-real"))))

(deftest objective-menu-actions-select-apply-and-rerun-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :max-sharpe}
                                               :return-model {:kind :historical-mean}
                                               :metadata {:dirty? false}}}}
               :portfolio-ui {:optimizer {:objective-menu-selection :minimum-volatility}}}]
    (is (= [[:effects/save-many
             [[[:portfolio-ui :optimizer :objective-menu-open?] true]
              [[:portfolio-ui :optimizer :objective-menu-selection] :max-sharpe]]]]
           (actions/open-portfolio-optimizer-objective-menu state)))
    (is (= [[:effects/save
             [:portfolio-ui :optimizer :objective-menu-selection]
             :target-volatility]]
           (actions/select-portfolio-optimizer-objective-menu-option
            state
            "targetVolatility")))
    (is (= [[:effects/save-many
             [[[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]]]]
           (actions/close-portfolio-optimizer-objective-menu state)))
    (is (= [[:effects/save-many
             [[[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]]]]
           (actions/handle-portfolio-optimizer-objective-menu-keydown
            state
            "Escape")))
    (is (= []
           (actions/handle-portfolio-optimizer-objective-menu-keydown
            state
            "Enter")))
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective]
               {:kind :minimum-variance}]
              [[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/apply-portfolio-optimizer-objective-menu-selection-and-run
            state)))))

(deftest objective-menu-apply-use-my-views-updates-return-model-and-reruns-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:BTC"}]
                                               :objective {:kind :minimum-variance}
                                               :return-model {:kind :black-litterman
                                                              :views [{:kind :absolute
                                                                       :instrument-id "perp:BTC"
                                                                       :return 0.2
                                                                       :confidence 0.75
                                                                       :weights {"perp:BTC" 1}}]}}}}
               :portfolio-ui {:optimizer {:objective-menu-selection :use-my-views}}}]
    (is (= [[:effects/save-many
             [[[:portfolio :optimizer :draft :objective]
               {:kind :max-sharpe}]
              [[:portfolio :optimizer :draft :return-model]
               {:kind :black-litterman
                :views [{:kind :absolute
                         :instrument-id "perp:BTC"
                         :return 0.2
                         :confidence 0.75
                         :weights {"perp:BTC" 1}}]}]
              [[:portfolio-ui :optimizer :objective-menu-open?] false]
              [[:portfolio-ui :optimizer :objective-menu-selection] nil]
              [[:portfolio :optimizer :draft :metadata :dirty?] true]]]
            [:effects/run-portfolio-optimizer-pipeline]]
           (actions/apply-portfolio-optimizer-objective-menu-selection-and-run
            state)))))

(deftest set-draft-constraint-normalizes-supported-values-test
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :max-asset-weight]
                                0.42]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-constraint
          {}
          :max-asset-weight
          "0.42")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :long-only?]
                                true]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-constraint
          {}
          :long-only?
          true)))
  (is (= []
         (actions/set-portfolio-optimizer-constraint
          {}
          :gross-max
          "not-a-number"))))

(deftest set-draft-objective-parameter-updates-supported-targets-test
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective :target-return]
                                0.18]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-parameter
          {}
          :target-return
          "0.18")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :objective :target-volatility]
                                0.22]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-objective-parameter
          {}
          "targetVolatility"
          "0.22")))
  (is (= []
         (actions/set-portfolio-optimizer-objective-parameter
          {}
          :unknown
          "0.1"))))

(deftest set-draft-execution-assumption-normalizes-supported-values-test
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :execution-assumptions :fallback-slippage-bps]
                                35]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :fallback-slippage-bps
          "35")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :execution-assumptions :manual-capital-usdc]
                                100000]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :manual-capital-usdc
          "100000")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :execution-assumptions :manual-capital-usdc]
                                nil]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :manual-capital-usdc
          "")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :execution-assumptions :default-order-type]
                                :market]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :default-order-type
          "market")))
  (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :execution-assumptions :fee-mode]
                                :taker]
                               [[:portfolio :optimizer :draft :metadata :dirty?]
                                true]]]]
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :fee-mode
          :taker)))
  (is (= []
         (actions/set-portfolio-optimizer-execution-assumption
          {}
          :fallback-slippage-bps
          "not-a-number"))))

(deftest set-draft-instrument-filter-updates-allowlist-and-blocklist-test
  (let [state {:portfolio {:optimizer {:draft {:constraints {:allowlist ["perp:BTC"]
                                                             :blocklist ["spot:PURR"]}}}}}]
    (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :allowlist]
                                  ["perp:BTC" "perp:ETH"]]
                                 [[:portfolio :optimizer :draft :metadata :dirty?]
                                  true]]]]
           (actions/set-portfolio-optimizer-instrument-filter
            state
            :allowlist
            "perp:ETH"
            true)))
    (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :blocklist]
                                  []]
                                 [[:portfolio :optimizer :draft :metadata :dirty?]
                                  true]]]]
           (actions/set-portfolio-optimizer-instrument-filter
            state
            :blocklist
            "spot:PURR"
            false)))
    (is (= []
           (actions/set-portfolio-optimizer-instrument-filter
            state
            :unknown
            "perp:BTC"
            true)))))

(deftest set-draft-asset-override-updates-row-level-constraints-test
  (let [state {:portfolio {:optimizer {:draft {:universe [{:instrument-id "perp:ETH"
                                                            :market-type :perp}
                                                           {:instrument-id "spot:PURR"
                                                            :market-type :spot}]
                                           :constraints {:held-locks ["perp:BTC"]}}}}}]
    (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :asset-overrides "perp:ETH" :max-weight]
                                  0.28]
                                 [[:portfolio :optimizer :draft :metadata :dirty?]
                                  true]]]]
           (actions/set-portfolio-optimizer-asset-override
            state
            :max-weight
            "perp:ETH"
            "0.28")))
    (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :held-locks]
                                  ["perp:BTC" "perp:ETH"]]
                                 [[:portfolio :optimizer :draft :metadata :dirty?]
                                  true]]]]
           (actions/set-portfolio-optimizer-asset-override
            state
            :held-lock?
            "perp:ETH"
            true)))
    (is (= [[:effects/save-many [[[:portfolio :optimizer :draft :constraints :perp-leverage "perp:ETH" :max-weight]
                                  0.5]
                                 [[:portfolio :optimizer :draft :metadata :dirty?]
                                  true]]]]
           (actions/set-portfolio-optimizer-asset-override
            state
            :perp-max-weight
            "perp:ETH"
            "0.5")))
    (is (= []
           (actions/set-portfolio-optimizer-asset-override
            state
            :max-weight
            "perp:ETH"
            "not-a-number")))
    (is (= []
           (actions/set-portfolio-optimizer-asset-override
            state
            :perp-max-weight
            "spot:PURR"
            "0.5")))))
