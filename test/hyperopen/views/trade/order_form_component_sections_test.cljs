(ns hyperopen.views.trade.order-form-component-sections-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.trade.order-form-component-sections :as sections]
            [hyperopen.views.trade.order-form-type-extensions :as type-extensions]))

(defn- collect-nodes-by-tag [node tag]
  (cond
    (vector? node)
    (let [attrs (when (map? (second node)) (second node))
          children (if attrs (drop 2 node) (drop 1 node))
          self (when (= tag (first node)) [node])]
      (into (or self [])
            (mapcat #(collect-nodes-by-tag % tag) children)))

    (seq? node)
    (mapcat #(collect-nodes-by-tag % tag) node)

    :else []))

(defn- collect-strings [node]
  (cond
    (string? node) [node]
    (vector? node)
    (let [attrs (when (map? (second node)) (second node))
          children (if attrs (drop 2 node) (drop 1 node))]
      (mapcat collect-strings children))
    (seq? node) (mapcat collect-strings node)
    :else []))

(defn- collect-input-placeholders [node]
  (->> (collect-nodes-by-tag node :input)
       (map second)
       (map :placeholder)
       (remove nil?)
       set))

(def ^:private entry-callbacks
  {:on-close-dropdown [[:actions/close-pro-order-type-dropdown]]
   :on-select-entry-market [[:actions/select-order-entry-mode :market]]
   :on-select-entry-limit [[:actions/select-order-entry-mode :limit]]
   :on-toggle-dropdown [[:actions/toggle-pro-order-type-dropdown]]
   :on-dropdown-keydown [[:actions/handle-pro-order-type-dropdown-keydown [:event/key]]]
   :on-select-pro-order-type (fn [order-type]
                               [[:actions/select-pro-order-type order-type]])})

(deftest entry-mode-tabs-renders-open-and-closed-dropdown-states-test
  (let [closed-node (sections/entry-mode-tabs {:entry-mode :limit
                                               :type :limit
                                               :pro-dropdown-open? false
                                               :pro-tab-label "Pro"
                                               :pro-dropdown-options [:scale :twap]
                                               :order-type-label name}
                                              entry-callbacks)
        open-node (sections/entry-mode-tabs {:entry-mode :pro
                                             :type :scale
                                             :pro-dropdown-open? true
                                             :pro-tab-label "Scale"
                                             :pro-dropdown-options [:scale :twap]
                                             :order-type-label name}
                                            entry-callbacks)
        closed-overlay-count (->> (collect-nodes-by-tag closed-node :div)
                                  (filter #(contains? (set (get-in % [1 :class])) "fixed"))
                                  count)
        open-overlays (->> (collect-nodes-by-tag open-node :div)
                           (filter #(contains? (set (get-in % [1 :class])) "fixed")))
        option-buttons (->> (collect-nodes-by-tag open-node :button)
                            (filter #(= :actions/select-pro-order-type
                                        (ffirst (get-in % [1 :on :click])))))
        selected-option-classes (set (get-in (first option-buttons) [1 :class]))
        unselected-option-classes (set (get-in (second option-buttons) [1 :class]))]
    (is (= 0 closed-overlay-count))
    (is (= 1 (count open-overlays)))
    (is (= [[:actions/close-pro-order-type-dropdown]]
           (get-in (first open-overlays) [1 :on :click])))

    (is (= 2 (count option-buttons)))
    (is (= [[:actions/select-pro-order-type :scale]]
           (get-in (first option-buttons) [1 :on :click])))
    (is (= [[:actions/select-pro-order-type :twap]]
           (get-in (second option-buttons) [1 :on :click])))
    (is (contains? selected-option-classes "bg-base-200"))
    (is (contains? unselected-option-classes "hover:bg-base-200"))))

(deftest tp-sl-panel-covers-enabled-and-market-branches-test
  (let [disabled-node (sections/tp-sl-panel {:tp {:enabled? false}
                                             :sl {:enabled? false}}
                                            {:on-toggle-tp-enabled [[:actions/tp-enabled]]
                                             :on-set-tp-trigger [[:actions/tp-trigger [:event.target/value]]]
                                             :on-toggle-tp-market [[:actions/tp-market]]
                                             :on-set-tp-limit [[:actions/tp-limit [:event.target/value]]]
                                             :on-toggle-sl-enabled [[:actions/sl-enabled]]
                                             :on-set-sl-trigger [[:actions/sl-trigger [:event.target/value]]]
                                             :on-toggle-sl-market [[:actions/sl-market]]
                                             :on-set-sl-limit [[:actions/sl-limit [:event.target/value]]]})
        enabled-node (sections/tp-sl-panel {:tp {:enabled? true
                                                 :trigger "3000"
                                                 :is-market false
                                                 :limit "3010"}
                                            :sl {:enabled? true
                                                 :trigger "2900"
                                                 :is-market true
                                                 :limit "2890"}}
                                           {:on-toggle-tp-enabled [[:actions/tp-enabled]]
                                            :on-set-tp-trigger [[:actions/tp-trigger [:event.target/value]]]
                                            :on-toggle-tp-market [[:actions/tp-market]]
                                            :on-set-tp-limit [[:actions/tp-limit [:event.target/value]]]
                                            :on-toggle-sl-enabled [[:actions/sl-enabled]]
                                            :on-set-sl-trigger [[:actions/sl-trigger [:event.target/value]]]
                                            :on-toggle-sl-market [[:actions/sl-market]]
                                            :on-set-sl-limit [[:actions/sl-limit [:event.target/value]]]})
        disabled-placeholders (collect-input-placeholders disabled-node)
        enabled-placeholders (collect-input-placeholders enabled-node)
        disabled-labels (set (collect-strings disabled-node))]
    (is (contains? disabled-labels "Enable TP"))
    (is (contains? disabled-labels "Enable SL"))
    (is (empty? disabled-placeholders))

    (is (contains? enabled-placeholders "TP trigger"))
    (is (contains? enabled-placeholders "TP limit price"))
    (is (contains? enabled-placeholders "SL trigger"))
    (is (not (contains? enabled-placeholders "SL limit price")))))

(deftest tif-inline-control-renders-current-value-and-change-handler-test
  (let [node (sections/tif-inline-control {:tif :ioc}
                                          {:on-set-tif [[:actions/update-order-form [:tif] [:event.target/value]]]})
        select-node (first (collect-nodes-by-tag node :select))]
    (is (= "ioc" (get-in select-node [1 :value])))
    (is (= [[:actions/update-order-form [:tif] [:event.target/value]]]
           (get-in select-node [1 :on :change])))))

(deftest section-module-delegates-to-type-extensions-test
  (with-redefs [type-extensions/render-order-type-sections
                (fn [order-type form callbacks]
                  [:delegated order-type form callbacks])
                type-extensions/supported-order-type-sections
                (fn [] #{:trigger :scale :twap})]
    (is (= [:delegated :scale {:x 1} {:on true}]
           (sections/render-order-type-sections :scale {:x 1} {:on true})))
    (is (= #{:trigger :scale :twap}
           (sections/supported-order-type-sections)))))
