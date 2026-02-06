(ns hyperopen.websocket.client-test
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [hyperopen.websocket.client :as ws-client]))

(defn- make-fake-socket [sent-payloads close-events]
  (let [socket (js-obj)]
    (aset socket "readyState" 0)
    (aset socket "send"
          (fn [payload]
            (swap! sent-payloads conj payload)))
    (aset socket "close"
          (fn [& [code reason]]
            (let [code* (or code 1000)
                  reason* (or reason "")]
              (swap! close-events conj {:code code* :reason reason*})
              (aset socket "readyState" 3)
              (when-let [onclose (aget socket "onclose")]
                (onclose (js-obj "code" code*
                                 "reason" reason*
                                 "wasClean" true))))))
    socket))

(defn- decode-payload [payload]
  (js->clj (js/JSON.parse payload) :keywordize-keys true))

(use-fixtures
  :each
  (fn [f]
    (ws-client/reset-manager-state!)
    (f)
    (ws-client/reset-manager-state!)))

(deftest init-connection-idempotent-test
  (let [created (atom [])
        sent (atom [])
        closes (atom [])]
    (with-redefs [hyperopen.websocket.client/create-websocket
                  (fn [ws-url]
                    (let [socket (make-fake-socket sent closes)]
                      (swap! created conj {:url ws-url :socket socket})
                      socket))
                  hyperopen.websocket.client/add-event-listener! (fn [& _] nil)
                  hyperopen.websocket.client/schedule-interval! (fn [& _] :watchdog)]
      (ws-client/init-connection! "wss://example.test/ws")
      (ws-client/init-connection! "wss://example.test/ws")
      (is (= 1 (count @created)))
      (is (= :connecting (:status @ws-client/connection-state))))))

(deftest reconnect-schedules-exponential-backoff-test
  (let [created (atom [])
        sent (atom [])
        closes (atom [])
        timeouts (atom [])
        now (atom 1000)]
    (with-redefs [hyperopen.websocket.client/create-websocket
                  (fn [ws-url]
                    (let [socket (make-fake-socket sent closes)]
                      (swap! created conj {:url ws-url :socket socket})
                      socket))
                  hyperopen.websocket.client/add-event-listener! (fn [& _] nil)
                  hyperopen.websocket.client/schedule-interval! (fn [& _] :watchdog)
                  hyperopen.websocket.client/schedule-timeout!
                  (fn [f delay-ms]
                    (swap! timeouts conj {:callback f :delay-ms delay-ms})
                    :retry-timer)
                  hyperopen.websocket.client/clear-timeout! (fn [& _] nil)
                  hyperopen.websocket.client/random-value (constantly 0.5)
                  hyperopen.websocket.client/now-ms (fn [] @now)]
      (ws-client/init-connection! "wss://example.test/ws")
      (let [socket (:socket (first @created))]
        ((aget socket "onclose") (js-obj "code" 1006 "reason" "abnormal" "wasClean" false)))
      (is (= :reconnecting (:status @ws-client/connection-state)))
      (is (= 1 (:attempt @ws-client/connection-state)))
      (is (= 1 (count @timeouts)))
      (is (= 500 (:delay-ms (first @timeouts))))
      (is (= 1500 (:next-retry-at-ms @ws-client/connection-state))))))

(deftest hidden-tab-retry-cap-is-higher-than-visible-cap-test
  (let [cfg {:base-delay-ms 500
             :backoff-multiplier 2
             :jitter-ratio 0.2
             :max-visible-delay-ms 15000
             :max-hidden-delay-ms 60000}
        visible (ws-client/calculate-retry-delay-ms 12 false cfg 0.5)
        hidden (ws-client/calculate-retry-delay-ms 12 true cfg 0.5)]
    (is (<= visible 15000))
    (is (<= hidden 60000))
    (is (> hidden visible))))

(deftest focus-and-online-trigger-reconnect-test
  (let [created (atom [])
        sent (atom [])
        closes (atom [])
        listeners (atom {})
        fake-window (js-obj)
        fake-document (js-obj "visibilityState" "hidden")]
    (with-redefs [hyperopen.websocket.client/window-object (fn [] fake-window)
                  hyperopen.websocket.client/document-object (fn [] fake-document)
                  hyperopen.websocket.client/create-websocket
                  (fn [ws-url]
                    (let [socket (make-fake-socket sent closes)]
                      (swap! created conj {:url ws-url :socket socket})
                      socket))
                  hyperopen.websocket.client/add-event-listener!
                  (fn [_ event-name handler]
                    (swap! listeners assoc event-name handler))
                  hyperopen.websocket.client/schedule-interval! (fn [& _] :watchdog)
                  hyperopen.websocket.client/clear-timeout! (fn [& _] nil)]
      (ws-client/init-connection! "wss://example.test/ws")
      (swap! ws-client/connection-state assoc :status :disconnected)
      (swap! ws-client/runtime-state
             assoc :socket nil :active-socket-id nil :intentional-close? false)

      ((get @listeners "focus") nil)
      (is (= 2 (count @created)))

      (swap! ws-client/connection-state assoc :status :disconnected)
      (swap! ws-client/runtime-state
             assoc :socket nil :active-socket-id nil :intentional-close? false)
      ((get @listeners "online") nil)
      (is (= 3 (count @created)))

      (swap! ws-client/connection-state assoc :status :disconnected)
      (swap! ws-client/runtime-state
             assoc :socket nil :active-socket-id nil :intentional-close? false)
      ((get @listeners "visibilitychange") nil)
      (is (= 3 (count @created)))
      (aset fake-document "visibilityState" "visible")
      ((get @listeners "visibilitychange") nil)
      (is (= 4 (count @created))))))

(deftest queued-messages-flush-in-fifo-order-on-open-test
  (let [created (atom [])
        sent (atom [])
        closes (atom [])]
    (with-redefs [hyperopen.websocket.client/create-websocket
                  (fn [ws-url]
                    (let [socket (make-fake-socket sent closes)]
                      (swap! created conj {:url ws-url :socket socket})
                      socket))
                  hyperopen.websocket.client/add-event-listener! (fn [& _] nil)
                  hyperopen.websocket.client/schedule-interval! (fn [& _] :watchdog)]
      (ws-client/init-connection! "wss://example.test/ws")
      (ws-client/send-message! {:type "alpha" :n 1})
      (ws-client/send-message! {:type "beta" :n 2})
      (is (= 2 (:queue-size @ws-client/connection-state)))
      (let [socket (:socket (first @created))]
        (aset socket "readyState" 1)
        ((aget socket "onopen") (js-obj)))
      (is (= :connected (:status @ws-client/connection-state)))
      (is (= 0 (:queue-size @ws-client/connection-state)))
      (is (= [{:type "alpha" :n 1}
              {:type "beta" :n 2}]
             (mapv decode-payload @sent))))))

(deftest desired-subscriptions-replay-after-reconnect-test
  (let [created (atom [])
        sent (atom [])
        closes (atom [])
        timeouts (atom [])]
    (with-redefs [hyperopen.websocket.client/create-websocket
                  (fn [ws-url]
                    (let [socket (make-fake-socket sent closes)]
                      (swap! created conj {:url ws-url :socket socket})
                      socket))
                  hyperopen.websocket.client/add-event-listener! (fn [& _] nil)
                  hyperopen.websocket.client/schedule-interval! (fn [& _] :watchdog)
                  hyperopen.websocket.client/schedule-timeout!
                  (fn [f delay-ms]
                    (swap! timeouts conj {:callback f :delay-ms delay-ms})
                    :retry-timer)
                  hyperopen.websocket.client/clear-timeout! (fn [& _] nil)
                  hyperopen.websocket.client/random-value (constantly 0.5)]
      (ws-client/init-connection! "wss://example.test/ws")
      (let [socket-1 (:socket (first @created))]
        (aset socket-1 "readyState" 1)
        ((aget socket-1 "onopen") (js-obj))
        (ws-client/send-message! {:method "subscribe"
                                  :subscription {:type "trades" :coin "BTC"}})
        (reset! sent [])
        ((aget socket-1 "onclose") (js-obj "code" 1006 "reason" "abnormal" "wasClean" false))
        ((:callback (first @timeouts)))
        (let [socket-2 (:socket (second @created))]
          (aset socket-2 "readyState" 1)
          ((aget socket-2 "onopen") (js-obj))))
      (is (some #(= {:method "subscribe"
                     :subscription {:type "trades" :coin "BTC"}}
                   %)
                (map decode-payload @sent))))))

(deftest watchdog-closes-stale-connection-and-retries-test
  (let [created (atom [])
        sent (atom [])
        closes (atom [])
        watchdog-callback (atom nil)
        timeouts (atom [])
        now (atom 200000)]
    (with-redefs [hyperopen.websocket.client/create-websocket
                  (fn [ws-url]
                    (let [socket (make-fake-socket sent closes)]
                      (swap! created conj {:url ws-url :socket socket})
                      socket))
                  hyperopen.websocket.client/add-event-listener! (fn [& _] nil)
                  hyperopen.websocket.client/schedule-interval!
                  (fn [f _]
                    (reset! watchdog-callback f)
                    :watchdog)
                  hyperopen.websocket.client/schedule-timeout!
                  (fn [f delay-ms]
                    (swap! timeouts conj {:callback f :delay-ms delay-ms})
                    :retry-timer)
                  hyperopen.websocket.client/clear-timeout! (fn [& _] nil)
                  hyperopen.websocket.client/now-ms (fn [] @now)]
      (ws-client/init-connection! "wss://example.test/ws")
      (let [socket (:socket (first @created))]
        (aset socket "readyState" 1)
        ((aget socket "onopen") (js-obj)))
      (swap! ws-client/connection-state assoc :last-activity-at-ms 0)
      (@watchdog-callback)
      (is (seq @closes))
      (is (= :reconnecting (:status @ws-client/connection-state)))
      (is (= 1 (count @timeouts))))))

(deftest disconnect-does-not-auto-reconnect-test
  (let [created (atom [])
        sent (atom [])
        closes (atom [])
        timeouts (atom [])]
    (with-redefs [hyperopen.websocket.client/create-websocket
                  (fn [ws-url]
                    (let [socket (make-fake-socket sent closes)]
                      (swap! created conj {:url ws-url :socket socket})
                      socket))
                  hyperopen.websocket.client/add-event-listener! (fn [& _] nil)
                  hyperopen.websocket.client/schedule-interval! (fn [& _] :watchdog)
                  hyperopen.websocket.client/schedule-timeout!
                  (fn [f delay-ms]
                    (swap! timeouts conj {:callback f :delay-ms delay-ms})
                    :retry-timer)
                  hyperopen.websocket.client/clear-timeout! (fn [& _] nil)]
      (ws-client/init-connection! "wss://example.test/ws")
      (let [socket (:socket (first @created))]
        (aset socket "readyState" 1)
        ((aget socket "onopen") (js-obj)))
      (ws-client/disconnect!)
      (is (= :disconnected (:status @ws-client/connection-state)))
      (is (empty? @timeouts)))))
