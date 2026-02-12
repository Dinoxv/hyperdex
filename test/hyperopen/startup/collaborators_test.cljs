(ns hyperopen.startup.collaborators-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.api :as api]
            [hyperopen.account.history.effects :as account-history-effects]
            [hyperopen.runtime.state :as runtime-state]
            [hyperopen.startup.collaborators :as collaborators]
            [hyperopen.websocket.client :as ws-client]))

(deftest startup-base-deps-includes-default-collaborators-test
  (let [deps (collaborators/startup-base-deps
              {:store :store
               :startup-runtime :startup-runtime})]
    (is (= :store (:store deps)))
    (is (= :startup-runtime (:startup-runtime deps)))
    (is (= runtime-state/websocket-url (:ws-url deps)))
    (is (fn? (:log-fn deps)))
    (is (identical? api/get-request-stats
                    (:get-request-stats deps)))
    (is (identical? account-history-effects/fetch-and-merge-funding-history!
                    (:fetch-and-merge-funding-history! deps)))
    (is (identical? ws-client/init-connection!
                    (:init-connection! deps)))))

(deftest startup-base-deps-allows-overriding-default-collaborators-test
  (let [log-fn* (fn [& _] nil)
        dispatch!* (fn [& _] nil)
        deps (collaborators/startup-base-deps
              {:log-fn log-fn*
               :ws-url "wss://example.test/ws"
               :dispatch! dispatch!*})]
    (is (identical? log-fn* (:log-fn deps)))
    (is (= "wss://example.test/ws" (:ws-url deps)))
    (is (identical? dispatch!* (:dispatch! deps)))))
