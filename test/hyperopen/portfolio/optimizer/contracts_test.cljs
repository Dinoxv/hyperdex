(ns hyperopen.portfolio.optimizer.contracts-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.spec.alpha :as s]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

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

(deftest state-paths-and-contract-specs-are-named-test
  (is (= [:portfolio :optimizer]
         contracts/optimizer-path))
  (is (= [:portfolio :optimizer :draft]
         contracts/draft-path))
  (is (= [:portfolio :optimizer :run-state]
         contracts/run-state-path))
  (is (= [:portfolio :optimizer :tracking]
         contracts/tracking-path))
  (is (= [:portfolio-ui :optimizer]
         contracts/optimizer-ui-path))
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
