(ns hyperopen.api.projections.portfolio-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api.projections.portfolio :as portfolio]))

(deftest portfolio-projections-track-loading-success-and-error-test
  (let [summary {:account {:equity "1000"}}
        state {:portfolio {:summary-by-key {:stale true}
                           :loading? false
                           :error "stale"}}
        loading (portfolio/begin-portfolio-load state)
        success (portfolio/apply-portfolio-success loading summary)
        empty-success (portfolio/apply-portfolio-success loading nil)
        failed (portfolio/apply-portfolio-error loading (js/Error. "portfolio-fail"))]
    (is (= true (get-in loading [:portfolio :loading?])))
    (is (nil? (get-in loading [:portfolio :error])))
    (is (= summary (get-in success [:portfolio :summary-by-key])))
    (is (= false (get-in success [:portfolio :loading?])))
    (is (nil? (get-in success [:portfolio :error])))
    (is (number? (get-in success [:portfolio :loaded-at-ms])))
    (is (= {} (get-in empty-success [:portfolio :summary-by-key])))
    (is (= false (get-in failed [:portfolio :loading?])))
    (is (= "Error: portfolio-fail" (get-in failed [:portfolio :error])))))
