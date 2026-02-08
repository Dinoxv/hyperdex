(ns hyperopen.views.account-equity-view-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.account-equity-view :as view]))

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

(deftest account-equity-heading-and-label-contrast-test
  (let [view-node (view/account-equity-view {:webdata2 {}
                                             :spot {}
                                             :perp-dex-clearinghouse {}})
        title-node (find-first-node view-node #(contains? (direct-texts %) "Account Equity"))
        section-node (find-first-node view-node #(contains? (direct-texts %) "Perps Overview"))
        spot-label-node (find-first-node view-node #(contains? (direct-texts %) "Spot"))]
    (is (contains? (node-class-set title-node) "text-trading-text"))
    (is (contains? (node-class-set section-node) "text-trading-text"))
    (is (contains? (node-class-set section-node) "font-semibold"))
    (is (contains? (node-class-set spot-label-node) "text-trading-text-secondary"))))

(deftest metric-row-value-contrast-test
  (testing "default values are white and placeholders are muted"
    (let [value-node (last (view/metric-row "Balance" "$10.00"))
          placeholder-node (last (view/metric-row "Balance" "--"))]
      (is (contains? (node-class-set value-node) "text-trading-text"))
      (is (contains? (node-class-set placeholder-node) "text-trading-text-secondary")))))

(deftest pnl-display-color-mapping-test
  (let [positive (view/pnl-display 10.5)
        negative (view/pnl-display -2.25)
        zero (view/pnl-display 0)
        missing (view/pnl-display nil)]
    (is (= "text-success" (:class positive)))
    (is (= "text-error" (:class negative)))
    (is (= "text-trading-text" (:class zero)))
    (is (= "text-trading-text-secondary" (:class missing)))
    (is (= "--" (:text missing)))))
