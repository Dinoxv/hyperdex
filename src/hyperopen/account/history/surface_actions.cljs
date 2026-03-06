(ns hyperopen.account.history.surface-actions
  (:require [clojure.string :as str]
            [hyperopen.account.history.shared :as history-shared]
            [hyperopen.platform :as platform]))

(def ^:private open-orders-sortable-columns
  #{"Time" "Type" "Coin" "Direction" "Size" "Original Size" "Order Value" "Price"})

(def ^:private open-orders-sort-directions
  #{:asc :desc})

(def ^:private open-orders-direction-filter-options
  #{:all :long :short})

(def ^:private positions-direction-filter-options
  #{:all :long :short})

(defn restore-open-orders-sort-settings! [store]
  (let [stored-column (or (platform/local-storage-get "open-orders-sort-by") "Time")
        stored-direction (keyword (or (platform/local-storage-get "open-orders-sort-direction") "desc"))
        column (if (contains? open-orders-sortable-columns stored-column)
                 stored-column
                 "Time")
        direction (if (contains? open-orders-sort-directions stored-direction)
                    stored-direction
                    :desc)]
    (swap! store update-in [:account-info] merge {:open-orders-sort {:column column
                                                                     :direction direction}})))

(defn- normalize-open-orders-direction-filter
  [direction-filter]
  (let [direction* (cond
                     (keyword? direction-filter) direction-filter
                     (string? direction-filter) (keyword (str/lower-case direction-filter))
                     :else :all)]
    (if (contains? open-orders-direction-filter-options direction*)
      direction*
      :all)))

(defn- normalize-positions-direction-filter
  [direction-filter]
  (let [direction* (cond
                     (keyword? direction-filter) direction-filter
                     (string? direction-filter) (keyword (str/lower-case direction-filter))
                     :else :all)]
    (if (contains? positions-direction-filter-options direction*)
      direction*
      :all)))

(defn sort-positions [state column]
  (let [current-sort (get-in state [:account-info :positions-sort])
        current-column (:column current-sort)
        current-direction (:direction current-sort)
        new-direction (if (and (= current-column column) (= current-direction :asc))
                        :desc
                        :asc)]
    [[:effects/save [:account-info :positions-sort] {:column column :direction new-direction}]]))

(defn sort-balances [state column]
  (let [current-sort (get-in state [:account-info :balances-sort])
        current-column (:column current-sort)
        current-direction (:direction current-sort)
        new-direction (if (and (= current-column column) (= current-direction :asc))
                        :desc
                        :asc)]
    [[:effects/save [:account-info :balances-sort] {:column column :direction new-direction}]]))

(defn sort-open-orders [state column]
  (let [current-sort (get-in state [:account-info :open-orders-sort])
        current-column (:column current-sort)
        current-direction (:direction current-sort)
        new-direction (if (= current-column column)
                        (if (= current-direction :asc) :desc :asc)
                        (if (contains? #{"Time" "Size" "Original Size" "Order Value" "Price"} column)
                          :desc
                          :asc))]
    [[:effects/save [:account-info :open-orders-sort] {:column column
                                                       :direction new-direction}]
     [:effects/local-storage-set "open-orders-sort-by" column]
     [:effects/local-storage-set "open-orders-sort-direction" (name new-direction)]]))

(defn toggle-open-orders-direction-filter-open [state]
  (let [open? (boolean (get-in state [:account-info :open-orders :filter-open?]))]
    [[:effects/save [:account-info :open-orders :filter-open?] (not open?)]]))

(defn set-open-orders-direction-filter [_state direction-filter]
  (let [direction* (normalize-open-orders-direction-filter direction-filter)]
    [[:effects/save-many [[[:account-info :open-orders :direction-filter] direction*]
                          [[:account-info :open-orders :filter-open?] false]]]]))

(defn toggle-positions-direction-filter-open [state]
  (let [open? (boolean (get-in state [:account-info :positions :filter-open?]))]
    [[:effects/save [:account-info :positions :filter-open?] (not open?)]]))

(defn set-positions-direction-filter [_state direction-filter]
  (let [direction* (normalize-positions-direction-filter direction-filter)]
    [[:effects/save-many [[[:account-info :positions :direction-filter] direction*]
                          [[:account-info :positions :filter-open?] false]]]]))

(defn set-account-info-coin-search [_state tab search-value]
  (let [tab* (history-shared/normalize-account-info-tab tab)
        search* (history-shared/normalize-coin-search-value search-value)]
    (case tab*
      :positions
      [[:effects/save [:account-info :positions :coin-search] search*]]

      :open-orders
      [[:effects/save [:account-info :open-orders :coin-search] search*]]

      :trade-history
      [[:effects/save-many [[[:account-info :trade-history :coin-search] search*]
                            [[:account-info :trade-history :page] 1]
                            [[:account-info :trade-history :page-input] "1"]]]]

      :order-history
      [[:effects/save-many [[[:account-info :order-history :coin-search] search*]
                            [[:account-info :order-history :page] 1]
                            [[:account-info :order-history :page-input] "1"]]]]

      [[:effects/save [:account-info :balances-coin-search] search*]])))

(defn set-hide-small-balances [_state checked]
  [[:effects/save [:account-info :hide-small-balances?] checked]])
