(ns hyperopen.views.vault-detail-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.vault-detail-view :as vault-detail-view]))

(defn- node-children [node]
  (if (map? (second node))
    (drop 2 node)
    (drop 1 node)))

(defn- find-first-node [node pred]
  (cond
    (vector? node)
    (let [children (node-children node)]
      (or (when (pred node) node)
          (some #(find-first-node % pred) children)))

    (seq? node)
    (some #(find-first-node % pred) node)

    :else nil))

(defn- collect-strings [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings (node-children node))
    (seq? node) (mapcat collect-strings node)
    :else []))

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
                                  :relationship {:type :parent
                                                 :child-addresses ["0x9999999999999999999999999999999999999999"]}
                                  :follower-state {:vault-equity 50
                                                   :all-time-pnl 12}}}
            :webdata-by-vault {"0x1234567890abcdef1234567890abcdef12345678"
                               {:fills [{:time 3
                                         :coin "BTC"
                                         :side "buy"
                                         :sz "0.5"
                                         :px "101"}]}}
            :user-equity-by-address {"0x1234567890abcdef1234567890abcdef12345678"
                                     {:equity 50}}
            :merged-index-rows [{:name "Vault Detail"
                                 :vault-address "0x1234567890abcdef1234567890abcdef12345678"
                                 :leader "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
                                 :tvl 200
                                 :apr 0.2
                                 :snapshot-by-key {:month [0.1 0.2]
                                                   :all-time [0.5]}}]}})

(deftest vault-detail-view-renders-header-metrics-tabs-and-activity-test
  (let [view (vault-detail-view/vault-detail-view sample-state)
        root (find-first-node view #(= "vault-detail-root" (get-in % [1 :data-parity-id])))
        back-button (find-first-node view
                                     #(= [[:actions/navigate "/vaults"]]
                                         (get-in % [1 :on :click])))
        tab-button (find-first-node view
                                    #(= [[:actions/set-vault-detail-tab :vault-performance]]
                                        (get-in % [1 :on :click])))
        text (set (collect-strings view))]
    (is (some? root))
    (is (some? back-button))
    (is (some? tab-button))
    (is (contains? text "Vault Detail"))
    (is (contains? text "Past Month Return"))
    (is (contains? text "Account Activity"))))

(deftest vault-detail-view-shows-invalid-message-when-route-address-is-invalid-test
  (let [view (vault-detail-view/vault-detail-view (assoc-in sample-state [:router :path] "/vaults/not-an-address"))
        text (set (collect-strings view))]
    (is (contains? text "Invalid vault address."))))
