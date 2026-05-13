(ns hyperopen.wallet.agent-safety-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.trading :as trading-api]
            [hyperopen.wallet.agent-safety :as agent-safety]))

(defn- ready-store []
  (atom {:wallet {:connected? true
                  :address "0xowner"
                  :agent {:status :ready
                          :agent-address "0xagent"}}}))

(deftest install-agent-safety-classifies-volume-gate-and-stops-refresh-test
  (async done
    (let [store (ready-store)
          runtime (atom {:timeouts {:agent-schedule-cancel-refresh nil}})
          schedule-calls (atom [])
          timer-calls (atom [])
          original-schedule-cancel! trading-api/schedule-cancel!]
      (set! trading-api/schedule-cancel!
            (fn [_store address cancel-at-ms]
              (swap! schedule-calls conj [address cancel-at-ms])
              (js/Promise.resolve
               {:status "err"
                :response "Cannot set scheduled cancel time until enough volume traded. Required: $1000000. Traded: $890168.23."})))
      (agent-safety/install-agent-safety-watch!
       {:store store
        :runtime runtime
        :ahead-ms 60000
        :refresh-ms 30000
        :now-ms-fn (constantly 1000)
        :set-timeout-fn (fn [callback delay-ms]
                          (swap! timer-calls conj [callback delay-ms])
                          :timer-id)})
      (js/setTimeout
       (fn []
         (try
           (is (= [["0xowner" 61000]] @schedule-calls))
           (is (= [] @timer-calls))
           (is (= {:status :unavailable
                   :reason :volume-gate
                   :required "$1,000,000"
                   :traded "$890,168.23"
                   :message "Safety auto-cancel unavailable until $1,000,000 traded. Current volume: $890,168.23."}
                  (get-in @store [:wallet :agent :safety])))
           (finally
             (set! trading-api/schedule-cancel! original-schedule-cancel!)
             (agent-safety/stop-agent-safety! {:runtime runtime
                                               :clear-timeout-fn (fn [_])})
             (done))))
       0))))
