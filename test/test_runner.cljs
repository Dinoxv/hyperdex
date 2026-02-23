(ns test-runner
  (:require [test-runner-generated :as generated-runner]))

(defn run-all-tests
  "Run all test namespaces and return the results"
  []
  (generated-runner/run-generated-tests))

(defn -main
  "Entry point for test runner"
  []
  (println "\n=== Running Hyperopen Tests ===")
  (let [results (run-all-tests)]
    (println "\n=== Test Results ===")
    results))

;; Run tests when this namespace loads in test environment
(-main)
