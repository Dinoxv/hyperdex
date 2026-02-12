(ns hyperopen.startup.wiring-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.startup.wiring :as startup-wiring]
            [hyperopen.wallet.address-watcher :as address-watcher]))

(deftest reify-address-handler-implements-address-change-handler-contract-test
  (let [calls (atom [])
        handler (startup-wiring/reify-address-handler
                 (fn [new-address]
                   (swap! calls conj new-address))
                 "startup-handler")]
    (is (satisfies? address-watcher/IAddressChangeHandler handler))
    (is (= "startup-handler"
           (address-watcher/get-handler-name handler)))
    (address-watcher/on-address-changed handler nil "0xabc")
    (is (= ["0xabc"] @calls))))

(deftest install-address-handlers-injects-reify-and-default-handler-name-test
  (let [captured (atom nil)
        base-deps {:store (atom {})}
        bootstrap-fn (fn [_] nil)]
    (with-redefs [hyperopen.startup.composition/install-address-handlers!
                  (fn [deps]
                    (reset! captured deps)
                    :ok)]
      (is (= :ok
             (startup-wiring/install-address-handlers! base-deps bootstrap-fn)))
      (is (identical? bootstrap-fn (:bootstrap-account-data! @captured)))
      (is (= "startup-account-bootstrap-handler"
             (:address-handler-name @captured)))
      (is (fn? (:address-handler-reify @captured))))))

(deftest initialize-remote-data-streams-merges-callback-deps-test
  (let [captured (atom nil)
        install-fn (fn [] :install)
        start-fn (fn [] :start)
        schedule-fn (fn [] :schedule)]
    (with-redefs [hyperopen.startup.composition/initialize-remote-data-streams!
                  (fn [deps]
                    (reset! captured deps)
                    :ok)]
      (is (= :ok
             (startup-wiring/initialize-remote-data-streams!
              {:store (atom {})}
              {:install-address-handlers-fn install-fn
               :start-critical-bootstrap-fn start-fn
               :schedule-deferred-bootstrap-fn schedule-fn})))
      (is (identical? install-fn (:install-address-handlers! @captured)))
      (is (identical? start-fn (:start-critical-bootstrap! @captured)))
      (is (identical? schedule-fn (:schedule-deferred-bootstrap! @captured))))))
