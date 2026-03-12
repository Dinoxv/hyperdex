(ns hyperopen.system-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.router :as router]
            [hyperopen.system :as system]))

(deftest default-store-state-seeds-router-path-from-current-location-test
  (with-redefs [router/current-path (fn [] "/staking")]
    (is (= "/staking"
           (get-in (system/default-store-state) [:router :path])))))
