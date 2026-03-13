(ns hyperopen.websocket.diagnostics.view-model-test
  (:require [cljs.spec.alpha :as s]
            [cljs.test :refer-macros [deftest is]]
            [hyperopen.websocket.diagnostics.schema :as diagnostics-schema]
            [hyperopen.websocket.diagnostics.view-model :as diagnostics-vm]))

(def ^:private address
  "0x1234567890abcdef1234567890abcdef12345678")

(defn- base-state
  []
  {:router {:path "/trade"}
   :trade-ui {:mobile-surface :ticket}
   :websocket {:health {:generated-at-ms 1700000000000
                        :transport {:state :connected
                                    :freshness :live
                                    :last-recv-at-ms 1699999999500
                                    :expected-traffic? true
                                    :attempt 2
                                    :last-close {:code 1006
                                                 :reason "abnormal"
                                                 :at-ms 1699999999000}}
                        :groups {:orders_oms {:worst-status :idle}
                                 :market_data {:worst-status :live}
                                 :account {:worst-status :n-a}}
                        :streams {["openOrders" nil address nil nil]
                                  {:group :orders_oms
                                   :topic "openOrders"
                                   :subscribed? true
                                   :status :n-a
                                   :last-payload-at-ms 1699999999500
                                   :stale-threshold-ms nil
                                   :descriptor {:type "openOrders"
                                                :user address}}}
                        :market-projection {:stores [{:store-id nil
                                                      :pending-count 0
                                                      :overwrite-total 0
                                                      :flush-count 1
                                                      :max-pending-depth 1
                                                      :p95-flush-duration-ms 6
                                                      :last-flush-duration-ms 6
                                                      :last-queue-wait-ms 2}]
                                            :flush-events [{:seq 1
                                                            :at-ms 1699999999900
                                                            :store-id nil
                                                            :pending-count 1
                                                            :overwrite-count 0
                                                            :flush-duration-ms 6
                                                            :queue-wait-ms 2}]}}}
   :websocket-ui {:diagnostics-open? true
                  :reveal-sensitive? false
                  :copy-status nil
                  :show-surface-freshness-cues? true
                  :reset-counts {:orders_oms 1}
                  :diagnostics-timeline [{:event :connected
                                          :at-ms 1699999999800
                                          :details {:user address}}]}})

(deftest footer-view-model-satisfies-schema-and-masks-sensitive-values-test
  (let [vm (diagnostics-vm/footer-view-model
            (base-state)
            {:app-version "0.1.0"
             :build-id "build-123"
             :wall-now-ms 1700000001000
             :diagnostics-timeline-limit 10
             :network-hint {:effective-type "3g"
                            :rtt 450
                            :downlink 0.7
                            :save-data? false}})
        stream-row (get-in vm [:diagnostics :stream-groups 0 :streams 0])
        timeline-row (get-in vm [:diagnostics :timeline 0])]
    (is (s/valid? ::diagnostics-schema/footer-vm vm))
    (is (not (.includes (:descriptor-text stream-row) address)))
    (is (.includes (:descriptor-text stream-row) "0x1234...45678"))
    (is (not (.includes (:details-text timeline-row) address)))
    (is (.includes (:details-text timeline-row) "0x1234...45678"))))

(deftest footer-view-model-normalizes-empty-market-store-ids-to-na-test
  (let [vm (diagnostics-vm/footer-view-model
            (base-state)
            {:app-version "0.1.0"
             :build-id "build-123"
             :wall-now-ms 1700000001000
             :diagnostics-timeline-limit 10})
        store (first (get-in vm [:diagnostics :market-projection :stores]))
        flush-row (first (get-in vm [:diagnostics :market-projection :flush-rows]))]
    (is (= "n/a" (:store-id-text store)))
    (is (= "n/a" (:store-id-text flush-row)))))
