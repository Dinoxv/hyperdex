(ns hyperopen.views.account-info-view-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.account-info-view :as view]))

(defn- class-values [class-attr]
  (cond
    (nil? class-attr) []
    (string? class-attr) (remove str/blank? (str/split class-attr #"\s+"))
    (sequential? class-attr) (mapcat class-values class-attr)
    :else []))

(defn- classes-from-tag [tag]
  (if (keyword? tag)
    (let [parts (str/split (name tag) #"\.")]
      (if (> (count parts) 1)
        (rest parts)
        []))
    []))

(defn- node-class-set [node]
  (let [attrs (when (and (vector? node) (map? (second node)))
                (second node))
        classes (concat (classes-from-tag (first node))
                        (class-values (:class attrs)))]
    (set classes)))

(defn- node-children [node]
  (if (map? (second node))
    (drop 2 node)
    (drop 1 node)))

(defn- direct-texts [node]
  (->> (node-children node)
       (filter string?)
       set))

(defn- find-first-node [node pred]
  (cond
    (vector? node)
    (let [children (node-children node)]
      (or (when (pred node) node)
          (some #(find-first-node % pred) children)))

    (seq? node)
    (some #(find-first-node % pred) node)

    :else nil))

(def sample-balance-row
  {:key "spot-0"
   :coin "USDC (Spot)"
   :total-balance 150.12
   :available-balance 120.45
   :usdc-value 150.12
   :pnl-value -1.2
   :pnl-pct -0.8
   :amount-decimals 2})

(def default-sort-state {:column nil :direction :asc})

(def sample-position-data
  {:position {:coin "HYPE"
              :leverage {:value 5}
              :szi "12.34"
              :positionValue "85081.58"
              :entryPx "34.51"
              :unrealizedPnl "-8206.13"
              :returnOnEquity "-0.088"
              :liquidationPx "12.10"
              :marginUsed "2400"
              :cumFunding {:allTime "10.0"}}})

(deftest balances-header-contrast-test
  (let [header-node (view/balance-table-header default-sort-state)
        sortable-node (view/sortable-balances-header "Coin" default-sort-state)
        sortable-right-node (view/sortable-balances-header "Total Balance" default-sort-state :right)
        non-sortable-node (view/non-sortable-header "Send")
        non-sortable-center-node (view/non-sortable-header "Send" :center)]
    (is (contains? (node-class-set header-node) "text-trading-text"))
    (is (contains? (node-class-set sortable-node) "text-trading-text"))
    (is (contains? (node-class-set sortable-node) "justify-start"))
    (is (contains? (node-class-set sortable-right-node) "justify-end"))
    (is (contains? (node-class-set sortable-node) "hover:text-trading-text"))
    (is (contains? (node-class-set non-sortable-node) "text-trading-text"))
    (is (contains? (node-class-set non-sortable-center-node) "justify-center"))))

(deftest balances-title-and-filter-contrast-test
  (let [content (view/balances-tab-content [sample-balance-row] false default-sort-state)
        title-node (find-first-node content #(contains? (direct-texts %) "Balances ("))
        filter-label-node (find-first-node content #(contains? (direct-texts %) "Hide Small Balances"))]
    (is (contains? (node-class-set title-node) "text-trading-text"))
    (is (contains? (node-class-set filter-label-node) "text-trading-text"))))

(deftest balance-row-primary-value-and-action-contrast-test
  (let [row-node (view/balance-row sample-balance-row)
        coin-node (find-first-node row-node #(contains? (direct-texts %) "USDC (Spot)"))
        send-button-node (find-first-node row-node #(contains? (direct-texts %) "Send"))
        transfer-button-node (find-first-node row-node #(contains? (direct-texts %) "Transfer"))]
    (is (contains? (node-class-set row-node) "text-trading-text"))
    (is (contains? (node-class-set coin-node) "font-semibold"))
    (is (contains? (node-class-set send-button-node) "text-trading-text"))
    (is (contains? (node-class-set transfer-button-node) "text-trading-text"))))

(deftest balance-pnl-color-and-placeholder-contrast-test
  (testing "pnl uses success/error colors and white placeholders"
    (let [positive (view/format-pnl 2.0 1.5)
          negative (view/format-pnl -3.0 -2.0)
          zero (view/format-pnl 0 0)
          missing (view/format-pnl nil nil)]
      (is (contains? (node-class-set positive) "text-success"))
      (is (contains? (node-class-set negative) "text-error"))
      (is (contains? (node-class-set zero) "text-trading-text"))
      (is (contains? (node-class-set missing) "text-trading-text")))))

(deftest balances-and-positions-values-use-semibold-weight-test
  (let [balance-row-node (view/balance-row sample-balance-row)
        position-row-node (view/position-row sample-position-data)
        balance-value-node (find-first-node balance-row-node
                                            #(let [classes (node-class-set %)]
                                               (and (contains? classes "text-right")
                                                    (contains? classes "font-semibold"))))
        position-value-node (find-first-node position-row-node
                                             #(let [classes (node-class-set %)]
                                                (and (contains? classes "text-right")
                                                     (contains? classes "font-semibold"))))]
    (is (some? balance-value-node))
    (is (some? position-value-node))))
