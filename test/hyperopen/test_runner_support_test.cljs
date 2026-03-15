(ns hyperopen.test-runner-support-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.test-runner-support :as support]))

(deftest exit-code-for-results-test
  (is (= 0
         (support/exit-code-for-results nil)))
  (is (= 0
         (support/exit-code-for-results {:fail 0
                                         :error 0})))
  (is (= 1
         (support/exit-code-for-results {:fail 1
                                         :error 0})))
  (is (= 1
         (support/exit-code-for-results {:fail 0
                                         :error 2}))))
