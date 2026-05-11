(ns hyperopen.portfolio.optimizer.contracts-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.spec.alpha :as s]
            [hyperopen.portfolio.optimizer.contract-fixtures :as contract-fixtures]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.optimizer.fixtures :as optimizer-fixtures]))

(def sample-request
  {:scenario-id "scn_contract"
   :as-of-ms 2000
   :requested-universe [{:instrument-id "perp:BTC"}]
   :universe [{:instrument-id "perp:BTC"}]
   :current-portfolio {:capital {:nav-usdc 1000}}
   :return-model {:kind :historical-mean}
   :risk-model {:kind :diagonal-shrink}
   :objective {:kind :minimum-variance}
   :constraints {:max-asset-weight 0.5}
   :execution-assumptions {:default-order-type :market
                           :fallback-slippage-bps 25
                           :fee-mode :taker
                           :cost-contexts-by-id
                           {"perp:BTC" {:source :live-orderbook
                                        :best-bid {:px "100" :sz "1"}
                                        :best-ask {:px "101" :sz "2"}}}}
   :history {:return-series-by-instrument {"perp:BTC" [0.01 0.02]}
             :freshness {:as-of-ms 1000
                         :age-ms 10
                         :stale? false}}})

(def sample-draft
  {:name "Contract Scenario"
   :universe []
   :objective {:kind :minimum-variance}
   :return-model {:kind :historical-mean}
   :risk-model {:kind :diagonal-shrink}
   :constraints {:long-only? false}
   :execution-assumptions {:default-order-type :market}
   :metadata {:dirty? false}})

(def sample-solved-result
  {:status :solved
   :scenario-id "scn_contract"
   :as-of-ms 5000
   :instrument-ids ["perp:BTC" "perp:ETH"]
   :target-weights [0.6 0.4]
   :current-weights [0.5 0.5]
   :target-weights-by-instrument {"perp:BTC" 0.6
                                  "perp:ETH" 0.4}
   :current-weights-by-instrument {"perp:BTC" 0.5
                                   "perp:ETH" 0.5}
   :expected-returns-by-instrument {"perp:BTC" 0.12
                                    "perp:ETH" 0.08}
   :return-decomposition-by-instrument {"perp:BTC" {:total 0.12}
                                        "perp:ETH" {:total 0.08}}
   :diagnostics {:weight-sensitivity-by-instrument {"perp:BTC" {:max-delta 0.01}}}
   :rebalance-preview {:trades []}})

(defn- allowed?
  [allowed status]
  (contains? allowed status))

(deftest state-paths-and-contract-specs-are-named-test
  (is (= [:portfolio :optimizer]
         contracts/optimizer-path))
  (is (= [:portfolio :optimizer :draft]
         contracts/draft-path))
  (is (= [:portfolio :optimizer :draft :return-model]
         contracts/draft-return-model-path))
  (is (= [:portfolio :optimizer :draft :return-model :views]
         contracts/draft-return-model-views-path))
  (is (= [:portfolio :optimizer :draft :metadata :dirty?]
         contracts/draft-dirty-path))
  (is (= [:portfolio :optimizer :run-state]
         contracts/run-state-path))
  (is (= [:portfolio :optimizer :last-successful-run :result]
         contracts/last-successful-run-result-path))
  (is (= [:portfolio :optimizer :history-load-state :request-signature]
         contracts/history-load-state-request-signature-path))
  (is (= [:portfolio :optimizer :execution-modal :error]
         contracts/execution-modal-error-path))
  (is (= [:portfolio :optimizer :tracking]
         contracts/tracking-path))
  (is (= [:portfolio :optimizer :tracking :error]
         contracts/tracking-error-path))
  (is (= [:portfolio-ui :optimizer]
         contracts/optimizer-ui-path))
  (is (= [:portfolio-ui :optimizer :results-tab]
         contracts/ui-results-tab-path))
  (is (= [:portfolio-ui :optimizer :black-litterman-editor]
         contracts/ui-black-litterman-editor-path))
  (is (= [:portfolio-ui :optimizer :frontier-overlay-mode]
         contracts/ui-frontier-overlay-mode-path))
  (is (= ::contracts/draft
         (:optimizer/draft contracts/contract-specs)))
  (is (= ::contracts/request-signature
         (:optimizer/request-signature contracts/contract-specs)))
  (is (= ::contracts/worker-envelope
         (:optimizer/worker-envelope contracts/contract-specs))))

(deftest draft-and-record-migrations-stamp-current-versions-test
  (testing "drafts"
    (let [draft (contracts/migrate-draft sample-draft)]
      (is (= contracts/draft-schema-version
             (:schema-version draft)))
      (is (= draft (contracts/migrate-draft draft)))
      (is (s/valid? ::contracts/draft draft))))
  (testing "saved scenarios"
    (let [record (contracts/migrate-scenario-record
                  {:id "scn_contract"
                   :name "Contract Scenario"
                   :status :saved
                   :config (contracts/migrate-draft sample-draft)
                   :saved-run nil
                   :updated-at-ms 3000})]
      (is (= contracts/scenario-record-schema-version
             (:schema-version record)))
      (is (= record (contracts/migrate-scenario-record record)))
      (is (s/valid? ::contracts/scenario-record record))))
  (testing "tracking records"
    (let [record (contracts/migrate-tracking-record
                  {:scenario-id "scn_contract"
                   :updated-at-ms 4000
                   :snapshots []})]
      (is (= contracts/tracking-record-schema-version
             (:schema-version record)))
      (is (= record (contracts/migrate-tracking-record record)))
      (is (s/valid? ::contracts/tracking-record record)))))

(deftest future-version-migrations-fail-until-format-changes-test
  (testing "current optimizer persisted contracts are still version 1"
    (is (= 1 contracts/draft-schema-version))
    (is (= 1 contracts/scenario-record-schema-version))
    (is (= 1 contracts/tracking-record-schema-version)))
  (testing "unsupported future versions fail loudly until a real migration exists"
    (is (thrown-with-msg?
         js/Error
         #"Unsupported optimizer draft schema version"
         (contracts/migrate-draft (assoc sample-draft :schema-version 2))))
    (is (thrown-with-msg?
         js/Error
         #"Unsupported optimizer scenario record schema version"
         (contracts/migrate-scenario-record
          {:schema-version 2
           :id "scn_contract"
           :name "Contract Scenario"
           :status :saved
           :config (contracts/migrate-draft sample-draft)
           :updated-at-ms 3000})))
    (is (thrown-with-msg?
         js/Error
         #"Unsupported optimizer tracking record schema version"
         (contracts/migrate-tracking-record
          {:schema-version 2
           :scenario-id "scn_contract"
           :updated-at-ms 4000
           :snapshots []})))))

(deftest v1-persisted-contract-fixtures-migrate-and-validate-test
  (let [draft (contracts/migrate-draft contract-fixtures/v1-draft)
        scenario (contracts/migrate-scenario-record
                  contract-fixtures/v1-scenario-record)
        tracking (contracts/migrate-tracking-record
                  contract-fixtures/v1-tracking-record)]
    (is (s/valid? ::contracts/draft draft)
        (s/explain-str ::contracts/draft draft))
    (is (s/valid? ::contracts/scenario-record scenario)
        (s/explain-str ::contracts/scenario-record scenario))
    (is (s/valid? ::contracts/tracking-record tracking)
        (s/explain-str ::contracts/tracking-record tracking))))

(deftest request-signature-canonicalizes-optimizer-inputs-test
  (let [signature (contracts/build-request-signature sample-request)
        changed-volatile (-> sample-request
                             (assoc-in [:execution-assumptions
                                        :cost-contexts-by-id
                                        "perp:BTC"
                                        :best-bid
                                        :sz]
                                       "99")
                             (assoc-in [:history :freshness :age-ms] 9999))
        changed-model (assoc sample-request
                             :return-model
                             {:kind :black-litterman
                              :views [{:instrument-id "perp:BTC"
                                       :return 0.2}]})]
    (is (= contracts/request-signature-schema-version
           (:schema-version signature)))
    (is (= sample-request (:request signature)))
    (is (= (contracts/optimizer-input-signature sample-request)
           (:input-signature signature)))
    (is (= (:input-signature signature)
           (contracts/optimizer-input-signature changed-volatile)))
    (is (not= (:input-signature signature)
              (contracts/optimizer-input-signature changed-model)))
    (is (s/valid? ::contracts/request-signature signature))))

(deftest specs-reject-malformed-stabilized-contracts-test
  (testing "draft maps require the stabilized draft shapes"
    (let [draft (contracts/migrate-draft sample-draft)]
      (is (false? (s/valid? ::contracts/draft
                            (assoc draft :universe {"perp:BTC" true}))))
      (is (false? (s/valid? ::contracts/draft
                            (assoc draft :status :unknown))))
      (is (false? (s/valid? ::contracts/draft
                            (assoc draft :constraints []))))))
  (testing "request signatures must be canonical for their request"
    (is (false? (s/valid? ::contracts/request-signature
                          (assoc (contracts/build-request-signature sample-request)
                                 :input-signature
                                 {:return-model {:kind :not-the-request}})))))
  (testing "scenario and tracking records reject unknown lifecycle statuses"
    (let [draft (contracts/migrate-draft sample-draft)]
      (is (false? (s/valid? ::contracts/scenario-record
                            {:schema-version contracts/scenario-record-schema-version
                             :id "scn_contract"
                             :name "Contract Scenario"
                             :status :missing
                             :config draft
                             :updated-at-ms 3000}))))
    (is (false? (s/valid? ::contracts/tracking-snapshot
                          {:scenario-id "scn_contract"
                           :as-of-ms 4000
                           :status :missing})))
    (is (false? (s/valid? ::contracts/tracking-record
                          {:schema-version contracts/tracking-record-schema-version
                           :scenario-id "scn_contract"
                           :updated-at-ms 4000
                           :snapshots [{:scenario-id "scn_contract"
                                        :as-of-ms 4000
                                        :status :missing}]})))))

(deftest result-payload-contract-validates-solved-payloads-test
  (is (s/valid? ::contracts/result-payload sample-solved-result))
  (is (s/valid? ::contracts/result-payload
                {:status :infeasible
                 :reason :insufficient-history}))
  (is (false? (s/valid? ::contracts/result-payload
                        {:status :solved})))
  (is (false? (s/valid? ::contracts/result-payload
                        (assoc sample-solved-result
                               :target-weights
                               [0.6]))))
  (is (false? (s/valid? ::contracts/result-payload
                        (assoc sample-solved-result
                               :target-weights-by-instrument
                               {"perp:BTC" 0.6})))))

(deftest result-payload-contract-accepts-real-solved-fixture-test
  (let [payload (get-in (optimizer-fixtures/sample-scenario-state)
                        [:portfolio
                         :optimizer
                         :last-successful-run
                         :result])]
    (is (= :solved (:status payload)))
    (is (s/valid? ::contracts/result-payload payload)
        (s/explain-str ::contracts/result-payload payload))))

(deftest optimizer-contract-status-sets-cover-current-producers-test
  (testing "draft statuses"
    (doseq [status [:draft :saved :archived :tracking]]
      (is (allowed? contracts/draft-statuses status))))
  (testing "scenario record statuses"
    (doseq [status [:saved :archived :executed :partially-executed :tracking :failed]]
      (is (allowed? contracts/scenario-record-statuses status))))
  (testing "tracking snapshot statuses"
    (doseq [status [:tracked :not-trackable]]
      (is (allowed? contracts/tracking-snapshot-statuses status))))
  (testing "result payload statuses"
    (doseq [status [:solved :infeasible :error :failed]]
      (is (allowed? contracts/result-payload-statuses status)))))

(deftest wire-codec-normalizes-worker-boundary-test
  (let [perp-id (keyword "perp:BTC")
        normalized (contracts/normalize-worker-boundary
                    {:type "optimizer-result"
                     :payload {:status "solved"
                               :expected-returns-by-instrument {perp-id 0.12}
                               :diagnostics {:weight-sensitivity-by-instrument
                                             {perp-id {:max-delta 0.01}}}}
                     :return-model {:kind "black-litterman"
                                    :views [{:kind "absolute"
                                             :instrument-id "perp:BTC"
                                             :weights {perp-id 1}}]}})]
    (is (= :optimizer-result (:type normalized)))
    (is (= :solved (get-in normalized [:payload :status])))
    (is (= {"perp:BTC" 0.12}
           (get-in normalized [:payload :expected-returns-by-instrument])))
    (is (= {"perp:BTC" {:max-delta 0.01}}
           (get-in normalized
                   [:payload :diagnostics :weight-sensitivity-by-instrument])))
    (is (= {"perp:BTC" 1}
           (get-in normalized [:return-model :views 0 :weights])))
    (is (s/valid? ::contracts/worker-envelope
                  {:type (:type normalized)
                   :payload (:payload normalized)}))))
