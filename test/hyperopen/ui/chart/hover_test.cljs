(ns hyperopen.ui.chart.hover-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.ui.chart.hover :as hover]))

(deftest hover-index-from-pointer-maps-midpoints-and-clamps-test
  (is (= 2 (hover/hover-index-from-pointer 50 {:left 0 :width 100} 5)))
  (is (= 0 (hover/hover-index-from-pointer -20 {:left 0 :width 100} 5)))
  (is (= 4 (hover/hover-index-from-pointer 160 {:left 0 :width 100} 5)))
  (is (= 0 (hover/hover-index-from-pointer "999" {:left "10" :width "40"} "1.9"))))

(deftest hover-index-from-pointer-coerces-values-and-rejects-invalid-inputs-test
  (is (= 2 (hover/hover-index-from-pointer "25" {:left "10" :width "30"} "4.9")))
  (is (nil? (hover/hover-index-from-pointer 25 {:left 10 :width 0} 4)))
  (is (nil? (hover/hover-index-from-pointer 25 {:left 10 :width -5} 4)))
  (is (nil? (hover/hover-index-from-pointer "abc" {:left 10 :width 30} 4)))
  (is (nil? (hover/hover-index-from-pointer 25 {:left nil :width 30} 4)))
  (is (nil? (hover/hover-index-from-pointer 25 {:left 10 :width 30} nil)))
  (is (nil? (hover/hover-index-from-pointer 25 {:left 10 :width 30} 0)))
  (is (nil? (hover/hover-index-from-pointer 25 {:left 10 :width 30} -1))))

(deftest normalize-hover-index-floors-clamps-and-rejects-invalid-inputs-test
  (is (= 2 (hover/normalize-hover-index 2.9 5)))
  (is (= 0 (hover/normalize-hover-index -1 5)))
  (is (= 4 (hover/normalize-hover-index 99 5)))
  (is (= 1 (hover/normalize-hover-index "1.8" "5.9")))
  (is (nil? (hover/normalize-hover-index nil 5)))
  (is (nil? (hover/normalize-hover-index 2 nil)))
  (is (nil? (hover/normalize-hover-index "abc" 5)))
  (is (nil? (hover/normalize-hover-index 2 0))))
