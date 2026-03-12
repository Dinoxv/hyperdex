(ns hyperopen.router-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.router :as router]))

(deftest normalize-path-supports-deep-link-variants-test
  (is (= "/trade" (router/normalize-path nil)))
  (is (= "/trade" (router/normalize-path "")))
  (is (= "/trade" (router/normalize-path "/")))
  (is (= "/staking" (router/normalize-path "/staking")))
  (is (= "/staking" (router/normalize-path "staking")))
  (is (= "/staking" (router/normalize-path "/staking/?tab=validators#history")))
  (is (= "/staking" (router/normalize-path " https://example.com/staking?tab=validators ")))
  (is (= "/vaults/0xABCDEF"
         (router/normalize-path "  /vaults/0xABCDEF///  "))))

(deftest normalize-location-path-prefers-hash-route-when-pathname-is-root-test
  (is (= "/staking"
         (router/normalize-location-path "/" "#/staking")))
  (is (= "/staking"
         (router/normalize-location-path nil "#/staking?tab=validators")))
  (is (= "/portfolio"
         (router/normalize-location-path "/portfolio" "#/staking")))
  (is (= "/trade"
         (router/normalize-location-path "/" "#")))
  (is (= "/trade"
         (router/normalize-location-path nil nil))))
