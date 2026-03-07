(ns hyperopen.funding.application.modal-vm-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.funding.application.modal-vm :as modal-vm]))

(defn- non-blank-text
  [value]
  (when (and (string? value)
             (not (str/blank? value)))
    value))

(defn- base-deps
  ([] (base-deps {}))
  ([overrides]
   (merge {:modal-state (fn [state] (:modal state))
           :normalize-mode identity
           :normalize-hyperunit-lifecycle (fn [value]
                                            (merge {:direction nil
                                                    :asset-key nil
                                                    :state nil
                                                    :status nil}
                                                   value))
           :normalize-deposit-step #(or % :asset-select)
           :deposit-assets-filtered (fn [state _modal] (:deposit-assets state))
           :deposit-asset (fn [state _modal] (:deposit-asset state))
           :withdraw-assets (fn [state] (:withdraw-assets state))
           :withdraw-asset (fn [state _modal] (:withdraw-asset state))
           :deposit-asset-implemented? (fn [asset] (true? (:implemented? asset)))
           :normalize-deposit-asset-key identity
           :non-blank-text non-blank-text
           :preview (fn [state _modal] (:preview-result state))
           :normalize-hyperunit-fee-estimate (fn [value]
                                               (merge {:status :ready
                                                       :by-chain {}
                                                       :error nil}
                                                      value))
           :normalize-hyperunit-withdrawal-queue (fn [value]
                                                   (merge {:status :ready
                                                           :by-chain {}
                                                           :error nil}
                                                          value))
           :hyperunit-source-chain (fn [asset] (:source-chain asset))
           :hyperunit-fee-entry (fn [estimate chain] (get-in estimate [:by-chain chain]))
           :hyperunit-withdrawal-queue-entry (fn [queue chain] (get-in queue [:by-chain chain]))
           :hyperunit-explorer-tx-url (fn [direction chain tx-id]
                                        (when tx-id
                                          (str "https://explorer/"
                                               (name direction)
                                               "/"
                                               (or chain "hyperliquid")
                                               "/"
                                               tx-id)))
           :hyperunit-lifecycle-terminal? (fn [lifecycle]
                                            (contains? #{:completed :terminal}
                                                       (:status lifecycle)))
           :hyperunit-lifecycle-failure? (fn [lifecycle] (= :failed (:state lifecycle)))
           :hyperunit-lifecycle-recovery-hint (fn [lifecycle] (:recovery-hint lifecycle))
           :estimate-fee-display (fn [amount chain]
                                   (when amount
                                     (str amount "@" chain)))
           :transfer-max-amount (fn [state _modal] (:transfer-max state))
           :withdraw-max-amount (fn [_state asset] (:max asset))
           :withdraw-minimum-amount (fn [asset] (:min asset))
           :format-usdc-display str
           :format-usdc-input str
           :deposit-quick-amounts [5 10 25]
           :deposit-min-usdc 5
           :withdraw-min-usdc 5}
          overrides)))

(defn- deposit-asset
  [& {:keys [key symbol flow-kind source-chain minimum implemented?]
      :or {key :btc
           symbol "BTC"
           flow-kind :hyperunit-address
           source-chain "bitcoin"
           minimum 0.0001
           implemented? true}}]
  {:key key
   :symbol symbol
   :flow-kind flow-kind
   :source-chain source-chain
   :minimum minimum
   :implemented? implemented?})

(defn- withdraw-asset
  [& {:keys [key symbol flow-kind source-chain min max]
      :or {key :usdc
           symbol "USDC"
           flow-kind :evm-address
           source-chain "ethereum"
           min 5
           max 100}}]
  {:key key
   :symbol symbol
   :flow-kind flow-kind
   :source-chain source-chain
   :min min
   :max max})

(defn- base-state
  ([] (base-state {}))
  ([{:keys [modal] :as overrides}]
   (let [default-state {:modal {:open? true
                                :mode :deposit
                                :deposit-step :amount-entry
                                :to-perp? true
                                :amount-input ""
                                :destination-input ""}
                        :deposit-assets [(deposit-asset)]
                        :deposit-asset (deposit-asset)
                        :withdraw-assets [(withdraw-asset)]
                        :withdraw-asset (withdraw-asset)
                        :preview-result {:ok? true
                                         :display-message nil}
                        :transfer-max 55}]
     (-> default-state
         (update :modal merge modal)
         (merge (dissoc overrides :modal))))))

(deftest funding-modal-view-model-directly-exposes-generated-address-state-test
  (let [state (base-state {:modal {:deposit-generated-asset-key :btc
                                   :deposit-generated-address "bc1generated"
                                   :deposit-generated-signatures ["sig-a" "sig-b"]}})
        view-model (modal-vm/funding-modal-view-model (base-deps) state)]
    (is (= :deposit/address (get-in view-model [:content :kind])))
    (is (= "bc1generated" (:deposit-generated-address view-model)))
    (is (= "Regenerate address" (:deposit-submit-label view-model)))
    (is (= 2 (get-in view-model [:deposit :flow :generated-signature-count])))
    (is (= "bc1generated"
           (get-in view-model [:deposit :flow :generated-address])))))

(deftest funding-modal-view-model-hides-preview-feedback-before-deposit-amount-entry-test
  (let [state (base-state {:modal {:deposit-step :asset-select}
                           :deposit-asset nil
                           :preview-result {:ok? false
                                            :display-message "Enter a valid amount."}})
        view-model (modal-vm/funding-modal-view-model (base-deps) state)]
    (is (= :deposit/select (get-in view-model [:content :kind])))
    (is (nil? (:status-message view-model)))
    (is (not (get-in view-model [:feedback :visible?])))
    (is (= true (:submit-disabled? view-model)))))

(deftest funding-modal-view-model-builds-withdraw-lifecycle-and-queue-models-test
  (let [state (base-state {:modal {:mode :withdraw
                                   :amount-input "0.25"
                                   :destination-input "bc1qexample"
                                   :hyperunit-lifecycle {:direction :withdraw
                                                         :asset-key :btc
                                                         :state :failed
                                                         :status :terminal
                                                         :position-in-withdraw-queue 4
                                                         :destination-tx-hash "tx-123"
                                                         :recovery-hint "Retry from the activity panel."}
                                   :hyperunit-fee-estimate {:status :ready
                                                            :by-chain {"bitcoin" {:withdrawal-eta "~20 mins"
                                                                                  :withdrawal-fee "0.00001"}}}
                                   :hyperunit-withdrawal-queue {:status :ready
                                                                :by-chain {"bitcoin" {:withdrawal-queue-length 9
                                                                                      :last-withdraw-queue-operation-tx-id
                                                                                      "queue-123"}}}}
                           :withdraw-assets [(withdraw-asset :key :btc
                                                             :symbol "BTC"
                                                             :flow-kind :hyperunit-address
                                                             :source-chain "bitcoin"
                                                             :min 0.0003
                                                             :max 1.25)]
                           :withdraw-asset (withdraw-asset :key :btc
                                                           :symbol "BTC"
                                                           :flow-kind :hyperunit-address
                                                           :source-chain "bitcoin"
                                                           :min 0.0003
                                                           :max 1.25)})
        view-model (modal-vm/funding-modal-view-model (base-deps) state)]
    (is (= :withdraw/form (get-in view-model [:content :kind])))
    (is (= :failure (:hyperunit-lifecycle-outcome view-model)))
    (is (= "Needs Attention" (:hyperunit-lifecycle-outcome-label view-model)))
    (is (= "Retry from the activity panel."
           (:hyperunit-lifecycle-recovery-hint view-model)))
    (is (= "https://explorer/withdraw/bitcoin/tx-123"
           (:hyperunit-lifecycle-destination-explorer-url view-model)))
    (is (= :ready
           (get-in view-model [:withdraw :flow :withdrawal-queue :state])))
    (is (= 9 (:withdraw-queue-length view-model)))
    (is (= "queue-123" (:withdraw-queue-last-operation-tx-id view-model)))
    (is (= "https://explorer/withdraw/bitcoin/queue-123"
           (:withdraw-queue-last-operation-explorer-url view-model)))
    (is (= "~20 mins" (:withdraw-estimated-time view-model)))
    (is (= "0.00001@bitcoin" (:withdraw-network-fee view-model)))))

(deftest funding-modal-view-model-direct-feedback-uses-preview-errors-for-withdrawals-test
  (let [state (base-state {:modal {:mode :withdraw
                                   :amount-input ""
                                   :destination-input ""}
                           :preview-result {:ok? false
                                            :display-message "Enter a valid amount."}})
        view-model (modal-vm/funding-modal-view-model (base-deps) state)]
    (is (= "Enter a valid amount." (:status-message view-model)))
    (is (= true (get-in view-model [:feedback :visible?])))
    (is (= true (:submit-disabled? view-model)))
    (is (= "Withdraw" (:submit-label view-model)))))

(deftest funding-modal-view-model-marks-unsupported-deposit-flows-test
  (let [state (base-state {:deposit-assets [(deposit-asset :flow-kind :route
                                                           :implemented? false)]
                           :deposit-asset (deposit-asset :flow-kind :route
                                                         :implemented? false)
                           :preview-result {:ok? false
                                            :display-message "Deposit routing unavailable."}})
        view-model (modal-vm/funding-modal-view-model (base-deps) state)]
    (is (= :deposit/unavailable (get-in view-model [:content :kind])))
    (is (= false (:deposit-flow-supported? view-model)))
    (is (= "Route-based bridge/swap flow will be implemented in the next milestone."
           (get-in view-model [:deposit :flow :unsupported-detail])))
    (is (= "Deposit unavailable"
           (get-in view-model [:deposit :actions :submit-label])))))
