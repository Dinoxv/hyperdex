(ns hyperopen.startup.watchers-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.platform :as platform]
            [hyperopen.startup.watchers :as watchers]))

(defn- base-store []
  {:websocket {}
   :websocket-ui {:reconnect-count 0}
   :asset-selector {:markets []}})

(deftest stream-runtime-health-watch-syncs-only-on-fingerprint-transition-test
  (let [store (atom (base-store))
        connection-state (atom {:status :disconnected})
        stream-runtime (atom {:health-fingerprint {:transport/state :disconnected}})
        sync-calls (atom [])]
    (with-redefs [platform/queue-microtask! (fn [f] (f))
                  platform/now-ms (constantly 1000)]
      (watchers/install-websocket-watchers!
       {:store store
        :connection-state connection-state
        :stream-runtime stream-runtime
        :append-diagnostics-event! (fn [& _] nil)
        :sync-websocket-health! (fn [runtime-store & {:as opts}]
                                  (swap! sync-calls conj {:runtime-store runtime-store
                                                          :opts opts}))
        :on-websocket-connected! (fn [] nil)
        :on-websocket-disconnected! (fn [] nil)})
      (swap! stream-runtime assoc :metrics {:market-coalesced 1})
      (is (empty? @sync-calls))
      (swap! stream-runtime assoc :health-fingerprint {:transport/state :connected})
      (is (= 1 (count @sync-calls)))
      (is (= {:projected-fingerprint {:transport/state :connected}}
             (:opts (first @sync-calls))))
      (swap! stream-runtime assoc :now-ms 2000)
      (is (= 1 (count @sync-calls))))))

(deftest connection-status-watch-forces-sync-only-on-status-transitions-test
  (let [store (atom (base-store))
        connection-state (atom {:status :disconnected
                                :attempt 0
                                :next-retry-at-ms nil
                                :last-close nil
                                :queue-size 0})
        stream-runtime (atom {:health-fingerprint nil})
        sync-calls (atom [])
        connected-calls (atom 0)
        disconnected-calls (atom 0)]
    (with-redefs [platform/queue-microtask! (fn [f] (f))
                  platform/now-ms (constantly 2000)]
      (watchers/install-websocket-watchers!
       {:store store
        :connection-state connection-state
        :stream-runtime stream-runtime
        :append-diagnostics-event! (fn [& _] nil)
        :sync-websocket-health! (fn [_ & {:as opts}]
                                  (swap! sync-calls conj opts))
        :on-websocket-connected! #(swap! connected-calls inc)
        :on-websocket-disconnected! #(swap! disconnected-calls inc)})
      (swap! connection-state assoc :attempt 1)
      (testing "Non-status updates do not force health sync"
        (is (empty? @sync-calls)))
      (swap! connection-state assoc :status :connected)
      (testing "Status transitions force sync and notify connected callback"
        (is (= [{:force? true}] @sync-calls))
        (is (= 1 @connected-calls))
        (is (= 0 @disconnected-calls))))))
