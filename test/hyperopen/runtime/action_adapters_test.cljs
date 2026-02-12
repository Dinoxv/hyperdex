(ns hyperopen.runtime.action-adapters-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.platform :as platform]
            [hyperopen.runtime.action-adapters :as action-adapters]
            [hyperopen.wallet.agent-runtime :as agent-runtime]))

(deftest enable-agent-trading-injects-platform-now-ms-fn-test
  (let [captured-now-ms (atom nil)]
    (with-redefs [platform/now-ms (fn [] 4242)
                  agent-runtime/enable-agent-trading!
                  (fn [{:keys [now-ms-fn]}]
                    (reset! captured-now-ms (now-ms-fn))
                    nil)]
      (action-adapters/enable-agent-trading nil (atom {}) {}))
    (is (= 4242 @captured-now-ms))))
