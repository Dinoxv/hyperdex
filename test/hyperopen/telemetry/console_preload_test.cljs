(ns hyperopen.telemetry.console-preload-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [nexus.registry :as nxr]
            [hyperopen.system :as app-system]
            [hyperopen.telemetry.console-preload :as console-preload]))

(deftest console-preload-installs-debug-global-in-debug-build-test
  (let [api (aget js/globalThis "HYPEROPEN_DEBUG")]
    (is (some? api))
    (is (fn? (aget api "registeredActionIds")))
    (is (fn? (aget api "dispatch")))))

(deftest registered-action-ids-api-returns-stable-string-ids-test
  (let [api (@#'console-preload/debug-api)
        ids ((aget api "registeredActionIds"))
        ids* (js->clj ids)]
    (is (array? ids))
    (is (some #{":actions/start-spectate-mode"} ids*))
    (is (some #{":actions/stop-spectate-mode"} ids*))
    (is (= ids* (sort ids*)))))

(deftest dispatch-api-normalizes-supported-action-id-strings-and-delegates-test
  (let [store (atom {:account-context {}})
        dispatched (atom [])]
    (with-redefs [app-system/store store
                  nxr/dispatch (fn [runtime-store event actions]
                                 (swap! dispatched conj [runtime-store event actions]))]
      (let [api (@#'console-preload/debug-api)
            dispatch! (aget api "dispatch")
            start-result (dispatch! #js [":actions/start-spectate-mode" "0xabc"])
            stop-result (dispatch! #js ["actions/stop-spectate-mode"])]
        (is (= [[store nil [[:actions/start-spectate-mode "0xabc"]]]
                [store nil [[:actions/stop-spectate-mode]]]]
               @dispatched))
        (is (= {:dispatched true
                :actionId ":actions/start-spectate-mode"
                :argCount 1}
               (js->clj start-result :keywordize-keys true)))
        (is (= {:dispatched true
                :actionId ":actions/stop-spectate-mode"
                :argCount 0}
               (js->clj stop-result :keywordize-keys true)))))))

(deftest dispatch-api-rejects-malformed-and-unregistered-actions-test
  (let [api (@#'console-preload/debug-api)
        dispatch! (aget api "dispatch")]
    (testing "non-array inputs fail fast"
      (is (thrown-with-msg?
           js/Error
           #"expected an action vector"
           (dispatch! "not-an-action-vector"))))
    (testing "empty vectors fail fast"
      (is (thrown-with-msg?
           js/Error
           #"expected an action vector"
           (dispatch! #js []))))
    (testing "unknown action ids are rejected"
      (is (thrown-with-msg?
           js/Error
           #"unregistered action id"
           (dispatch! #js [":actions/not-real"]))))))
