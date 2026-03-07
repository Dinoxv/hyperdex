(ns hyperopen.funding.application.modal-vm
  (:require [clojure.string :as str]))

(defn- titleize-token
  [value fallback]
  (if-let [token (some-> value name (str/replace #"-" " "))]
    (let [words (->> (str/split token #"\s+")
                     (remove str/blank?)
                     (map str/capitalize))]
      (if (seq words)
        (str/join " " words)
        fallback))
    fallback))

(defn- lifecycle-next-check-label
  [next-at-ms]
  (when (number? next-at-ms)
    "Scheduled"))

(defn- fee-state
  [loading? error]
  (cond
    loading? :loading
    (seq error) :error
    :else :ready))

(defn- withdrawal-queue-state
  [loading? queue-length error]
  (cond
    loading? :loading
    (number? queue-length) :ready
    (seq error) :error
    :else :idle))

(defn- lifecycle-panel-model
  [lifecycle
   {:keys [selected-asset-key
           direction
           terminal?
           outcome
           outcome-label
           recovery-hint
           destination-explorer-url
           include-queue-position?]}]
  (when (and (= direction (:direction lifecycle))
             (= selected-asset-key (:asset-key lifecycle)))
    {:direction direction
     :stage-label (titleize-token (:state lifecycle)
                                  (if (= direction :withdraw)
                                    "Awaiting Hyperliquid Send"
                                    "Awaiting Source Transfer"))
     :status-label (titleize-token (:status lifecycle)
                                   "Pending")
     :outcome (when terminal?
                {:label (or outcome-label "Terminal")
                 :tone outcome})
     :source-confirmations (:source-tx-confirmations lifecycle)
     :destination-confirmations (:destination-tx-confirmations lifecycle)
     :queue-position (when include-queue-position?
                       (:position-in-withdraw-queue lifecycle))
     :destination-tx (when-let [tx-hash (:destination-tx-hash lifecycle)]
                       {:hash tx-hash
                        :explorer-url destination-explorer-url})
     :next-check-label (lifecycle-next-check-label (:state-next-at lifecycle))
     :error (:error lifecycle)
     :recovery-hint (when (and terminal?
                               (= outcome :failure)
                               (seq recovery-hint))
                      recovery-hint)}))

(defn- deposit-content-kind
  [deposit-step selected-asset flow-kind supported?]
  (cond
    (not= deposit-step :amount-entry) :deposit/select
    (nil? selected-asset) :deposit/missing-asset
    (not supported?) :deposit/unavailable
    (= flow-kind :hyperunit-address) :deposit/address
    :else :deposit/amount))

(defn- summary-row
  [label value]
  {:label label
   :value value})

(defn funding-modal-view-model
  [{:keys [modal-state
           normalize-mode
           normalize-hyperunit-lifecycle
           normalize-deposit-step
           deposit-assets-filtered
           deposit-asset
           withdraw-assets
           withdraw-asset
           deposit-asset-implemented?
           normalize-deposit-asset-key
           non-blank-text
           preview
           normalize-hyperunit-fee-estimate
           normalize-hyperunit-withdrawal-queue
           hyperunit-source-chain
           hyperunit-fee-entry
           hyperunit-withdrawal-queue-entry
           hyperunit-explorer-tx-url
           hyperunit-lifecycle-terminal?
           hyperunit-lifecycle-failure?
           hyperunit-lifecycle-recovery-hint
           estimate-fee-display
           transfer-max-amount
           withdraw-max-amount
           withdraw-minimum-amount
           format-usdc-display
           format-usdc-input
           deposit-quick-amounts
           deposit-min-usdc
           withdraw-min-usdc]}
   state]
  (let [modal (modal-state state)
        mode (normalize-mode (:mode modal))
        deposit? (= mode :deposit)
        legacy? (= mode :legacy)
        hyperunit-lifecycle (normalize-hyperunit-lifecycle (:hyperunit-lifecycle modal))
        deposit-step (normalize-deposit-step (:deposit-step modal))
        deposit-assets* (deposit-assets-filtered state modal)
        selected-deposit-asset (deposit-asset state modal)
        withdraw-assets* (withdraw-assets state)
        selected-withdraw-asset (withdraw-asset state modal)
        selected-withdraw-asset-key (:key selected-withdraw-asset)
        selected-withdraw-symbol (or (:symbol selected-withdraw-asset) "USDC")
        selected-withdraw-flow-kind (or (:flow-kind selected-withdraw-asset) :unknown)
        selected-deposit-asset-key (:key selected-deposit-asset)
        selected-deposit-flow-kind (or (:flow-kind selected-deposit-asset) :unknown)
        selected-deposit-implemented? (deposit-asset-implemented? selected-deposit-asset)
        generated-address-asset-key (normalize-deposit-asset-key (:deposit-generated-asset-key modal))
        generated-address-active? (and selected-deposit-asset-key
                                       (= generated-address-asset-key selected-deposit-asset-key))
        generated-address (when generated-address-active?
                            (non-blank-text (:deposit-generated-address modal)))
        generated-signatures (when generated-address-active?
                               (:deposit-generated-signatures modal))
        generated-signature-count (if (sequential? generated-signatures)
                                    (count generated-signatures)
                                    0)
        preview-result (preview state modal)
        preview-ok? (:ok? preview-result)
        hyperunit-fee-estimate (normalize-hyperunit-fee-estimate
                                (:hyperunit-fee-estimate modal))
        hyperunit-fee-estimate-loading? (= :loading
                                           (:status hyperunit-fee-estimate))
        hyperunit-fee-estimate-error (non-blank-text
                                      (:error hyperunit-fee-estimate))
        hyperunit-withdrawal-queue (normalize-hyperunit-withdrawal-queue
                                    (:hyperunit-withdrawal-queue modal))
        hyperunit-withdrawal-queue-loading? (= :loading
                                               (:status hyperunit-withdrawal-queue))
        hyperunit-withdrawal-queue-error (non-blank-text
                                          (:error hyperunit-withdrawal-queue))
        deposit-chain (hyperunit-source-chain selected-deposit-asset)
        withdraw-chain (hyperunit-source-chain selected-withdraw-asset)
        deposit-chain-fee (hyperunit-fee-entry hyperunit-fee-estimate deposit-chain)
        withdraw-chain-fee (hyperunit-fee-entry hyperunit-fee-estimate withdraw-chain)
        withdraw-chain-queue (hyperunit-withdrawal-queue-entry
                              hyperunit-withdrawal-queue
                              withdraw-chain)
        withdraw-queue-length (when (and (= selected-withdraw-flow-kind :hyperunit-address)
                                         (map? withdraw-chain-queue))
                                (:withdrawal-queue-length withdraw-chain-queue))
        withdraw-queue-last-operation-tx-id (when (and (= selected-withdraw-flow-kind :hyperunit-address)
                                                       (map? withdraw-chain-queue))
                                              (non-blank-text
                                               (:last-withdraw-queue-operation-tx-id
                                                withdraw-chain-queue)))
        withdraw-queue-last-operation-explorer-url (when (= selected-withdraw-flow-kind :hyperunit-address)
                                                     (hyperunit-explorer-tx-url
                                                      :withdraw
                                                      withdraw-chain
                                                      withdraw-queue-last-operation-tx-id))
        lifecycle-terminal? (hyperunit-lifecycle-terminal? hyperunit-lifecycle)
        lifecycle-outcome (when lifecycle-terminal?
                            (if (hyperunit-lifecycle-failure? hyperunit-lifecycle)
                              :failure
                              :success))
        lifecycle-outcome-label (case lifecycle-outcome
                                  :failure "Needs Attention"
                                  :success "Completed"
                                  nil)
        lifecycle-recovery-hint (when (= lifecycle-outcome :failure)
                                  (hyperunit-lifecycle-recovery-hint hyperunit-lifecycle))
        lifecycle-destination-explorer-url (hyperunit-explorer-tx-url
                                            (:direction hyperunit-lifecycle)
                                            (when (= :withdraw (:direction hyperunit-lifecycle))
                                              withdraw-chain)
                                            (:destination-tx-hash hyperunit-lifecycle))
        deposit-estimated-time (if (= selected-deposit-flow-kind :hyperunit-address)
                                 (or (when hyperunit-fee-estimate-loading? "Loading...")
                                     (non-blank-text (:deposit-eta deposit-chain-fee))
                                     "Depends on source confirmations")
                                 "~10 seconds")
        deposit-network-fee (if (= selected-deposit-flow-kind :hyperunit-address)
                              (or (when hyperunit-fee-estimate-loading? "Loading...")
                                  (estimate-fee-display (:deposit-fee deposit-chain-fee)
                                                        deposit-chain)
                                  "Paid on source chain")
                              "None")
        withdraw-estimated-time (if (= selected-withdraw-flow-kind :hyperunit-address)
                                  (or (when hyperunit-fee-estimate-loading? "Loading...")
                                      (non-blank-text (:withdrawal-eta withdraw-chain-fee))
                                      "Depends on destination chain")
                                  "~10 seconds")
        withdraw-network-fee (if (= selected-withdraw-flow-kind :hyperunit-address)
                               (or (when hyperunit-fee-estimate-loading? "Loading...")
                                   (estimate-fee-display (:withdrawal-fee withdraw-chain-fee)
                                                         withdraw-chain)
                                   "Paid on destination chain")
                               "None")
        transfer-max (transfer-max-amount state modal)
        withdraw-max (withdraw-max-amount state selected-withdraw-asset)
        withdraw-min-amount (withdraw-minimum-amount selected-withdraw-asset)
        max-amount (case mode
                     :transfer transfer-max
                     :withdraw withdraw-max
                     0)
        preview-message (:display-message preview-result)
        error (:error modal)
        deposit-step-amount-entry? (= deposit-step :amount-entry)
        status-message (or error
                           (when (and (not preview-ok?)
                                      (seq preview-message)
                                      (or (not= mode :deposit)
                                          deposit-step-amount-entry?))
                             preview-message))
        show-status-message? (and (seq status-message)
                                  (not legacy?)
                                  (not deposit?))
        submitting? (true? (:submitting? modal))
        submit-disabled? (or submitting?
                            (and deposit?
                                 (not deposit-step-amount-entry?))
                            (not preview-ok?))
        legacy-kind (or (:legacy-kind modal) :unknown)
        title (cond
                (and deposit?
                     deposit-step-amount-entry?
                     (string? (:symbol selected-deposit-asset)))
                (str "Deposit " (:symbol selected-deposit-asset))

                :else
                (case mode
                  :deposit "Deposit"
                  :transfer "Perps <-> Spot"
                  :withdraw "Withdraw"
                  :legacy (str/capitalize (name legacy-kind))
                  "Funding"))
        deposit-submit-label (if submitting?
                               (if (and deposit?
                                        (= selected-deposit-flow-kind :hyperunit-address))
                                 "Generating..."
                                 "Submitting...")
                               (if preview-ok?
                                 (if (and deposit?
                                          (= selected-deposit-flow-kind :hyperunit-address))
                                   (if (seq generated-address)
                                     "Regenerate address"
                                     "Generate address")
                                   "Deposit")
                                 (if (and deposit?
                                          (not selected-deposit-implemented?))
                                   "Deposit unavailable"
                                   (or preview-message "Enter a valid amount"))))
        deposit-content-kind* (deposit-content-kind deposit-step
                                                    selected-deposit-asset
                                                    selected-deposit-flow-kind
                                                    selected-deposit-implemented?)
        deposit-lifecycle (lifecycle-panel-model hyperunit-lifecycle
                                                 {:selected-asset-key selected-deposit-asset-key
                                                  :direction :deposit
                                                  :terminal? lifecycle-terminal?
                                                  :outcome lifecycle-outcome
                                                  :outcome-label lifecycle-outcome-label
                                                  :recovery-hint lifecycle-recovery-hint
                                                  :destination-explorer-url lifecycle-destination-explorer-url
                                                  :include-queue-position? false})
        withdraw-lifecycle (lifecycle-panel-model hyperunit-lifecycle
                                                  {:selected-asset-key selected-withdraw-asset-key
                                                   :direction :withdraw
                                                   :terminal? lifecycle-terminal?
                                                   :outcome lifecycle-outcome
                                                   :outcome-label lifecycle-outcome-label
                                                   :recovery-hint lifecycle-recovery-hint
                                                   :destination-explorer-url lifecycle-destination-explorer-url
                                                   :include-queue-position? true})
        deposit-unsupported-detail (case selected-deposit-flow-kind
                                     :route "Route-based bridge/swap flow will be implemented in the next milestone."
                                     :hyperunit-address "Address-based deposit instructions will be implemented in the next milestone."
                                     "Deposit flow details are unavailable.")
        deposit-summary-rows [(summary-row "Minimum deposit"
                                           (str (or (:minimum selected-deposit-asset) deposit-min-usdc)
                                                " "
                                                (or (:symbol selected-deposit-asset) "")))
                              (summary-row "Estimated time" deposit-estimated-time)
                              (summary-row "Network fee" deposit-network-fee)]
        withdraw-summary-rows (cond-> []
                                (and (number? withdraw-min-amount)
                                     (pos? withdraw-min-amount))
                                (conj (summary-row "Minimum withdrawal"
                                                   (str withdraw-min-amount
                                                        " "
                                                        selected-withdraw-symbol)))
                                true
                                (conj (summary-row "Estimated time" withdraw-estimated-time)
                                      (summary-row "Network fee" withdraw-network-fee)))
        content-kind (case mode
                       :deposit deposit-content-kind*
                       :transfer :transfer/form
                       :withdraw :withdraw/form
                       :legacy :unsupported/workflow
                       :unknown)]
    {:open? (true? (:open? modal))
     :mode mode
     :legacy-kind legacy-kind
     :anchor (:anchor modal)
     :title title
     :deposit-step deposit-step
     :deposit-search-input (or (:deposit-search-input modal) "")
     :deposit-assets deposit-assets*
     :deposit-selected-asset selected-deposit-asset
     :deposit-flow-kind selected-deposit-flow-kind
     :deposit-flow-supported? selected-deposit-implemented?
     :deposit-generated-address generated-address
     :deposit-generated-signatures generated-signatures
     :withdraw-assets withdraw-assets*
     :withdraw-selected-asset selected-withdraw-asset
     :withdraw-selected-asset-key selected-withdraw-asset-key
     :withdraw-flow-kind selected-withdraw-flow-kind
     :withdraw-generated-address (non-blank-text (:withdraw-generated-address modal))
     :amount-input (or (:amount-input modal) "")
     :to-perp? (true? (:to-perp? modal))
     :destination-input (or (:destination-input modal) "")
     :hyperunit-lifecycle hyperunit-lifecycle
     :hyperunit-lifecycle-terminal? lifecycle-terminal?
     :hyperunit-lifecycle-outcome lifecycle-outcome
     :hyperunit-lifecycle-outcome-label lifecycle-outcome-label
     :hyperunit-lifecycle-recovery-hint lifecycle-recovery-hint
     :hyperunit-lifecycle-destination-explorer-url lifecycle-destination-explorer-url
     :hyperunit-withdrawal-queue hyperunit-withdrawal-queue
     :max-display (format-usdc-display max-amount)
     :max-input (format-usdc-input max-amount)
     :max-symbol (if (= mode :withdraw) selected-withdraw-symbol "USDC")
     :submitting? submitting?
     :submit-disabled? submit-disabled?
     :preview-ok? preview-ok?
     :status-message status-message
     :deposit-submit-label deposit-submit-label
     :deposit-quick-amounts deposit-quick-amounts
     :deposit-min-usdc deposit-min-usdc
     :deposit-min-amount (or (:minimum selected-deposit-asset) deposit-min-usdc)
     :deposit-estimated-time deposit-estimated-time
     :deposit-network-fee deposit-network-fee
     :withdraw-estimated-time withdraw-estimated-time
     :withdraw-network-fee withdraw-network-fee
     :withdraw-queue-length withdraw-queue-length
     :withdraw-queue-last-operation-tx-id withdraw-queue-last-operation-tx-id
     :withdraw-queue-last-operation-explorer-url withdraw-queue-last-operation-explorer-url
     :hyperunit-fee-estimate-loading? hyperunit-fee-estimate-loading?
     :hyperunit-fee-estimate-error hyperunit-fee-estimate-error
     :hyperunit-withdrawal-queue-loading? hyperunit-withdrawal-queue-loading?
     :hyperunit-withdrawal-queue-error hyperunit-withdrawal-queue-error
     :submit-label (if submitting?
                     "Submitting..."
                     (case mode
                       :transfer "Transfer"
                       :withdraw "Withdraw"
                       "Confirm"))
     :min-withdraw-usdc withdraw-min-usdc
     :min-withdraw-amount withdraw-min-amount
     :min-withdraw-symbol selected-withdraw-symbol
     :modal {:open? (true? (:open? modal))
             :mode mode
             :title title
             :anchor (:anchor modal)}
     :content {:kind content-kind}
     :feedback {:message status-message
                :visible? show-status-message?
                :tone :error}
     :deposit {:step deposit-step
               :search {:value (or (:deposit-search-input modal) "")
                        :placeholder "Search a supported asset"}
               :assets deposit-assets*
               :selected-asset selected-deposit-asset
               :flow {:kind selected-deposit-flow-kind
                      :supported? selected-deposit-implemented?
                      :generated-address generated-address
                      :generated-signature-count generated-signature-count
                      :unsupported-detail deposit-unsupported-detail
                      :fee-estimate {:state (fee-state hyperunit-fee-estimate-loading?
                                                       hyperunit-fee-estimate-error)
                                     :message hyperunit-fee-estimate-error}}
               :amount {:value (or (:amount-input modal) "")
                        :quick-amounts deposit-quick-amounts
                        :minimum-value (or (:minimum selected-deposit-asset) deposit-min-usdc)
                        :minimum-input (format-usdc-input (or (:minimum selected-deposit-asset)
                                                              deposit-min-usdc))}
               :summary {:rows deposit-summary-rows}
               :lifecycle deposit-lifecycle
               :actions {:submit-label deposit-submit-label
                         :submit-disabled? submit-disabled?
                         :submitting? submitting?}}
     :transfer {:to-perp? (true? (:to-perp? modal))
                :amount {:value (or (:amount-input modal) "")
                         :max-display (format-usdc-display transfer-max)
                         :max-input (format-usdc-input transfer-max)
                         :symbol "USDC"}
                :actions {:submit-label (if submitting? "Submitting..." "Transfer")
                          :submit-disabled? submit-disabled?
                          :submitting? submitting?}}
     :withdraw {:assets withdraw-assets*
                :selected-asset selected-withdraw-asset
                :destination {:value (or (:destination-input modal) "")}
                :amount {:value (or (:amount-input modal) "")
                         :max-display (format-usdc-display withdraw-max)
                         :max-input (format-usdc-input withdraw-max)
                         :symbol selected-withdraw-symbol}
                :flow {:kind selected-withdraw-flow-kind
                       :protocol-address (non-blank-text (:withdraw-generated-address modal))
                       :fee-estimate {:state (fee-state hyperunit-fee-estimate-loading?
                                                        hyperunit-fee-estimate-error)
                                      :message hyperunit-fee-estimate-error}
                       :withdrawal-queue {:state (withdrawal-queue-state hyperunit-withdrawal-queue-loading?
                                                                         withdraw-queue-length
                                                                         hyperunit-withdrawal-queue-error)
                                          :length withdraw-queue-length
                                          :last-operation {:tx-id withdraw-queue-last-operation-tx-id
                                                           :explorer-url withdraw-queue-last-operation-explorer-url}
                                          :message hyperunit-withdrawal-queue-error}}
                :summary {:rows withdraw-summary-rows}
                :lifecycle withdraw-lifecycle
                :actions {:submit-label (if submitting? "Submitting..." "Withdraw")
                          :submit-disabled? submit-disabled?
                          :submitting? submitting?}}
     :legacy {:kind legacy-kind
              :message (str "The " (name legacy-kind) " funding workflow is not available yet.")}}))
