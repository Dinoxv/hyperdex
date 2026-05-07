(ns hyperopen.views.portfolio.optimize.black-litterman-preview-chart-test
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [hyperopen.test-support.hiccup :as hiccup]
            [hyperopen.views.portfolio.optimize.black-litterman-preview-chart :as preview-chart]))

(deftest black-litterman-preview-chart-renders-vault-name-instead-of-address-test
  (let [vault-address "0x3333333333333333333333333333333333333333"
        vault-id (str "vault:" vault-address)
        panel (preview-chart/black-litterman-preview-panel
               {:status :ready
                :request {:universe [{:instrument-id "perp:BTC"
                                      :market-type :perp
                                      :coin "BTC"}
                                     {:instrument-id vault-id
                                      :market-type :vault
                                      :coin vault-id
                                      :vault-address vault-address
                                      :name "Alpha Yield"}]
                          :return-model {:kind :black-litterman
                                         :views [{:id "view-1"
                                                  :kind :absolute
                                                  :instrument-id vault-id
                                                  :return 0.04
                                                  :confidence 0.8
                                                  :weights {vault-id 1}}]}
                          :risk-model {:kind :sample-covariance}
                          :periods-per-year 10
                          :history {:return-series-by-instrument
                                    {"perp:BTC" [0.01 0.02 -0.01 0.03]
                                     vault-id [0.02 -0.01 0.04 0.01]}}
                          :black-litterman-prior
                          {:source :market-cap
                           :weights-by-instrument {"perp:BTC" 0.5
                                                   vault-id 0.5}}}})
        text (hiccup/node-text panel)]
    (is (str/includes? text "Alpha Yield"))
    (is (not (str/includes? text vault-id)))))
