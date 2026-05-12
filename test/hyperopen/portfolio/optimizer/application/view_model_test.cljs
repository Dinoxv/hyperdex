(ns hyperopen.portfolio.optimizer.application.view-model-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.portfolio.optimizer.actions.common :as action-common]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.application.universe-candidates :as universe-candidates]
            [hyperopen.portfolio.optimizer.application.view-model :as view-model]
            [hyperopen.portfolio.optimizer.fixtures :as fixtures]))

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

(def ^:private vault-address
  "0x1111111111111111111111111111111111111111")

(def ^:private vault-instrument
  {:instrument-id (str "vault:" vault-address)
   :market-type :vault
   :coin (str "vault:" vault-address)
   :vault-address vault-address
   :name "Alpha Vault"})

(defn- candle-rows
  [time-and-close-pairs]
  (mapv (fn [[time-ms close]]
          {:time time-ms
           :close (str close)})
        time-and-close-pairs))

(defn- ready-workspace-state
  []
  {:router {:path "/portfolio/optimize/new"}
   :portfolio {:optimizer
               {:draft {:id "draft-current"
                        :name "Current Draft"
                        :universe [btc-instrument eth-instrument]
                        :objective {:kind :max-sharpe}
                        :return-model {:kind :historical-mean}
                        :risk-model {:kind :sample-covariance}
                        :constraints {:long-only? true
                                      :max-asset-weight 1.0}
                        :metadata {:dirty? false}}
                :history-data {:candle-history-by-coin
                               {"BTC" (candle-rows [[1000 100]
                                                    [2000 110]
                                                    [3000 108]
                                                    [4000 116]])
                                "ETH" (candle-rows [[1000 50]
                                                    [2000 54]
                                                    [3000 49]
                                                    [4000 55]])}
                               :funding-history-by-coin {}}
                :market-cap-by-coin {}
                :runtime {:as-of-ms 5000
                          :stale-after-ms 60000}
                :run-state {:status :succeeded
                            :run-id "run-1"
                            :completed-at-ms 5100}}}
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
   {:computed-at-ms 5100
    :request-signature (request-signature-for-state state)
    :result {:status :solved
             :instrument-ids ["perp:BTC" "perp:ETH"]
             :target-weights [0.6 0.4]
             :current-weights [0.5 0.5]
             :target-weights-by-instrument {"perp:BTC" 0.6
                                            "perp:ETH" 0.4}
             :current-weights-by-instrument {"perp:BTC" 0.5
                                             "perp:ETH" 0.5}
             :expected-return 0.1
             :volatility 0.2
             :performance {:shrunk-sharpe 0.5}
             :diagnostics {:turnover 0.1}
             :rebalance-preview {:status :ready
                                 :capital-usd 1000
                                 :summary {:ready-count 2
                                           :blocked-count 0}
                                 :rows []}}}))

(deftest workspace-model-projects-setup-state-test
  (let [state (ready-workspace-state)
        solved-run (solved-run-for-state state)
        model (view-model/workspace-model
               (-> state
                   (assoc-in [:portfolio :optimizer :active-scenario :loaded-id]
                             "scn_current")
                   (assoc-in [:portfolio :optimizer :run-state :request-signature]
                             (:request-signature solved-run))
                   (assoc-in [:portfolio :optimizer :last-successful-run]
                             solved-run)
                   (assoc-in [:portfolio :optimizer :scenario-save-state :status]
                             :saving)
                   (assoc-in [:portfolio-ui :optimizer :black-litterman-editor]
                             {:selected-kind :absolute}))
               {:scenario-id "new"})]
    (is (= "Current Draft" (get-in model [:draft :name])))
    (is (= :succeeded (get-in model [:run-state :status])))
    (is (= :idle (get-in model [:optimization-progress :status])))
    (is (= :saving (get-in model [:scenario-save-state :status])))
    (is (= :idle (get-in model [:history-load-state :status])))
    (is (= {:selected-kind :absolute} (:editor-state model)))
    (is (= "/portfolio/optimize/scn_current" (:result-path model)))
    (is (false? (:running? model)))
    (is (true? (:run-triggerable? model)))
    (is (true? (:saving-scenario? model)))
    (is (true? (:current-result? model)))
    (is (map? (:readiness model)))
    (is (map? (:snapshot model)))
    (is (map? (:preview-snapshot model)))))

(deftest scenario-detail-model-scopes-mismatched-route-state-test
  (let [model (view-model/scenario-detail-model
               {:portfolio-ui {:optimizer {:results-tab :tracking}}
                :portfolio {:optimizer
                            {:active-scenario {:loaded-id "scn_old"
                                               :name "Old Scenario"
                                               :status :computed}
                             :draft {:id "scn_old"
                                     :name "Old Scenario"
                                     :universe [btc-instrument]}
                             :last-successful-run (fixtures/sample-last-successful-run
                                                   {:result {:status :solved}})
                             :scenario-load-state {:status :loading
                                                   :scenario-id "scn_new"}}}}
               {:scenario-id "scn_new"})]
    (is (= "scn_new" (:scenario-id model)))
    (is (= :tracking (:selected-tab model)))
    (is (true? (:loading? model)))
    (is (= "Scenario scn_new" (:scenario-name model)))
    (is (= [] (get-in model [:state :portfolio :optimizer :draft :universe])))
    (is (nil? (get-in model [:state :portfolio :optimizer :last-successful-run])))
    (is (= {:loaded-id nil
            :status :loading
            :read-only? true}
           (get-in model [:state :portfolio :optimizer :active-scenario])))
    (is (false? (:current-result? model)))
    (is (false? (:stale? model)))))

(deftest scenario-detail-model-retains-unsaved-draft-route-state-test
  (let [retained-run (fixtures/sample-last-successful-run
                      {:result {:status :solved
                                :instrument-ids ["perp:BTC"]
                                :target-weights [1.0]
                                :current-weights [0.25]}})
        state {:portfolio-ui {:optimizer {:results-tab :recommendation}}
               :portfolio {:optimizer
                           {:active-scenario {:loaded-id nil
                                              :status :computed}
                            :draft {:id "draft-current"
                                    :name "Retained Draft"
                                    :universe [btc-instrument]}
                            :last-successful-run retained-run
                            :scenario-load-state {:status :loading
                                                  :scenario-id "draft"}}}}
        model (view-model/scenario-detail-model state {:scenario-id "draft"})
        unnamed-model (view-model/scenario-detail-model
                       (assoc-in state [:portfolio :optimizer :draft :name] nil)
                       {:scenario-id "draft"})]
    (is (= "draft" (:scenario-id model)))
    (is (false? (:loading? model)))
    (is (= "Retained Draft" (:scenario-name model)))
    (is (= retained-run (:last-successful-run model)))
    (is (= {:loaded-id nil
            :status :computed}
           (:active-scenario model)))
    (is (= [btc-instrument] (get-in model [:state :portfolio :optimizer :draft :universe])))
    (is (= "Unsaved Optimization" (:scenario-name unnamed-model)))))

(deftest universe-section-model-projects-search-candidates-test
  (let [calls (atom [])
        candidate {:key "perp:ETH"
                   :market-type :perp
                   :coin "ETH"
                   :symbol "ETH-USDC"}
        candidate-markets-stub (fn
                                 ([_state _universe query]
                                  (swap! calls conj query)
                                  [candidate])
                                 ([_state _universe query _opts]
                                  (swap! calls conj query)
                                  [candidate]))
        blank-model (with-redefs [universe-candidates/candidate-markets
                                  candidate-markets-stub]
                      (view-model/universe-section-model
                       {:portfolio-ui {:optimizer {:universe-search-query "   "}}}
                       {:universe [btc-instrument]}
                       {}))
        search-model (with-redefs [universe-candidates/candidate-markets
                                   candidate-markets-stub]
                       (view-model/universe-section-model
                        {:portfolio-ui {:optimizer {:universe-search-query "eth"
                                                    :universe-search-active-index 3}}}
                        {:universe [btc-instrument]}
                        {}))]
    (is (= ["eth"] @calls))
    (is (= [btc-instrument] (:universe blank-model)))
    (is (= "   " (:search-query blank-model)))
    (is (false? (:searching? blank-model)))
    (is (= [] (:markets blank-model)))
    (is (= [] (:market-keys blank-model)))
    (is (= "eth" (:search-query search-model)))
    (is (true? (:searching? search-model)))
    (is (= [candidate] (:markets search-model)))
    (is (= 0 (:active-index search-model)))
    (is (= ["perp:ETH"] (:market-keys search-model)))))

(deftest selected-history-label-projects-loading-and-readiness-state-test
  (testing "prefetch and load states win before readiness labels"
    (is (= "queued"
           (view-model/selected-history-label
            {:portfolio {:optimizer {:history-prefetch
                                      {:by-instrument-id
                                       {"perp:BTC" {:status :queued}}}}}}
            {}
            {}
            {}
            btc-instrument)))
    (is (= "loading"
           (view-model/selected-history-label
            {}
            {}
            {:status :loading
             :request-signature {:universe [btc-instrument]}}
            {}
            btc-instrument))))
  (testing "readiness and validated history produce stable labels"
    (is (= "sufficient"
           (view-model/selected-history-label
            {}
            {:request {:universe [btc-instrument]}}
            {}
            {}
            btc-instrument)))
    (is (= "missing"
           (view-model/selected-history-label
            {}
            {}
            {:status :succeeded
             :request-signature {:universe [btc-instrument]}}
            {"perp:BTC" :missing}
            btc-instrument)))
    (is (= "insufficient"
           (view-model/selected-history-label
            {:portfolio {:optimizer {:history-data
                                      {:candle-history-by-coin
                                       {"BTC" (candle-rows [[1000 100]])}}}}}
            {}
            {:status :succeeded
             :request-signature {:universe [btc-instrument]}}
            {"perp:BTC" :insufficient}
            btc-instrument)))))

(deftest selected-history-label-projects-vault-gap-and-cache-state-test
  (testing "vault shared gaps are surfaced before generic readiness labels"
    (is (= "shared gap"
           (view-model/selected-history-label
            {}
            {:request {:universe [vault-instrument]}}
            {:status :succeeded
             :request-signature {:universe [vault-instrument]}}
            {(:instrument-id vault-instrument) :loaded-but-misaligned}
            vault-instrument))))
  (testing "cached vault history allows an insufficient label without a fresh load result"
    (is (= "insufficient"
           (view-model/selected-history-label
            {:portfolio {:optimizer {:history-data
                                      {:vault-details-by-address
                                       {vault-address {:portfolio
                                                       {:month
                                                        {:accountValueHistory
                                                         [[1000 100]
                                                          [2000 101]]}}}}}}}}
            {}
            {:status :idle}
            {(:instrument-id vault-instrument) :insufficient}
            vault-instrument)))))
