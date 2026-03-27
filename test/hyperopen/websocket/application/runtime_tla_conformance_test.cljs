(ns hyperopen.websocket.application.runtime-tla-conformance-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.websocket.application.runtime-reducer :as reducer]
            [hyperopen.websocket.application.runtime-tla-trace-fixtures :as fixtures]
            [hyperopen.websocket.domain.model :as model]
            [hyperopen.websocket.flight-recorder :as flight-recorder]))

(defn- reducer-step
  [state msg]
  (reducer/step {:calculate-retry-delay-ms (fn [_ _ _ _] 500)} state msg))

(defn- replay-trace
  [trace]
  (flight-recorder/replay-runtime-messages {:recording (fixtures/recording trace)
                                            :reducer reducer-step}))

(deftest stale-socket-trace-keeps-runtime-state-observationally-stable-test
  (let [{:keys [final-state trace]} (replay-trace fixtures/stale-socket-trace)]
    (testing "Stale decoded and parse-error events leave reducer-owned buffers and parse metrics unchanged"
      (is (empty? (get-in final-state [:market-coalesce :pending])))
      (is (= 0 (get-in final-state [:metrics :market-coalesced])))
      (is (= 0 (get-in final-state [:metrics :market-dispatched])))
      (is (= 0 (get-in final-state [:metrics :ingress-parse-errors]))))
    (testing "Stale events do not emit new market-flush or dead-letter effects"
      (is (not-any? #(= :fx/timer-set-timeout %)
                    (mapcat :effect-types trace)))
      (is (not-any? #(= :fx/dead-letter %)
                    (mapcat :effect-types trace))))))

(deftest replay-order-trace-preserves-sorted-subscription-then-queue-order-test
  (let [{:keys [trace final-state]} (replay-trace fixtures/replay-order-trace)
        sent-payloads (->> trace
                           first
                           :effects
                           (filter #(= :fx/socket-send (:fx/type %)))
                           (mapv :data))]
    (testing "Socket open replays subscriptions by domain key order before queued payloads"
      (is (= fixtures/expected-replay-order sent-payloads)))
    (testing "Replay drains the queue once the active socket opens"
      (is (empty? (:queue final-state)))
      (is (= :connected (:status final-state))))))

(deftest force-reconnect-trace-clears-market-pending-before-requesting-new-connect-test
  (let [{:keys [trace final-state]} (replay-trace fixtures/force-reconnect-trace)
        effect-types (mapcat :effect-types trace)]
    (testing "Force reconnect drops stale market pending data before the next socket generation"
      (is (empty? (get-in final-state [:market-coalesce :pending])))
      (is (false? (:market-flush-active? final-state))))
    (testing "Force reconnect tears down the old socket and requests a new connect"
      (is (some #{:fx/socket-detach-handlers} effect-types))
      (is (some #{:fx/socket-close} effect-types))
      (is (some #{:fx/socket-connect} effect-types)))))

(deftest seq-gap-resubscribe-trace-clears-gap-state-after-resubscribe-test
  (let [{:keys [final-state]} (replay-trace fixtures/seq-gap-resubscribe-trace)
        sub-key (model/subscription-key {:type "trades" :coin "BTC"})
        stream (get-in final-state [:streams sub-key])]
    (testing "Resubscribe clears reducer seq-gap metadata back to the modeled baseline"
      (is (nil? (:last-seq stream)))
      (is (false? (:seq-gap-detected? stream)))
      (is (= 0 (:seq-gap-count stream)))
      (is (nil? (:last-gap stream))))))

(deftest intentional-close-trace-suppresses-retry-after-active-socket-close-test
  (let [{:keys [final-state]} (replay-trace fixtures/intentional-close-trace)]
    (testing "Intentional close leaves runtime disconnected without scheduling retry"
      (is (= :disconnected (:status final-state)))
      (is (false? (:retry-timer-active? final-state)))
      (is (nil? (:next-retry-at-ms final-state))))))
