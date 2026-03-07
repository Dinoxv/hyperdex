(ns hyperopen.views.funding-modal-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.funding.actions :as funding-actions]
            [hyperopen.views.funding-modal :as view]))

(defn- base-state
  []
  {:wallet {:address "0x1234567890abcdef1234567890abcdef12345678"}
   :spot {:clearinghouse-state {:balances [{:coin "USDC" :available "12.5" :total "12.5" :hold "0"}]}}
   :webdata2 {:clearinghouseState {:availableToWithdraw "8.5"
                                   :marginSummary {:accountValue "20"
                                                   :totalMarginUsed "11.5"}}}
   :funding-ui {:modal (funding-actions/default-funding-modal-state)}})

(defn- node-children
  [node]
  (if (map? (second node))
    (drop 2 node)
    (drop 1 node)))

(defn- find-first-node
  [node pred]
  (cond
    (vector? node)
    (let [children (node-children node)]
      (or (when (pred node) node)
          (some #(find-first-node % pred) children)))

    (seq? node)
    (some #(find-first-node % pred) node)

    :else nil))

(defn- collect-strings
  [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings (node-children node))
    (seq? node) (mapcat collect-strings node)
    :else []))

(deftest deposit-amount-content-renders-minimum-prefill-action-test
  (let [state (assoc-in (base-state)
                        [:funding-ui :modal]
                        {:open? true
                         :mode :deposit
                         :deposit-step :amount-entry
                         :deposit-selected-asset-key :usdc
                         :amount-input ""})
        view-node (view/funding-modal-view state)
        min-button (find-first-node view-node
                                    #(= [[:actions/set-funding-deposit-amount-to-minimum]]
                                        (get-in % [1 :on :click])))
        all-text (set (collect-strings view-node))]
    (is (some? min-button))
    (is (contains? all-text "MIN"))
    (is (not (contains? all-text "MAX")))))

(deftest deposit-flow-does-not-render-withdraw-lifecycle-panel-test
  (let [state (assoc-in (base-state)
                        [:funding-ui :modal]
                        {:open? true
                         :mode :deposit
                         :deposit-step :amount-entry
                         :deposit-selected-asset-key :btc
                         :hyperunit-lifecycle {:direction :withdraw
                                               :asset-key :btc
                                               :operation-id "op_btc_5"
                                               :state :queued
                                               :status :pending
                                               :source-tx-confirmations 1
                                               :destination-tx-confirmations nil
                                               :position-in-withdraw-queue 2
                                               :destination-tx-hash nil
                                               :state-next-at 1700000000000
                                               :last-updated-ms 1700000000000
                                               :error nil}})
        view-node (view/funding-modal-view state)
        deposit-step (find-first-node view-node #(= "funding-deposit-amount-step"
                                                    (get-in % [1 :data-role])))
        deposit-lifecycle (find-first-node view-node #(= "funding-deposit-lifecycle"
                                                         (get-in % [1 :data-role])))]
    (is (some? deposit-step))
    (is (nil? deposit-lifecycle))))
