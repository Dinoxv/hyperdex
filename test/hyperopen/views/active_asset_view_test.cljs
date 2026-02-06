(ns hyperopen.views.active-asset-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.active-asset-view :as view]))

(defn- collect-strings [node]
  (cond
    (string? node) [node]
    (vector? node) (mapcat collect-strings node)
    (seq? node) (mapcat collect-strings node)
    :else []))

(defn- collect-path-ds [node]
  (cond
    (vector? node)
    (let [attrs (when (map? (second node)) (second node))
          d-value (:d attrs)
          children (if (map? (second node))
                     (drop 2 node)
                     (drop 1 node))]
      (concat (when d-value [d-value])
              (mapcat collect-path-ds children)))

    (seq? node)
    (mapcat collect-path-ds node)

    :else
    []))

(deftest active-asset-row-symbol-fallback-test
  (let [ctx-data {:coin "SOL"
                  :mark 87.0
                  :oracle 86.9
                  :change24h 1.2
                  :change24hPct 1.4
                  :volume24h 1000
                  :openInterest 100
                  :fundingRate 0.001}
        ;; Simulates malformed/partial market state missing display fields.
        market {:market-type :perp}
        view-node (view/active-asset-row ctx-data market {:visible-dropdown nil} {:asset-selector {:missing-icons #{}}})
        strings (set (collect-strings view-node))]
    (is (contains? strings "SOL"))))

(deftest active-asset-list-spot-id-market-resolution-fallback-test
  (let [full-state {:active-asset "@1"
                    :active-market nil
                    :asset-selector {:missing-icons #{}
                                     :market-by-key {"spot:@1" {:key "spot:@1"
                                                                 :coin "@1"
                                                                 :symbol "HYPE/USDC"
                                                                 :base "HYPE"
                                                                 :quote "USDC"
                                                                 :market-type :spot
                                                                 :mark 10.0
                                                                 :markRaw "10.0"
                                                                 :change24h 1.0
                                                                 :change24hPct 11.11
                                                                 :volume24h 100000.0}}}}
        view-node (view/active-asset-list {} {:visible-dropdown nil} full-state)
        strings (set (collect-strings view-node))]
    (is (contains? strings "HYPE/USDC"))
    (is (contains? strings "SPOT"))
    (is (not (contains? strings "Loading...")))))

(deftest asset-icon-spot-includes-chevron-test
  (let [spot-market {:key "spot:@1"
                     :coin "@1"
                     :symbol "HYPE/USDC"
                     :base "HYPE"
                     :market-type :spot}
        icon-node (view/asset-icon spot-market false #{})
        path-ds (set (collect-path-ds icon-node))]
    (is (contains? path-ds "M19 9l-7 7-7-7"))))
