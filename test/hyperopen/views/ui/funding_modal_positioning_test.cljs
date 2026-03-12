(ns hyperopen.views.ui.funding-modal-positioning-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.ui.funding-modal-positioning :as funding-modal-positioning]))

(deftest resolve-modal-layout-uses-shared-anchor-selectors-and-divider-alignment-test
  (let [selectors (atom [])
        action-selector (str "[data-role='"
                             funding-modal-positioning/deposit-action-data-role
                             "']")
        panel-selector (str "[data-parity-id='"
                            funding-modal-positioning/trade-order-entry-panel-parity-id
                            "']")
        original-document (.-document js/globalThis)
        original-inner-width (.-innerWidth js/globalThis)
        original-inner-height (.-innerHeight js/globalThis)]
    (set! (.-document js/globalThis)
          #js {:querySelector (fn [selector]
                                (swap! selectors conj selector)
                                (case selector
                                  "[data-role='funding-action-deposit']"
                                  #js {:getBoundingClientRect
                                       (fn []
                                         #js {:left 1040
                                              :right 1180
                                              :top 620
                                              :bottom 660
                                              :width 140
                                              :height 40})}

                                  "[data-parity-id='trade-order-entry-panel']"
                                  #js {:getBoundingClientRect
                                       (fn []
                                         #js {:left 1120
                                              :right 1400
                                              :top 0
                                              :bottom 900
                                              :width 280
                                              :height 900})}

                                  nil))})
    (set! (.-innerWidth js/globalThis) 1440)
    (set! (.-innerHeight js/globalThis) 900)
    (try
      (let [layout (funding-modal-positioning/resolve-modal-layout {:mode :deposit})]
        (is (= [action-selector panel-selector] @selectors))
        (is (false? (:mobile-sheet? layout)))
        (is (true? (:anchored-popover? layout)))
        (is (= 1130 (get-in layout [:anchor :left])))
        (is (= 1130 (get-in layout [:anchor :right])))
        (is (= "672px" (get-in layout [:popover-style :left])))
        (is (= "448px" (get-in layout [:popover-style :width])))
        (is (nil? (:sheet-style layout))))
      (finally
        (set! (.-document js/globalThis) original-document)
        (set! (.-innerWidth js/globalThis) original-inner-width)
        (set! (.-innerHeight js/globalThis) original-inner-height)))))
