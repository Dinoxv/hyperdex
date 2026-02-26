(ns hyperopen.views.vaults.detail-vm-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.vaults.detail-vm :as detail-vm]))

(def sample-state
  {:router {:path "/vaults/0x1234567890abcdef1234567890abcdef12345678"}
   :vaults-ui {:detail-tab :about
               :snapshot-range :month
               :detail-loading? false}
   :vaults {:errors {:details-by-address {}
                     :webdata-by-vault {}}
            :details-by-address {"0x1234567890abcdef1234567890abcdef12345678"
                                 {:name "Vault Detail"
                                  :leader "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
                                  :description "Sample vault"
                                  :portfolio {:month {:accountValueHistory [[1 10] [2 11] [3 15]]}}
                                  :followers 3
                                  :leader-commission 0.15
                                  :relationship {:type :child
                                                 :parent-address "0x9999999999999999999999999999999999999999"}
                                  :follower-state {:vault-equity 50
                                                   :all-time-pnl 12}}}
            :webdata-by-vault {"0x1234567890abcdef1234567890abcdef12345678"
                               {:fills [{:time 3
                                         :coin "BTC"
                                         :side "buy"
                                         :sz "0.5"
                                         :px "101"}
                                        {:time 4
                                         :coin "ETH"
                                         :side "sell"
                                         :sz "1.2"
                                         :px "202"}]
                                :openOrders [1]
                                :assetPositions [1 2]}}
            :user-equity-by-address {"0x1234567890abcdef1234567890abcdef12345678"
                                     {:equity 50}}
            :merged-index-rows [{:name "Vault Detail"
                                 :vault-address "0x1234567890abcdef1234567890abcdef12345678"
                                 :leader "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
                                 :tvl 200
                                 :apr 0.2
                                 :snapshot-by-key {:month [0.1 0.2]
                                                   :all-time [0.5]}}]}})

(deftest vault-detail-vm-builds-metrics-relationship-chart-and-activity-test
  (let [vm (detail-vm/vault-detail-vm sample-state)]
    (is (= :detail (:kind vm)))
    (is (= "Vault Detail" (:name vm)))
    (is (= "0x1234567890abcdef1234567890abcdef12345678" (:vault-address vm)))
    (is (= :child (get-in vm [:relationship :type])))
    (is (= "0x9999999999999999999999999999999999999999"
           (get-in vm [:relationship :parent-address])))
    (is (= 200 (get-in vm [:metrics :tvl])))
    (is (= 20 (get-in vm [:metrics :past-month-return])))
    (is (= 50 (get-in vm [:metrics :your-deposit])))
    (is (= 12 (get-in vm [:metrics :all-time-earned])))
    (is (seq (get-in vm [:chart :points])))
    (is (seq (get-in vm [:chart :path])))
    (is (= 2 (count (:activity-fills vm))))
    (is (= 2 (get-in vm [:activity-summary :fill-count])))
    (is (= 1 (get-in vm [:activity-summary :open-order-count])))
    (is (= 2 (get-in vm [:activity-summary :position-count])))))

(deftest vault-detail-vm-flags-invalid-vault-addresses-test
  (let [vm (detail-vm/vault-detail-vm (assoc-in sample-state [:router :path] "/vaults/not-an-address"))]
    (is (= :detail (:kind vm)))
    (is (true? (:invalid-address? vm)))))
