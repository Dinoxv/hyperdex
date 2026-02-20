(ns hyperopen.views.account-info-view-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.views.account-info.test-support.fixtures :as fixtures]
            [hyperopen.views.account-info.test-support.hiccup :as hiccup]
            [hyperopen.views.account-info-view :as view]))

(deftest account-info-panel-uses-fixed-height-and-bounded-content-test
  (let [panel (view/account-info-panel fixtures/sample-account-info-state)
        panel-classes (hiccup/node-class-set panel)
        content-node (second (vec (hiccup/node-children panel)))
        content-classes (hiccup/node-class-set content-node)]
    (is (contains? panel-classes "h-96"))
    (is (contains? panel-classes "flex"))
    (is (contains? panel-classes "flex-col"))
    (is (contains? panel-classes "min-h-0"))
    (is (contains? panel-classes "overflow-hidden"))
    (is (contains? content-classes "flex-1"))
    (is (contains? content-classes "min-h-0"))
    (is (contains? content-classes "overflow-hidden"))))
