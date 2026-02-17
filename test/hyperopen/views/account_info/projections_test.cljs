(ns hyperopen.views.account-info.projections-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.account-info.projections :as projections]))

(deftest open-order-for-active-asset-matches-base-and-namespaced-coin-forms-test
  (is (true? (projections/open-order-for-active-asset? "SOL"
                                                    {:coin "SOL"})))
  (is (true? (projections/open-order-for-active-asset? "SOL"
                                                    {:coin "perp:SOL"})))
  (is (true? (projections/open-order-for-active-asset? "perp:SOL"
                                                    {:coin "SOL"})))
  (is (false? (projections/open-order-for-active-asset? "SOL"
                                                     {:coin "BTC"})))
  (is (false? (projections/open-order-for-active-asset? nil
                                                     {:coin "SOL"}))))

(deftest normalized-open-orders-for-active-asset-filters-and-dedupes-by-coin-and-oid-test
  (let [live-orders [{:coin "SOL"
                      :oid 11
                      :side "B"
                      :sz "1.00"
                      :limitPx "60.0"
                      :timestamp 1700000000000}
                     {:coin "SOL"
                      :oid 11
                      :side "B"
                      :sz "1.00"
                      :limitPx "60.0"
                      :timestamp 1700000000001}
                     {:coin "BTC"
                      :oid 91
                      :side "A"
                      :sz "0.10"
                      :limitPx "100000.0"
                      :timestamp 1700000000002}
                     {:order {:coin "perp:SOL"
                              :oid 33
                              :side "A"
                              :sz "2.50"
                              :px "61.5"
                              :timestamp 1700000000003}}]
        snapshot nil
        snapshot-by-dex {:dex-a [{:coin "SOL"
                                  :oid 11
                                  :side "B"
                                  :sz "1.00"
                                  :limitPx "60.0"
                                  :timestamp 1700000009999}]
                         :dex-b [{:coin "perp:SOL"
                                  :oid 33
                                  :side "A"
                                  :sz "2.50"
                                  :limitPx "61.5"
                                  :timestamp 1700000010000}]}
        orders (projections/normalized-open-orders-for-active-asset live-orders
                                                                     snapshot
                                                                     snapshot-by-dex
                                                                     "SOL")]
    (is (= [11 33] (mapv :oid orders)))
    (is (= ["SOL" "perp:SOL"] (mapv :coin orders)))
    (is (= ["B" "A"] (mapv :side orders)))
    (is (= ["60.0" "61.5"] (mapv :px orders)))))

(deftest normalized-open-orders-excludes-pending-cancel-oids-test
  (let [orders [{:coin "SOL"
                 :oid "11"
                 :side "B"
                 :sz "1.00"
                 :limitPx "60.0"}
                {:coin "SOL"
                 :oid 12
                 :side "A"
                 :sz "2.00"
                 :limitPx "61.0"}]
        normalized (projections/normalized-open-orders orders nil nil #{11})]
    (is (= [12] (mapv :oid normalized)))))

(deftest normalized-open-orders-for-active-asset-excludes-pending-cancel-oids-test
  (let [orders [{:coin "SOL"
                 :oid 11
                 :side "B"
                 :sz "1.00"
                 :limitPx "60.0"}
                {:coin "SOL"
                 :oid 12
                 :side "A"
                 :sz "2.00"
                 :limitPx "61.0"}]
        active-orders (projections/normalized-open-orders-for-active-asset orders
                                                                            nil
                                                                            nil
                                                                            "SOL"
                                                                            #{12})]
    (is (= [11] (mapv :oid active-orders)))))
