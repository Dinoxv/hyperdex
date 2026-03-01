(ns hyperopen.vaults.detail.benchmarks-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.vaults.detail.benchmarks :as benchmarks]))

(deftest returns-benchmark-selector-model-builds-market-and-vault-options-test
  (let [vault-address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        state {:asset-selector {:markets [{:coin "BTC"
                                           :symbol "BTC"
                                           :dex "hl"
                                           :market-type :perp
                                           :openInterest 1000}]}
               :vaults-ui {:detail-returns-benchmark-coins ["BTC"
                                                            (str "vault:" vault-address)]
                           :detail-returns-benchmark-search ""
                           :detail-returns-benchmark-suggestions-open? false}
               :vaults {:merged-index-rows [{:name "Peer Vault"
                                             :vault-address vault-address
                                             :relationship {:type :normal}
                                             :tvl 120}]}}
        model (benchmarks/returns-benchmark-selector-model state)]
    (is (= ["BTC" (str "vault:" vault-address)]
           (:selected-coins model)))
    (is (= "BTC (HL PERP)"
           (get-in model [:label-by-coin "BTC"])))
    (is (= "Peer Vault (VAULT)"
           (get-in model [:label-by-coin (str "vault:" vault-address)])))))

(deftest benchmark-cumulative-return-points-by-coin-supports-market-and-vault-benchmarks-test
  (let [vault-address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        state {:candles {"BTC" {:1h [[1 0 0 0 100]
                                     [2 0 0 0 110]]}}
               :vaults {:merged-index-rows [{:name "Peer Vault"
                                             :vault-address vault-address
                                             :relationship {:type :normal}
                                             :tvl 120
                                             :snapshot-by-key {:month [0.02 0.08]}}]}}
        strategy-return-points [{:time-ms 1 :value 0}
                                {:time-ms 2 :value 10}]
        rows-by-coin (benchmarks/benchmark-cumulative-return-points-by-coin
                      state
                      :month
                      ["BTC" (str "vault:" vault-address)]
                      strategy-return-points)]
    (is (= [0 10]
           (mapv :value (get rows-by-coin "BTC"))))
    (is (= [2 8]
           (mapv :value (get rows-by-coin (str "vault:" vault-address)))))))
