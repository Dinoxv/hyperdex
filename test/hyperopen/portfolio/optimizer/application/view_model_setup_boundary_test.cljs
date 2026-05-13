(ns hyperopen.portfolio.optimizer.application.view-model-setup-boundary-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.universe-candidates :as universe-candidates]
            [hyperopen.portfolio.optimizer.application.view-model :as view-model]))

(def ^:private btc-instrument
  {:instrument-id "perp:BTC"
   :market-type :perp
   :coin "BTC"
   :symbol "BTC-USDC"
   :name "Bitcoin"})

(def ^:private eth-instrument
  {:instrument-id "perp:ETH"
   :market-type :perp
   :coin "ETH"
   :symbol "ETH-USDC"
   :name "Ethereum"})

(deftest universe-section-model-projects-render-ready-row-labels-test
  (let [calls (atom [])
        selected-instrument (assoc btc-instrument
                                   :volume24h 99000000)
        candidate {:key "spot:@1"
                   :instrument-id "spot:@1"
                   :market-type :spot
                   :coin "@1"
                   :symbol "PURR/USDC"
                   :base "PURR"
                   :name "Purr"
                   :volume24h 125000000
                   :liquidity-label "thin"}
        candidate-markets-stub (fn
                                 ([_state _universe query]
                                  (swap! calls conj query)
                                  [candidate])
                                 ([_state _universe query _opts]
                                  (swap! calls conj query)
                                  [candidate]))
        model (with-redefs [universe-candidates/candidate-markets
                            candidate-markets-stub]
                (view-model/universe-section-model
                 {:portfolio-ui {:optimizer {:universe-search-query "purr"}}}
                 {:universe [selected-instrument]}
                 {:readiness {:request {:universe [selected-instrument]}}
                  :history-load-state {:status :idle}
                  :history-status-by-id {"perp:BTC" :aligned}}))]
    (is (= ["purr"] @calls))
    (is (= [{:instrument-id "perp:BTC"
             :market-type :perp
             :primary-label "BTC"
             :secondary-label "Bitcoin"
             :history-label "sufficient"
             :history-tone :long
             :liquidity-label "deep"}]
           (mapv #(select-keys % [:instrument-id
                                  :market-type
                                  :primary-label
                                  :secondary-label
                                  :history-label
                                  :history-tone
                                  :liquidity-label])
                 (:selected-rows model))))
    (is (= [{:market-key "spot:@1"
             :market-type :spot
             :active? true
             :label "PURR/USDC"
             :name "Purr"
             :adv-label "$125M"}]
           (mapv #(select-keys % [:market-key
                                  :market-type
                                  :active?
                                  :label
                                  :name
                                  :adv-label])
                 (:candidate-rows model))))))

(deftest readiness-panel-model-projects-copy-and-warning-rows-test
  (let [readiness {:status :blocked
                   :reason :incomplete-history
                   :request {:requested-universe [btc-instrument]
                             :universe []
                             :warnings []}
                   :blocking-warnings [{:code :missing-candle-history
                                        :instrument-id "perp:BTC"
                                        :coin "BTC"}]}
        failed-load {:status :failed
                     :error {:message "history endpoint unavailable"}}
        failed-model (view-model/readiness-panel-model readiness failed-load)
        missing-model (view-model/readiness-panel-model
                       {:status :blocked
                        :reason :missing-universe
                        :warnings []}
                       {:status :idle})]
    (is (= "History load failed. Existing history, if any, is retained."
           (:copy failed-model)))
    (is (= "history endpoint unavailable" (:error-message failed-model)))
    (is (= [{:message "Bitcoin: no candle history returned for BTC."
             :code-label "missing-candle-history"}]
           (:warnings failed-model)))
    (is (= "Select a universe before running."
           (:copy missing-model)))))

(deftest setup-summary-model-projects-summary-row-data-test
  (let [formatters {:labelize {:risk-adjusted "Risk Adjusted"
                               :historical-mean "Historical Mean"
                               :max-sharpe "Max Sharpe"
                               :conservative "Conservative"}
                    :percent-label (fn [value] (str (* 100 value) "%"))}
        draft {:universe [btc-instrument eth-instrument]
               :objective {:kind :max-sharpe}
               :return-model {:kind :historical-mean}
               :constraints {:gross-max 1.5
                             :max-asset-weight 0.4}}
        model (view-model/setup-summary-model draft formatters)
        row-by-label (into {}
                           (map (juxt :label identity))
                           (:summary-rows model))]
    (is (= :risk-adjusted (:active-preset model)))
    (is (false? (:black-litterman? model)))
    (is (= "Risk Adjusted" (get-in row-by-label ["Preset" :title])))
    (is (= "Historical Mean" (get-in row-by-label ["Expected Returns" :title])))
    (is (= "Max Sharpe" (get-in row-by-label ["Objective" :title])))
    (is (= "gross <= 1.5 - cap <= 40%" (get-in row-by-label ["Constraints" :title]))))
  (is (true? (:black-litterman?
              (view-model/setup-summary-model
               {:return-model {:kind :black-litterman}}
               {:labelize name
                :percent-label str})))))

(deftest black-litterman-cards-model-projects-preview-card-data-test
  (let [formatters {:pct (fn [value] (str "pct:" value))
                    :signed-view-pct (fn [value] (str "view:" value))
                    :signed-delta (fn [value] (str "delta:" value))}
        view {:id "view-1"
              :kind :relative
              :instrument-id "perp:BTC"
              :comparator-instrument-id "perp:ETH"
              :direction :outperform
              :return 0.3
              :confidence 0.2}
        draft {:universe [btc-instrument eth-instrument]
               :return-model {:kind :black-litterman
                              :views [view]}}
        readiness {:request {:universe [btc-instrument eth-instrument]
                             :return-model {:kind :black-litterman
                                            :views [view]}}}
        preview {:status :ready
                 :rows [{:instrument-id "perp:BTC"
                         :label "BTC"
                         :prior-return 0.2
                         :posterior-return 0.21}
                        {:instrument-id "perp:ETH"
                         :label "ETH"
                         :prior-return 0.3
                         :posterior-return 0.28}]}
        model (view-model/black-litterman-cards-model draft
                                                       readiness
                                                       preview
                                                       formatters)
        cards-by-role (into {}
                            (map (juxt :role identity))
                            (:cards model))
        market-card (get cards-by-role
                         "portfolio-optimizer-setup-use-my-views-card-market-reference")
        views-card (get cards-by-role
                        "portfolio-optimizer-setup-use-my-views-card-your-views")
        output-card (get cards-by-role
                         "portfolio-optimizer-setup-use-my-views-card-combined-output")
        output-note (some #(when (= :note (:kind %)) %)
                          (:rows output-card))]
    (is (= ["Market reference" "Your views" "Combined output"]
           (mapv :label (:cards model))))
    (is (= [{:kind :value
             :label "ETH"
             :value-label "pct:0.3"
             :value-tone :prior}
            {:kind :value
             :label "BTC"
             :value-label "pct:0.2"
             :value-tone :prior}]
           (mapv #(select-keys % [:kind :label :value-label :value-tone])
                 (:rows market-card))))
    (is (= [{:kind :view
             :label "BTC > ETH by"
             :return-label "view:0.3"
             :confidence-label "low"}]
           (mapv #(select-keys % [:kind
                                  :label
                                  :return-label
                                  :confidence-label])
                 (:rows views-card))))
    (is (some #(and (= :combined-output (:kind %))
                    (= "BTC" (:label %))
                    (= "pct:0.2" (:prior-label %))
                    (= "pct:0.21" (:posterior-label %))
                    (= "delta:0.009999999999999981" (:delta-label %)))
              (:rows output-card)))
    (is (str/includes? (:copy output-note)
                       "Low confidence on BTC means your view:0.3 view only pulls posterior to pct:0.21"))))
