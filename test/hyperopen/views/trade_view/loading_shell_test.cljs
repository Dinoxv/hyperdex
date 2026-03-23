(ns hyperopen.views.trade-view.loading-shell-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.trade-modules :as trade-modules]
            [hyperopen.views.trade-view :as trade-view]
            [hyperopen.views.trade.test-support :as support]))

(deftest trade-view-chart-loading-shell-preserves-chart-host-geometry-test
  (with-redefs [trade-modules/render-trade-chart-view (constantly nil)]
    (let [view-node (support/with-viewport-width
                      1280
                      #(trade-view/trade-view (support/active-asset-state)))
          shell-node (support/find-by-parity-id view-node "trade-chart-module-shell")
          host-node (support/find-first-node shell-node
                                             #(contains? (support/node-class-set %) "trading-chart-host"))]
      (is (some? shell-node))
      (is (contains? (support/node-class-set shell-node) "w-full"))
      (is (contains? (support/node-class-set shell-node) "h-full"))
      (is (some? host-node))
      (is (contains? (support/node-class-set host-node) "min-h-[360px]"))
      (is (some #{"Loading Chart"} (support/collect-strings shell-node))))))

(deftest trade-view-chart-shell-fills-the-desktop-top-row-test
  (with-redefs [trade-modules/render-trade-chart-view (constantly nil)]
    (let [view-node (support/with-viewport-width
                      1280
                      #(trade-view/trade-view (support/active-asset-state)))
          chart-panel (support/find-by-parity-id view-node "trade-chart-panel")
          loading-shell (support/find-by-parity-id view-node "trade-chart-module-shell")
          shell-inner (nth loading-shell 2)
          chart-panel-classes (support/node-class-set chart-panel)
          shell-inner-classes (support/node-class-set shell-inner)]
      (is (some? chart-panel))
      (is (some? loading-shell))
      (is (contains? chart-panel-classes "lg:row-start-1"))
      (is (contains? chart-panel-classes "lg:col-start-1"))
      (is (contains? shell-inner-classes "h-full"))
      (is (contains? shell-inner-classes "flex"))
      (is (contains? shell-inner-classes "flex-col")))))
