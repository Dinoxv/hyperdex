(ns hyperopen.views.asset-selector-view-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.views.asset-selector-view :as view]))

(def sample-markets
  [{:key "perp:BTC"
    :symbol "BTC-USDC"
    :coin "BTC"
    :base "BTC"
    :market-type :perp
    :category :crypto
    :hip3? false
    :mark 1
    :volume24h 10
    :change24hPct 1}
   {:key "perp:xyz:GOLD"
    :symbol "GOLD-USDC"
    :coin "xyz:GOLD"
    :base "GOLD"
    :market-type :perp
    :category :tradfi
    :hip3? true
    :mark 2
    :volume24h 20
    :change24hPct 2}
   {:key "spot:PURR/USDC"
    :symbol "PURR/USDC"
    :coin "PURR/USDC"
    :base "PURR"
    :market-type :spot
    :category :spot
    :hip3? false
    :mark 0.5
    :volume24h 5
    :change24hPct -1}])

(deftest filter-and-sort-assets-test
  (testing "strict search filters by prefix"
    (let [results (view/filter-and-sort-assets sample-markets "bt" :name :asc #{} false true :all)]
      (is (= 1 (count results)))
      (is (= "BTC-USDC" (:symbol (first results))))))

  (testing "favorites-only filter"
    (let [results (view/filter-and-sort-assets sample-markets "" :name :asc #{"perp:BTC"} true false :all)]
      (is (= 1 (count results)))
      (is (= "perp:BTC" (:key (first results))))))

  (testing "tab filter for spot"
    (let [results (view/filter-and-sort-assets sample-markets "" :name :asc #{} false false :spot)]
      (is (= 1 (count results)))
      (is (= :spot (:market-type (first results)))))))

(defn- collect-strings [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings node)
    (seq? node) (mapcat collect-strings node)
    :else []))

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

(defn- collect-all-classes [node]
  (cond
    (vector? node)
    (let [attrs (when (map? (second node)) (second node))
          children (if attrs (drop 2 node) (drop 1 node))]
      (concat (classes-from-tag (first node))
              (class-values (:class attrs))
              (mapcat collect-all-classes children)))

    (seq? node)
    (mapcat collect-all-classes node)

    :else []))

(defn- find-first-node [node pred]
  (cond
    (vector? node)
    (let [attrs (when (map? (second node)) (second node))
          children (if attrs (drop 2 node) (drop 1 node))]
      (or (when (pred node) node)
          (some #(find-first-node % pred) children)))

    (seq? node)
    (some #(find-first-node % pred) node)

    :else nil))

(defn- find-first-img-node [node]
  (find-first-node
    node
    (fn [candidate]
      (and (vector? candidate)
           (keyword? (first candidate))
           (str/starts-with? (name (first candidate)) "img")))))

(deftest asset-list-item-sub-cent-formatting-test
  (testing "last price renders adaptive decimals for tiny assets"
    (let [asset {:key "perp:PUMP"
                 :symbol "PUMP-USDC"
                 :coin "PUMP"
                 :base "PUMP"
                 :mark 0.002028
                 :markRaw "0.002028"
                 :volume24h 1000
                 :change24h -0.000329
                 :change24hPct -13.95
                 :fundingRate 0.001
                 :market-type :perp}
          hiccup (view/asset-list-item asset false #{} #{} #{})
          strings (collect-strings hiccup)
          rendered (set strings)]
      (is (contains? rendered "$0.002028"))
      (is (not (contains? rendered "$0.00"))))))

(deftest asset-selector-loading-state-test
  (let [base-props {:visible? true
                    :markets sample-markets
                    :selected-market-key "perp:BTC"
                    :search-term ""
                    :sort-by :name
                    :sort-direction :asc
                    :favorites #{}
                    :favorites-only? false
                    :strict? false
                    :active-tab :all
                    :missing-icons #{}}
        full-view (view/asset-selector-dropdown (assoc base-props :loading? true :phase :full))
        bootstrap-view (view/asset-selector-dropdown (assoc base-props :loading? true :phase :bootstrap))
        full-strings (set (collect-strings full-view))
        bootstrap-strings (set (collect-strings bootstrap-view))]
    (is (contains? full-strings "Loading markets..."))
    (is (contains? bootstrap-strings "Loading markets (bootstrap)..."))))

(deftest asset-list-item-applies-numeric-alignment-utilities-test
  (let [asset {:key "perp:SOL"
               :symbol "SOL-USDC"
               :coin "SOL"
               :base "SOL"
               :mark 101.55
               :markRaw "101.55"
               :volume24h 123456
               :change24h 2.2
               :change24hPct 1.3
               :fundingRate 0.0001
               :openInterest 99999
               :market-type :perp}
        row (view/asset-list-item asset false #{} #{} #{})
        classes (set (collect-all-classes row))]
    (is (contains? classes "num"))
    (is (contains? classes "num-right"))))

(deftest asset-list-item-hides-icon-until-load-and-wires-load-events-test
  (let [asset {:key "perp:BTC"
               :symbol "BTC-USDC"
               :coin "BTC"
               :base "BTC"
               :market-type :perp
               :mark 1
               :volume24h 10
               :change24hPct 1}
        row (view/asset-list-item asset false #{} #{} #{})
        img-node (find-first-img-node row)
        attrs (second img-node)
        classes (set (class-values (:class attrs)))]
    (is (some? img-node))
    (is (contains? classes "hidden"))
    (is (= [[:actions/mark-loaded-asset-icon "perp:BTC"]]
           (get-in attrs [:on :load])))
    (is (= [[:actions/mark-missing-asset-icon "perp:BTC"]]
           (get-in attrs [:on :error])))))

(deftest asset-list-item-shows-icon-after-load-test
  (let [asset {:key "perp:BTC"
               :symbol "BTC-USDC"
               :coin "BTC"
               :base "BTC"
               :market-type :perp
               :mark 1
               :volume24h 10
               :change24hPct 1}
        row (view/asset-list-item asset false #{} #{} #{"perp:BTC"})
        img-node (find-first-img-node row)
        attrs (second img-node)
        classes (set (class-values (:class attrs)))]
    (is (some? img-node))
    (is (not (contains? classes "hidden")))))
