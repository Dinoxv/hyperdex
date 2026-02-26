(ns hyperopen.views.vaults.vm-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.vaults.vm :as vm]))

(def sample-state
  {:wallet {:address "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"}
   :vaults-ui {:search-query ""
               :filter-leading? true
               :filter-deposited? true
               :filter-others? true
               :filter-closed? false
               :snapshot-range :month
               :sort {:column :tvl
                      :direction :desc}}
   :vaults {:loading {:index? false
                      :summaries? false}
            :errors {:index nil
                     :summaries nil}
            :user-equity-by-address {"0x2222222222222222222222222222222222222222" {:equity 25}}
            :merged-index-rows [{:name "Hyperliquidity Provider (HLP)"
                                 :vault-address "0x1111111111111111111111111111111111111111"
                                 :leader "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
                                 :tvl 120
                                 :apr 0.12
                                 :relationship {:type :parent}
                                 :is-closed? false
                                 :create-time-ms (- (.now js/Date) (* 3 24 60 60 1000))
                                 :snapshot-by-key {:month [0.1 0.2]}}
                                {:name "Beta"
                                 :vault-address "0x2222222222222222222222222222222222222222"
                                 :leader "0x3333333333333333333333333333333333333333"
                                 :tvl 80
                                 :apr 0.08
                                 :relationship {:type :normal}
                                 :is-closed? false
                                 :create-time-ms (- (.now js/Date) (* 8 24 60 60 1000))
                                 :snapshot-by-key {:month [0.05 0.09]}}
                                {:name "Gamma"
                                 :vault-address "0x3333333333333333333333333333333333333333"
                                 :leader "0x4444444444444444444444444444444444444444"
                                 :tvl 50
                                 :apr 0.03
                                 :relationship {:type :normal}
                                 :is-closed? false
                                 :create-time-ms (- (.now js/Date) (* 5 24 60 60 1000))
                                 :snapshot-by-key {:month [0.01 0.02]}}
                                {:name "Closed Vault"
                                 :vault-address "0x4444444444444444444444444444444444444444"
                                 :leader "0x5555555555555555555555555555555555555555"
                                 :tvl 999
                                 :apr 0.4
                                 :relationship {:type :normal}
                                 :is-closed? true
                                 :create-time-ms (- (.now js/Date) (* 1 24 60 60 1000))
                                 :snapshot-by-key {:month [0.03]}}
                                {:name "Child Vault"
                                 :vault-address "0x5555555555555555555555555555555555555555"
                                 :leader "0x6666666666666666666666666666666666666666"
                                 :tvl 777
                                 :apr 0.22
                                 :relationship {:type :child}
                                 :is-closed? false
                                 :create-time-ms (- (.now js/Date) (* 4 24 60 60 1000))
                                 :snapshot-by-key {:month [0.03]}}]}})

(deftest vault-route-helper-parses-list-and-detail-routes-test
  (is (true? (vm/vault-route? "/vaults")))
  (is (false? (vm/vault-detail-route? "/vaults")))
  (is (= "0x1234567890abcdef1234567890abcdef12345678"
         (vm/selected-vault-address "/vaults/0x1234567890abcdef1234567890abcdef12345678")))
  (is (false? (vm/vault-route? "/trade"))))

(deftest vault-list-vm-groups-filters-and-sorts-rows-test
  (let [view-model (vm/vault-list-vm sample-state)]
    (is (= 3 (:visible-count view-model)))
    (is (= 250 (:total-visible-tvl view-model)))
    (is (= ["Hyperliquidity Provider (HLP)"]
           (mapv :name (:protocol-rows view-model))))
    (is (= ["Beta" "Gamma"]
           (mapv :name (:user-rows view-model))))
    (is (= ["Beta" "Hyperliquidity Provider (HLP)" "Gamma"]
           (mapv :name (:rows view-model)))))
  (let [view-model (vm/vault-list-vm (assoc-in sample-state [:vaults-ui :search-query] "beta"))]
    (is (= ["Beta"] (mapv :name (:rows view-model)))))
  (let [view-model (vm/vault-list-vm (-> sample-state
                                         (assoc-in [:vaults-ui :filter-leading?] false)
                                         (assoc-in [:vaults-ui :filter-deposited?] false)
                                         (assoc-in [:vaults-ui :filter-others?] true)))]
    (is (= ["Gamma"] (mapv :name (:rows view-model)))))
  (let [view-model (vm/vault-list-vm (assoc-in sample-state [:vaults-ui :filter-closed?] true))]
    (is (= 4 (:visible-count view-model)))))
