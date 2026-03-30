(ns hyperopen.funding.application.lifecycle-polling-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.funding.application.lifecycle-polling :as lifecycle-polling]
            [hyperopen.funding.effects :as effects]
            [hyperopen.funding.test-support.effects :as effects-support]
            [hyperopen.test-support.async :as async-support]))

(defn- base-poll-store
  [mode]
  (atom {:funding-ui {:modal (effects-support/seed-modal mode)}}))

(defn- base-poll-opts
  [{:keys [direction
           store
           request-hyperunit-operations!
           request-hyperunit-withdrawal-queue!
           set-timeout-fn
           now-ms-fn
           on-terminal-lifecycle!
           install-lifecycle-poll-token!
           clear-lifecycle-poll-token!
           lifecycle-poll-token-active?
           modal-active-for-lifecycle?
           select-operation
           operation->lifecycle
           awaiting-lifecycle
           lifecycle-next-delay-ms
           hyperunit-lifecycle-terminal?
           fetch-hyperunit-withdrawal-queue!
           default-poll-delay-ms]}]
  {:store (or store (base-poll-store direction))
   :direction direction
   :wallet-address "0xabc"
   :asset-key :btc
   :protocol-address "bc1qprotocol"
   :destination-address "0xdestination"
   :base-url "https://api.hyperunit.xyz"
   :base-urls ["https://api.hyperunit.xyz"]
   :request-hyperunit-operations! (or request-hyperunit-operations!
                                      (fn [_opts]
                                        (js/Promise.resolve {:operations []})))
   :request-hyperunit-withdrawal-queue! (or request-hyperunit-withdrawal-queue!
                                            (fn [_opts] (js/Promise.resolve {:queue []})))
   :set-timeout-fn (or set-timeout-fn
                       (fn [_f _delay-ms] :timer-id))
   :now-ms-fn (or now-ms-fn
                  (fn [] 1700000000000))
   :runtime-error-message effects-support/fallback-runtime-error-message
   :on-terminal-lifecycle! on-terminal-lifecycle!
   :lifecycle-poll-key-fn (fn [_store direction* asset-key*]
                            [direction* asset-key*])
   :install-lifecycle-poll-token! (or install-lifecycle-poll-token!
                                      (fn [_poll-key _token] nil))
   :clear-lifecycle-poll-token! (or clear-lifecycle-poll-token!
                                    (fn [_poll-key _token] nil))
   :lifecycle-poll-token-active? (or lifecycle-poll-token-active?
                                     (fn [_poll-key _token] true))
   :modal-active-for-lifecycle? (or modal-active-for-lifecycle?
                                    (fn [_store _direction _asset-key _protocol-address] true))
   :normalize-hyperunit-lifecycle identity
   :select-operation (or select-operation
                         (fn [operations _opts]
                           (first operations)))
   :operation->lifecycle (or operation->lifecycle
                             (fn [operation direction* asset-key* now-ms]
                               {:operation-id (:operation-id operation)
                                :direction direction*
                                :asset-key asset-key*
                                :state (:state-key operation)
                                :status (:status operation)
                                :last-updated-ms now-ms}))
   :awaiting-lifecycle (or awaiting-lifecycle
                           (fn [direction* asset-key* now-ms]
                             {:direction direction*
                              :asset-key asset-key*
                              :state :awaiting
                              :status :pending
                              :last-updated-ms now-ms}))
   :lifecycle-next-delay-ms (or lifecycle-next-delay-ms
                                (fn [_now-ms _lifecycle] 2500))
   :hyperunit-lifecycle-terminal? (or hyperunit-lifecycle-terminal?
                                      (fn [lifecycle]
                                        (= :done (:state lifecycle))))
   :fetch-hyperunit-withdrawal-queue! (or fetch-hyperunit-withdrawal-queue!
                                          (fn [_opts] nil))
   :non-blank-text (fn [value]
                     (when-let [text (some-> value str .trim)]
                       (when (seq text)
                         text)))
   :default-poll-delay-ms (or default-poll-delay-ms 3000)})

(deftest start-hyperunit-lifecycle-polling-noops-when-required-inputs-are-missing-test
  (let [request-calls (atom 0)
        install-calls (atom 0)]
    (lifecycle-polling/start-hyperunit-lifecycle-polling!
     (assoc (base-poll-opts {:direction :deposit
                             :request-hyperunit-operations! (fn [_opts]
                                                              (swap! request-calls inc)
                                                              (js/Promise.resolve {:operations []}))
                             :install-lifecycle-poll-token! (fn [_poll-key _token]
                                                              (swap! install-calls inc))})
            :wallet-address "  "
            :asset-key nil))
    (is (zero? @request-calls))
    (is (zero? @install-calls))))

(deftest resolve-poll-runtime-selects-callables-and-defaults-test
  (let [resolve-poll-runtime @#'hyperopen.funding.application.lifecycle-polling/resolve-poll-runtime
        request-ops! (fn [_opts] :ops)
        request-queue! (fn [_opts] :queue)
        timeout! (fn [_f _delay-ms] :timer)
        now-ms!* (fn [] 42)
        terminal-callback! (fn [_lifecycle] :terminal)
        provided (resolve-poll-runtime {:request-hyperunit-operations! request-ops!
                                        :request-hyperunit-withdrawal-queue! request-queue!
                                        :set-timeout-fn timeout!
                                        :now-ms-fn now-ms!*
                                        :on-terminal-lifecycle! terminal-callback!})
        defaulted (resolve-poll-runtime {:request-hyperunit-operations! :not-a-fn
                                         :request-hyperunit-withdrawal-queue! nil
                                         :set-timeout-fn nil
                                         :now-ms-fn nil
                                         :on-terminal-lifecycle! "nope"})]
    (is (identical? request-ops! (:request-ops! provided)))
    (is (identical? request-queue! (:request-queue! provided)))
    (is (identical? timeout! (:timeout! provided)))
    (is (identical? now-ms!* (:now-ms!* provided)))
    (is (identical? terminal-callback! (:terminal-callback! provided)))
    (is (nil? (:request-ops! defaulted)))
    (is (nil? (:request-queue! defaulted)))
    (is (nil? (:terminal-callback! defaulted)))
    (is (fn? (:timeout! defaulted)))
    (is (fn? (:now-ms!* defaulted)))
    (is (number? ((:now-ms!* defaulted))))))

(deftest start-hyperunit-lifecycle-polling-clears-token-without-request-when-modal-inactive-test
  (let [request-calls (atom 0)
        install-calls (atom [])
        clear-calls (atom [])]
    (lifecycle-polling/start-hyperunit-lifecycle-polling!
     (base-poll-opts {:direction :deposit
                      :request-hyperunit-operations! (fn [_opts]
                                                       (swap! request-calls inc)
                                                       (js/Promise.resolve {:operations []}))
                      :install-lifecycle-poll-token! (fn [poll-key token]
                                                       (swap! install-calls conj [poll-key token]))
                      :clear-lifecycle-poll-token! (fn [poll-key token]
                                                     (swap! clear-calls conj [poll-key token]))
                      :modal-active-for-lifecycle? (fn [_store _direction _asset-key _protocol-address]
                                                     false)}))
    (is (zero? @request-calls))
    (is (= 1 (count @install-calls)))
    (is (= @install-calls @clear-calls))))

(deftest start-hyperunit-lifecycle-polling-ignores-stale-successes-after-token-turns-inactive-test
  (async done
    (let [store (base-poll-store :deposit)
          initial-lifecycle (get-in @store [:funding-ui :modal :hyperunit-lifecycle])
          token-active? (atom true)
          clear-calls (atom [])
          terminal-calls (atom [])
          scheduled-delays (atom [])]
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (base-poll-opts {:direction :deposit
                        :store store
                        :request-hyperunit-operations! (fn [_opts]
                                                         (reset! token-active? false)
                                                         (js/Promise.resolve
                                                          {:operations [{:operation-id "op-stale"
                                                                         :state-key :done
                                                                         :status :completed}]}))
                        :lifecycle-poll-token-active? (fn [_poll-key _token]
                                                        @token-active?)
                        :clear-lifecycle-poll-token! (fn [poll-key token]
                                                       (swap! clear-calls conj [poll-key token]))
                        :on-terminal-lifecycle! (fn [lifecycle]
                                                  (swap! terminal-calls conj lifecycle))
                        :set-timeout-fn (fn [_f delay-ms]
                                          (swap! scheduled-delays conj delay-ms))}))
      (js/setTimeout
       (fn []
         (is (= initial-lifecycle
                (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))
         (is (empty? @clear-calls))
         (is (empty? @terminal-calls))
         (is (empty? @scheduled-delays))
         (done))
       0))))

(deftest start-hyperunit-lifecycle-polling-terminal-success-updates-store-and-calls-terminal-callback-test
  (async done
    (let [store (base-poll-store :deposit)
          terminal-calls (atom [])
          clear-calls (atom [])
          scheduled-delays (atom [])]
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (base-poll-opts {:direction :deposit
                        :store store
                        :request-hyperunit-operations! (fn [_opts]
                                                         (js/Promise.resolve
                                                          {:operations [{:operation-id "op-1"
                                                                         :state-key :done
                                                                         :status :completed}]}))
                        :on-terminal-lifecycle! (fn [lifecycle]
                                                  (swap! terminal-calls conj lifecycle))
                        :clear-lifecycle-poll-token! (fn [poll-key token]
                                                       (swap! clear-calls conj [poll-key token]))
                        :set-timeout-fn (fn [_f delay-ms]
                                          (swap! scheduled-delays conj delay-ms))}))
      (js/setTimeout
       (fn []
         (is (= {:operation-id "op-1"
                 :direction :deposit
                 :asset-key :btc
                 :state :done
                 :status :completed
                 :last-updated-ms 1700000000000}
                (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))
         (is (= 1 (count @terminal-calls)))
         (is (= 1 (count @clear-calls)))
         (is (empty? @scheduled-delays))
         (done))
       0))))

(deftest start-hyperunit-lifecycle-polling-withdraw-pending-refreshes-queue-and-schedules-next-poll-test
  (async done
    (let [store (base-poll-store :withdraw)
          refresh-calls (atom [])
          scheduled-delays (atom [])]
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (base-poll-opts {:direction :withdraw
                        :store store
                        :request-hyperunit-operations! (fn [_opts]
                                                         (js/Promise.resolve {:operations []}))
                        :fetch-hyperunit-withdrawal-queue! (fn [opts]
                                                             (swap! refresh-calls conj opts))
                        :set-timeout-fn (fn [_f delay-ms]
                                          (swap! scheduled-delays conj delay-ms))
                        :select-operation (fn [_operations _opts] nil)}))
      (js/setTimeout
       (fn []
         (is (= {:direction :withdraw
                 :asset-key :btc
                 :state :awaiting
                 :status :pending
                 :last-updated-ms 1700000000000}
                (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))
         (is (= 1 (count @refresh-calls)))
         (is (= :btc (get-in (first @refresh-calls) [:expected-asset-key])))
         (is (= [2500] @scheduled-delays))
         (done))
       0))))

(deftest start-hyperunit-lifecycle-polling-error-path-preserves-previous-lifecycle-and-schedules-default-delay-test
  (async done
    (let [store (atom {:funding-ui {:modal (assoc (effects-support/seed-modal :deposit)
                                                  :hyperunit-lifecycle {:operation-id "prev-op"
                                                                        :state :pending
                                                                        :status :pending
                                                                        :retained true})}})
          scheduled-delays (atom [])]
      (lifecycle-polling/start-hyperunit-lifecycle-polling!
       (base-poll-opts {:direction :deposit
                        :store store
                        :request-hyperunit-operations! (fn [_opts]
                                                         (js/Promise.reject (js/Error. "boom")))
                        :set-timeout-fn (fn [_f delay-ms]
                                          (swap! scheduled-delays conj delay-ms))
                        :default-poll-delay-ms 3333}))
      (js/setTimeout
       (fn []
         (is (= {:operation-id "prev-op"
                 :state :pending
                 :status :pending
                 :retained true
                 :direction :deposit
                 :asset-key :btc
                 :last-updated-ms 1700000000000
                 :error "boom"}
                (get-in @store [:funding-ui :modal :hyperunit-lifecycle])))
         (is (= [3333] @scheduled-delays))
         (done))
       0))))

(deftest api-submit-funding-deposit-hyperunit-address-terminal-lifecycle-refreshes-user-data-test
  (async done
    (let [wallet-address "0xabc"
          deposit-address "bc1qexamplexyz"
          store (atom {:wallet {:address wallet-address}
                       :funding-ui {:modal (assoc (effects-support/seed-modal :deposit)
                                                  :deposit-step :amount-entry
                                                  :deposit-selected-asset-key :btc
                                                  :deposit-generated-address nil
                                                  :deposit-generated-signatures nil
                                                  :deposit-generated-asset-key nil)}})
          dispatches (atom [])
          timeout-calls (atom 0)]
      (-> (effects/api-submit-funding-deposit!
           {:store store
            :request {:action {:type "hyperunitGenerateDepositAddress"
                               :asset "btc"
                               :fromChain "bitcoin"
                               :network "Bitcoin"}}
            :submit-hyperunit-address-request! (fn [_store _address _action]
                                                 (js/Promise.resolve {:status "ok"
                                                                      :keep-modal-open? true
                                                                      :asset "btc"
                                                                      :deposit-address deposit-address
                                                                      :deposit-signatures [{:r "0x1"}]}))
            :request-hyperunit-operations! (fn [_opts]
                                             (js/Promise.resolve
                                              {:operations [{:operation-id "op_d1"
                                                             :asset "btc"
                                                             :protocol-address deposit-address
                                                             :destination-address wallet-address
                                                             :state-key :done
                                                             :status "completed"}]}))
            :set-timeout-fn (fn [_f _delay-ms]
                              (swap! timeout-calls inc)
                              :timer-id)
            :dispatch! (effects-support/capture-dispatch! dispatches)})
          (.then (fn [_resp]
                   (js/setTimeout
                    (fn []
                      (is (= [[[[:actions/load-user-data wallet-address]]]]
                             (mapv (fn [[_store event]] [event]) @dispatches)))
                      (is (= 0 @timeout-calls))
                      (is (= :done
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :state])))
                      (done))
                    0)))
          (.catch (async-support/unexpected-error done))))))

(deftest api-submit-funding-deposit-hyperunit-address-polls-and-updates-lifecycle-test
  (async done
    (let [wallet-address "0x1234567890abcdef1234567890abcdef12345678"
          store (atom {:wallet {:address wallet-address}
                       :funding-ui {:modal (assoc (effects-support/seed-modal :deposit)
                                                  :deposit-step :amount-entry
                                                  :deposit-selected-asset-key :btc
                                                  :deposit-generated-address nil
                                                  :deposit-generated-signatures nil
                                                  :deposit-generated-asset-key nil)}})
          operation-calls (atom [])
          timeout-calls (atom 0)]
      (-> (effects/api-submit-funding-deposit!
           {:store store
            :request {:action {:type "hyperunitGenerateDepositAddress"
                               :asset "btc"
                               :fromChain "bitcoin"
                               :network "Bitcoin"}}
            :submit-hyperunit-address-request! (fn [_store _address _action]
                                                 (js/Promise.resolve {:status "ok"
                                                                      :keep-modal-open? true
                                                                      :asset "btc"
                                                                      :deposit-address "bc1qexamplexyz"
                                                                      :deposit-signatures [{:r "0x1"}]}))
            :request-hyperunit-operations! (fn [opts]
                                             (swap! operation-calls conj opts)
                                             (js/Promise.resolve
                                              {:operations [{:operation-id "op_123"
                                                             :asset "btc"
                                                             :protocol-address "bc1qexamplexyz"
                                                             :destination-address wallet-address
                                                             :state-key :done
                                                             :status "completed"
                                                             :source-tx-confirmations 6
                                                             :destination-tx-hash "0xabc"}]}))
            :set-timeout-fn (fn [_f _delay-ms]
                              (swap! timeout-calls inc)
                              :timer-id)})
          (.then (fn [_resp]
                   (js/setTimeout
                    (fn []
                      (is (= [{:base-url "https://api.hyperunit.xyz"
                               :base-urls ["https://api.hyperunit.xyz"]
                               :address wallet-address}]
                             @operation-calls))
                      (is (= :deposit
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :direction])))
                      (is (= :btc
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :asset-key])))
                      (is (= "op_123"
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :operation-id])))
                      (is (= :done
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :state])))
                      (is (= :completed
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :status])))
                      (is (= 0 @timeout-calls))
                      (done))
                    0)))
          (.catch (async-support/unexpected-error done))))))

(deftest api-submit-funding-deposit-hyperunit-address-schedules-next-poll-from-state-next-attempt-test
  (async done
    (let [wallet-address "0x1234567890abcdef1234567890abcdef12345678"
          now-ms 1700000000000
          state-next-at-ms (+ now-ms 4500)
          state-next-at-text (.toISOString (js/Date. state-next-at-ms))
          store (atom {:wallet {:address wallet-address}
                       :funding-ui {:modal (assoc (effects-support/seed-modal :deposit)
                                                  :deposit-step :amount-entry
                                                  :deposit-selected-asset-key :btc
                                                  :deposit-generated-address nil
                                                  :deposit-generated-signatures nil
                                                  :deposit-generated-asset-key nil)}})
          scheduled-delays (atom [])]
      (-> (effects/api-submit-funding-deposit!
           {:store store
            :request {:action {:type "hyperunitGenerateDepositAddress"
                               :asset "btc"
                               :fromChain "bitcoin"
                               :network "Bitcoin"}}
            :submit-hyperunit-address-request! (fn [_store _address _action]
                                                 (js/Promise.resolve {:status "ok"
                                                                      :keep-modal-open? true
                                                                      :asset "btc"
                                                                      :deposit-address "bc1qexamplexyz"
                                                                      :deposit-signatures [{:r "0x1"}]}))
            :request-hyperunit-operations! (fn [_opts]
                                             (js/Promise.resolve
                                              {:operations [{:operation-id "op_124"
                                                             :asset "btc"
                                                             :protocol-address "bc1qexamplexyz"
                                                             :destination-address wallet-address
                                                             :state-key :wait-for-src-tx-finalization
                                                             :status "pending"
                                                             :state-next-attempt-at state-next-at-text}]}))
            :set-timeout-fn (fn [_f delay-ms]
                              (swap! scheduled-delays conj delay-ms)
                              :timer-id)
            :now-ms-fn (fn [] now-ms)})
          (.then (fn [_resp]
                   (js/setTimeout
                    (fn []
                      (is (= [4500] @scheduled-delays))
                      (is (= :wait-for-src-tx-finalization
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :state])))
                      (is (= :pending
                             (get-in @store [:funding-ui :modal :hyperunit-lifecycle :status])))
                      (done))
                    0)))
          (.catch (async-support/unexpected-error done))))))
