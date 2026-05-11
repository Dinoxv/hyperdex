(ns hyperopen.portfolio.optimizer.contract-fixtures)

(def v1-draft
  {:schema-version 1
   :id "persisted-v1"
   :name "Persisted V1"
   :status :saved
   :universe [{:instrument-id "perp:BTC"
               :market-type :perp
               :coin "BTC"}]
   :objective {:kind :minimum-variance}
   :return-model {:kind :historical-mean}
   :risk-model {:kind :diagonal-shrink}
   :constraints {:long-only? true
                 :max-asset-weight 0.8}
   :execution-assumptions {:default-order-type :market
                           :fallback-slippage-bps 25}
   :metadata {:dirty? false
              :created-at-ms 1777046400000
              :updated-at-ms 1777046400000}})

(def v1-scenario-record
  {:schema-version 1
   :id "persisted-v1"
   :name "Persisted V1"
   :status :saved
   :config v1-draft
   :saved-run {:request-signature {:scenario-id "persisted-v1"}
               :computed-at-ms 1777046400000
               :result {:status :infeasible
                        :reason :insufficient-history}}
   :created-at-ms 1777046400000
   :updated-at-ms 1777046400000})

(def v1-tracking-record
  {:schema-version 1
   :scenario-id "persisted-v1"
   :updated-at-ms 1777046400000
   :snapshots [{:scenario-id "persisted-v1"
                :as-of-ms 1777046400000
                :status :tracked
                :rows []}
               {:scenario-id "persisted-v1"
                :as-of-ms 1777132800000
                :status :not-trackable
                :warnings [{:code :no-saved-run}]}]})
