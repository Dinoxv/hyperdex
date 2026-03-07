(ns hyperopen.views.ui.anchored-popover-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.ui.anchored-popover :as anchored-popover]))

(deftest complete-anchor-no-longer-requires-bottom-test
  (is (true? (anchored-popover/complete-anchor? {:left 10
                                                 :right 30
                                                 :top 40})))
  (is (false? (anchored-popover/complete-anchor? {:left 10
                                                  :right 30}))))

(deftest anchored-popover-width-clamps-to-available-viewport-test
  (let [style (anchored-popover/anchored-popover-layout-style
               {:anchor {:left 280
                         :right 320
                         :top 48
                         :viewport-width 320
                         :viewport-height 640}
                :preferred-width-px 448
                :estimated-height-px 560})]
    (is (= "308px" (:width style)))
    (is (= "12px" (:left style)))))
