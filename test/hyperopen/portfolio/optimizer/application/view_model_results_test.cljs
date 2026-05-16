(ns hyperopen.portfolio.optimizer.application.view-model-results-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.portfolio.optimizer.application.view-model.results :as results]))

(deftest enrich-result-labels-replaces-raw-vault-labels-from-draft-test
  (let [vault-address "0x1e37a337ed460039d1b15bd3bc489de789768d5e"
        vault-id (str "vault:" vault-address)
        result {:status :solved
                :instrument-ids ["perp:BTC" vault-id]
                :labels-by-instrument {"perp:BTC" "BTC"
                                       vault-id vault-id}}
        draft {:universe [{:instrument-id "perp:BTC"
                           :market-type :perp
                           :coin "BTC"}
                          {:instrument-id vault-id
                           :market-type :vault
                           :name "HLP Vault"}]}
        enriched (results/enrich-result-labels result draft)]
    (is (= "perp:BTC"
           (results/instrument-label (:labels-by-instrument enriched) "perp:BTC")))
    (is (= "HLP Vault"
           (results/instrument-label (:labels-by-instrument enriched) vault-id)))))

(deftest enrich-result-labels-replaces-raw-vault-frontier-overlay-labels-test
  (let [vault-address "0xdfc24b077bc1425ad1dea75bcb6f8158e10df303"
        vault-id (str "vault:" vault-address)
        result {:status :solved
                :instrument-ids ["perp:BTC" vault-id]
                :labels-by-instrument {(keyword vault-id) vault-id}
                :frontier-overlays
                {:standalone [{:instrument-id vault-id
                               :label vault-id
                               :target-weight 0.5
                               :expected-return 0.2
                               :volatility 0.4}]
                 :contribution [{:instrument-id vault-id
                                 :label vault-id
                                 :target-weight 0.5
                                 :expected-return 0.1
                                 :volatility 0.2}]}}
        draft {:universe [{:instrument-id vault-id
                           :market-type :vault
                           :coin vault-id
                           :vault-address vault-address
                           :name "Hyperliquidity Provider (HLP)"
                           :symbol "Hyperliquidity Provider (HLP)"}]}
        enriched (results/enrich-result-labels result draft)]
    (is (= "Hyperliquidity Provider (HLP)"
           (get-in enriched [:labels-by-instrument vault-id])))
    (is (= "Hyperliquidity Provider (HLP)"
           (get-in enriched [:frontier-overlays :standalone 0 :label])))
    (is (= "Hyperliquidity Provider (HLP)"
           (get-in enriched [:frontier-overlays :contribution 0 :label])))))

(deftest enrich-result-labels-preserves-non-map-results-test
  (is (= :loading
         (results/enrich-result-labels :loading {:universe []}))))
