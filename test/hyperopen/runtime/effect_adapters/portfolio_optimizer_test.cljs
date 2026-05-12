(ns hyperopen.runtime.effect-adapters.portfolio-optimizer-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.runtime.effect-adapters.portfolio-optimizer :as portfolio-optimizer-adapters]))

(deftest facade-portfolio-optimizer-adapter-delegates-to-owner-module-test
  (is (identical? portfolio-optimizer-adapters/run-portfolio-optimizer-effect
                  effect-adapters/run-portfolio-optimizer-effect))
  (is (identical? portfolio-optimizer-adapters/run-portfolio-optimizer-pipeline-effect
                  effect-adapters/run-portfolio-optimizer-pipeline-effect))
  (is (identical? portfolio-optimizer-adapters/load-portfolio-optimizer-history-effect
                  effect-adapters/load-portfolio-optimizer-history-effect))
  (is (identical? portfolio-optimizer-adapters/save-portfolio-optimizer-scenario-effect
                  effect-adapters/save-portfolio-optimizer-scenario-effect))
  (is (identical? portfolio-optimizer-adapters/load-portfolio-optimizer-scenario-index-effect
                  effect-adapters/load-portfolio-optimizer-scenario-index-effect))
  (is (identical? portfolio-optimizer-adapters/load-portfolio-optimizer-scenario-effect
                  effect-adapters/load-portfolio-optimizer-scenario-effect))
  (is (identical? portfolio-optimizer-adapters/archive-portfolio-optimizer-scenario-effect
                  effect-adapters/archive-portfolio-optimizer-scenario-effect))
  (is (identical? portfolio-optimizer-adapters/duplicate-portfolio-optimizer-scenario-effect
                  effect-adapters/duplicate-portfolio-optimizer-scenario-effect))
  (is (identical? portfolio-optimizer-adapters/execute-portfolio-optimizer-plan-effect
                  effect-adapters/execute-portfolio-optimizer-plan-effect))
  (is (identical? portfolio-optimizer-adapters/refresh-portfolio-optimizer-tracking-effect
                  effect-adapters/refresh-portfolio-optimizer-tracking-effect))
  (is (identical? portfolio-optimizer-adapters/enable-portfolio-optimizer-manual-tracking-effect
                  effect-adapters/enable-portfolio-optimizer-manual-tracking-effect)))

(deftest run-portfolio-optimizer-effect-calls-run-bridge-with-runtime-store-test
  (let [calls (atom [])
        store (atom {})
        request {:scenario-id "scenario-1"}
        signature {:scenario-id "scenario-1" :revision 1}]
    (with-redefs [portfolio-optimizer-adapters/*request-run!*
                  (fn [payload]
                    (swap! calls conj payload)
                    "run-1")]
      (is (= "run-1"
             (portfolio-optimizer-adapters/run-portfolio-optimizer-effect
              :ctx
              store
              request
              signature
              {:computed-at-ms 123})))
      (is (= [{:request request
               :request-signature signature
               :computed-at-ms 123
               :store store}]
             @calls)))))
