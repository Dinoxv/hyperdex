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

(defn- string-value
  [value]
  (or value ""))

(defn- flow-kind
  [asset]
  (or (:flow-kind asset) :unknown))

(defn- asset-symbol
  [asset fallback]
  (or (:symbol asset) fallback))

(defn- asset-minimum
  [asset fallback]
  (or (:minimum asset) fallback))

(defn- modal-legacy-kind
  [modal]
  (or (:legacy-kind modal) :unknown))

(defn- base-context
  [{:keys [modal-state
           normalize-mode
           normalize-hyperunit-lifecycle
           normalize-deposit-step
           normalize-withdraw-step
           non-blank-text
           deposit-quick-amounts
           deposit-min-usdc
           withdraw-min-usdc]}
  state]
  (let [modal (modal-state state)
        mode (normalize-mode (:mode modal))
        deposit-step (normalize-deposit-step (:deposit-step modal))
        withdraw-step (normalize-withdraw-step (:withdraw-step modal))]
    {:state state
     :modal modal
     :open? (true? (:open? modal))
     :mode mode
     :deposit? (= mode :deposit)
     :withdraw? (= mode :withdraw)
     :legacy? (= mode :legacy)
     :deposit-step deposit-step
     :deposit-step-amount-entry? (= deposit-step :amount-entry)
     :withdraw-step withdraw-step
     :withdraw-step-amount-entry? (= withdraw-step :amount-entry)
     :deposit-search-input (string-value (:deposit-search-input modal))
     :withdraw-search-input (string-value (:withdraw-search-input modal))
     :amount-input (string-value (:amount-input modal))
     :to-perp? (true? (:to-perp? modal))
     :destination-input (string-value (:destination-input modal))
     :send-token (non-blank-text (:send-token modal))
     :send-symbol (non-blank-text (:send-symbol modal))
     :send-prefix-label (non-blank-text (:send-prefix-label modal))
     :send-max-amount (:send-max-amount modal)
     :send-max-display (string-value (:send-max-display modal))
     :send-max-input (string-value (:send-max-input modal))
     :withdraw-generated-address (non-blank-text (:withdraw-generated-address modal))
     :anchor (:anchor modal)
     :error (:error modal)
     :submitting? (true? (:submitting? modal))
     :legacy-kind (modal-legacy-kind modal)
     :hyperunit-lifecycle (normalize-hyperunit-lifecycle (:hyperunit-lifecycle modal))
     :deposit-quick-amounts deposit-quick-amounts
     :deposit-min-usdc deposit-min-usdc
     :withdraw-min-usdc withdraw-min-usdc}))

(defn- with-asset-context
  [{:keys [state modal] :as ctx}
   {:keys [deposit-assets-filtered
           deposit-asset
           withdraw-assets-filtered
           withdraw-assets
           withdraw-asset
           deposit-asset-implemented?]}]
  (let [selected-deposit-asset (deposit-asset state modal)
        selected-withdraw-asset (withdraw-asset state modal)]
    (assoc ctx
           :deposit-assets (deposit-assets-filtered state modal)
           :selected-deposit-asset selected-deposit-asset
           :selected-deposit-asset-key (:key selected-deposit-asset)
           :selected-deposit-symbol (asset-symbol selected-deposit-asset "")
           :selected-deposit-flow-kind (flow-kind selected-deposit-asset)
           :selected-deposit-implemented? (deposit-asset-implemented? selected-deposit-asset)
           :withdraw-assets (withdraw-assets-filtered state modal)
           :withdraw-all-assets (withdraw-assets state)
           :selected-withdraw-asset selected-withdraw-asset
           :selected-withdraw-asset-key (:key selected-withdraw-asset)
           :selected-withdraw-symbol (asset-symbol selected-withdraw-asset "USDC")
           :selected-withdraw-flow-kind (flow-kind selected-withdraw-asset))))

(defn- with-generated-address-context
  [{:keys [modal selected-deposit-asset-key] :as ctx}
   {:keys [normalize-deposit-asset-key non-blank-text]}]
  (let [generated-address-asset-key (normalize-deposit-asset-key
                                     (:deposit-generated-asset-key modal))
        generated-address-active? (and selected-deposit-asset-key
                                       (= generated-address-asset-key
                                          selected-deposit-asset-key))
        generated-signatures (when generated-address-active?
                               (:deposit-generated-signatures modal))]
    (assoc ctx
           :generated-address (when generated-address-active?
                                (non-blank-text (:deposit-generated-address modal)))
           :generated-signatures generated-signatures
           :generated-signature-count (if (sequential? generated-signatures)
                                        (count generated-signatures)
                                        0))))

(defn- with-preview-context
  [{:keys [state modal] :as ctx}
   {:keys [preview]}]
  (let [preview-result (preview state modal)]
    (assoc ctx
           :preview-result preview-result
           :preview-ok? (:ok? preview-result)
           :preview-message (:display-message preview-result))))

(defn- withdraw-queue-length
  [selected-withdraw-flow-kind withdraw-chain-queue]
  (when (and (= selected-withdraw-flow-kind :hyperunit-address)
             (map? withdraw-chain-queue))
    (:withdrawal-queue-length withdraw-chain-queue)))

(defn- withdraw-queue-last-operation-tx-id
  [non-blank-text selected-withdraw-flow-kind withdraw-chain-queue]
  (when (and (= selected-withdraw-flow-kind :hyperunit-address)
             (map? withdraw-chain-queue))
    (non-blank-text
     (:last-withdraw-queue-operation-tx-id withdraw-chain-queue))))

(defn- withdraw-queue-last-operation-explorer-url
  [hyperunit-explorer-tx-url selected-withdraw-flow-kind withdraw-chain tx-id]
  (when (= selected-withdraw-flow-kind :hyperunit-address)
    (hyperunit-explorer-tx-url :withdraw withdraw-chain tx-id)))

(defn- with-async-context
  [{:keys [modal
           selected-deposit-asset
           selected-withdraw-asset
           selected-withdraw-flow-kind] :as ctx}
   {:keys [normalize-hyperunit-fee-estimate
           normalize-hyperunit-withdrawal-queue
           hyperunit-source-chain
           hyperunit-fee-entry
           hyperunit-withdrawal-queue-entry
           hyperunit-explorer-tx-url
           non-blank-text]}]
  (let [hyperunit-fee-estimate (normalize-hyperunit-fee-estimate
                                (:hyperunit-fee-estimate modal))
        hyperunit-withdrawal-queue (normalize-hyperunit-withdrawal-queue
                                    (:hyperunit-withdrawal-queue modal))
        deposit-chain (hyperunit-source-chain selected-deposit-asset)
        withdraw-chain (hyperunit-source-chain selected-withdraw-asset)
        deposit-chain-fee (hyperunit-fee-entry hyperunit-fee-estimate deposit-chain)
        withdraw-chain-fee (hyperunit-fee-entry hyperunit-fee-estimate withdraw-chain)
        withdraw-chain-queue (hyperunit-withdrawal-queue-entry
                              hyperunit-withdrawal-queue
                              withdraw-chain)
        withdraw-queue-length (withdraw-queue-length selected-withdraw-flow-kind
                                                     withdraw-chain-queue)
        withdraw-queue-last-operation-tx-id (withdraw-queue-last-operation-tx-id
                                             non-blank-text
                                             selected-withdraw-flow-kind
                                             withdraw-chain-queue)
        withdraw-queue-last-operation-explorer-url (withdraw-queue-last-operation-explorer-url
                                                    hyperunit-explorer-tx-url
                                                    selected-withdraw-flow-kind
                                                    withdraw-chain
                                                    withdraw-queue-last-operation-tx-id)]
    (assoc ctx
           :hyperunit-fee-estimate hyperunit-fee-estimate
           :hyperunit-fee-estimate-loading? (= :loading (:status hyperunit-fee-estimate))
           :hyperunit-fee-estimate-error (non-blank-text (:error hyperunit-fee-estimate))
           :hyperunit-withdrawal-queue hyperunit-withdrawal-queue
           :hyperunit-withdrawal-queue-loading? (= :loading
                                                  (:status hyperunit-withdrawal-queue))
           :hyperunit-withdrawal-queue-error (non-blank-text
                                              (:error hyperunit-withdrawal-queue))
           :deposit-chain deposit-chain
           :withdraw-chain withdraw-chain
           :deposit-chain-fee deposit-chain-fee
           :withdraw-chain-fee withdraw-chain-fee
           :withdraw-chain-queue withdraw-chain-queue
           :withdraw-queue-length withdraw-queue-length
           :withdraw-queue-last-operation-tx-id withdraw-queue-last-operation-tx-id
           :withdraw-queue-last-operation-explorer-url
           withdraw-queue-last-operation-explorer-url)))

(defn- lifecycle-outcome
  [hyperunit-lifecycle-failure? lifecycle-terminal? hyperunit-lifecycle]
  (when lifecycle-terminal?
    (if (hyperunit-lifecycle-failure? hyperunit-lifecycle)
      :failure
      :success)))

(defn- lifecycle-outcome-label
  [lifecycle-outcome]
  (case lifecycle-outcome
    :failure "Needs Attention"
    :success "Completed"
    nil))

(defn- lifecycle-recovery-hint
  [hyperunit-lifecycle-recovery-hint lifecycle-outcome hyperunit-lifecycle]
  (when (= lifecycle-outcome :failure)
    (hyperunit-lifecycle-recovery-hint hyperunit-lifecycle)))

(defn- lifecycle-destination-explorer-url
  [hyperunit-explorer-tx-url hyperunit-lifecycle withdraw-chain]
  (hyperunit-explorer-tx-url
   (:direction hyperunit-lifecycle)
   (when (= :withdraw (:direction hyperunit-lifecycle))
     withdraw-chain)
   (:destination-tx-hash hyperunit-lifecycle)))

(defn- with-lifecycle-context
  [{:keys [hyperunit-lifecycle
           selected-deposit-asset-key
           selected-withdraw-asset-key
           withdraw-chain] :as ctx}
   {:keys [hyperunit-lifecycle-terminal?
           hyperunit-lifecycle-failure?
           hyperunit-lifecycle-recovery-hint
           hyperunit-explorer-tx-url]}]
  (let [lifecycle-terminal? (hyperunit-lifecycle-terminal? hyperunit-lifecycle)
        lifecycle-outcome (lifecycle-outcome hyperunit-lifecycle-failure?
                                             lifecycle-terminal?
                                             hyperunit-lifecycle)
        lifecycle-outcome-label (lifecycle-outcome-label lifecycle-outcome)
        lifecycle-recovery-hint (lifecycle-recovery-hint hyperunit-lifecycle-recovery-hint
                                                         lifecycle-outcome
                                                         hyperunit-lifecycle)
        lifecycle-destination-explorer-url (lifecycle-destination-explorer-url
                                            hyperunit-explorer-tx-url
                                            hyperunit-lifecycle
                                            withdraw-chain)]
    (assoc ctx
           :hyperunit-lifecycle-terminal? lifecycle-terminal?
           :hyperunit-lifecycle-outcome lifecycle-outcome
           :hyperunit-lifecycle-outcome-label lifecycle-outcome-label
           :hyperunit-lifecycle-recovery-hint lifecycle-recovery-hint
           :hyperunit-lifecycle-destination-explorer-url
           lifecycle-destination-explorer-url
           :deposit-lifecycle (lifecycle-panel-model hyperunit-lifecycle
                                                     {:selected-asset-key selected-deposit-asset-key
                                                      :direction :deposit
                                                      :terminal? lifecycle-terminal?
                                                      :outcome lifecycle-outcome
                                                      :outcome-label lifecycle-outcome-label
                                                      :recovery-hint lifecycle-recovery-hint
                                                      :destination-explorer-url
                                                      lifecycle-destination-explorer-url
                                                      :include-queue-position? false})
           :withdraw-lifecycle (lifecycle-panel-model hyperunit-lifecycle
                                                      {:selected-asset-key selected-withdraw-asset-key
                                                       :direction :withdraw
                                                       :terminal? lifecycle-terminal?
                                                       :outcome lifecycle-outcome
                                                       :outcome-label lifecycle-outcome-label
                                                       :recovery-hint lifecycle-recovery-hint
                                                       :destination-explorer-url
                                                       lifecycle-destination-explorer-url
                                                       :include-queue-position? true}))))

(defn- mode-max-amount
  [mode send-max transfer-max withdraw-max]
  (case mode
    :send send-max
    :transfer transfer-max
    :withdraw withdraw-max
    0))

(defn- max-symbol
  [mode send-symbol selected-withdraw-symbol]
  (case mode
    :send send-symbol
    :withdraw selected-withdraw-symbol
    "USDC"))

(defn- with-amount-context
  [{:keys [state
           modal
           mode
           send-max-amount
           send-symbol
           selected-deposit-asset
           selected-withdraw-asset
           selected-withdraw-symbol
           deposit-min-usdc] :as ctx}
   {:keys [transfer-max-amount
           withdraw-max-amount
           withdraw-minimum-amount
           format-usdc-display
           format-usdc-input]}]
  (let [transfer-max (transfer-max-amount state modal)
        withdraw-max (withdraw-max-amount state selected-withdraw-asset)
        withdraw-min-amount (withdraw-minimum-amount selected-withdraw-asset)
        deposit-min-amount (asset-minimum selected-deposit-asset deposit-min-usdc)
        max-amount (mode-max-amount mode send-max-amount transfer-max withdraw-max)]
    (assoc ctx
           :transfer-max transfer-max
           :withdraw-max withdraw-max
           :withdraw-min-amount withdraw-min-amount
           :deposit-min-amount deposit-min-amount
           :deposit-min-input (format-usdc-input deposit-min-amount)
           :max-amount max-amount
           :max-display (if (= mode :send)
                          (:send-max-display ctx)
                          (format-usdc-display max-amount))
           :max-input (if (= mode :send)
                        (:send-max-input ctx)
                        (format-usdc-input max-amount))
           :max-symbol (max-symbol mode send-symbol selected-withdraw-symbol)
           :transfer-max-display (format-usdc-display transfer-max)
           :transfer-max-input (format-usdc-input transfer-max)
           :withdraw-max-display (format-usdc-display withdraw-max)
           :withdraw-max-input (format-usdc-input withdraw-max))))

(defn- deposit-estimated-time
  [non-blank-text
   {:keys [selected-deposit-flow-kind
           hyperunit-fee-estimate-loading?
           deposit-chain-fee]}]
  (if (= selected-deposit-flow-kind :hyperunit-address)
    (or (when hyperunit-fee-estimate-loading? "Loading...")
        (non-blank-text (:deposit-eta deposit-chain-fee))
        "Depends on source confirmations")
    "~10 seconds"))

(defn- deposit-network-fee
  [estimate-fee-display
   {:keys [selected-deposit-flow-kind
           hyperunit-fee-estimate-loading?
           deposit-chain-fee
           deposit-chain]}]
  (if (= selected-deposit-flow-kind :hyperunit-address)
    (or (when hyperunit-fee-estimate-loading? "Loading...")
        (estimate-fee-display (:deposit-fee deposit-chain-fee)
                              deposit-chain)
        "Paid on source chain")
    "None"))

(defn- withdraw-estimated-time
  [non-blank-text
   {:keys [selected-withdraw-flow-kind
           hyperunit-fee-estimate-loading?
           withdraw-chain-fee]}]
  (if (= selected-withdraw-flow-kind :hyperunit-address)
    (or (when hyperunit-fee-estimate-loading? "Loading...")
        (non-blank-text (:withdrawal-eta withdraw-chain-fee))
        "Depends on destination chain")
    "~10 seconds"))

(defn- withdraw-network-fee
  [estimate-fee-display
   {:keys [selected-withdraw-flow-kind
           hyperunit-fee-estimate-loading?
           withdraw-chain-fee
           withdraw-chain]}]
  (if (= selected-withdraw-flow-kind :hyperunit-address)
    (or (when hyperunit-fee-estimate-loading? "Loading...")
        (estimate-fee-display (:withdrawal-fee withdraw-chain-fee)
                              withdraw-chain)
        "Paid on destination chain")
    "None"))

(defn- status-message
  [{:keys [error
           preview-ok?
           preview-message
           mode
           deposit-step-amount-entry?
           withdraw-step-amount-entry?]}]
  (or error
      (when (and (not preview-ok?)
                 (seq preview-message)
                 (or (not= mode :deposit)
                     deposit-step-amount-entry?)
                 (or (not= mode :withdraw)
                     withdraw-step-amount-entry?))
        preview-message)))

(defn- show-status-message?
  [{:keys [legacy? deposit? withdraw? withdraw-step-amount-entry?]} status-message]
  (boolean
   (and (seq status-message)
        (not legacy?)
        (not deposit?)
        (or (not withdraw?)
            withdraw-step-amount-entry?))))

(defn- submit-disabled?
  [{:keys [submitting?
           deposit?
           withdraw?
           deposit-step-amount-entry?
           withdraw-step-amount-entry?
           preview-ok?]}]
  (or submitting?
      (and deposit?
           (not deposit-step-amount-entry?))
      (and withdraw?
           (not withdraw-step-amount-entry?))
      (not preview-ok?)))

(defn- title
  [{:keys [mode
           deposit?
           withdraw?
           deposit-step-amount-entry?
           withdraw-step-amount-entry?
           selected-deposit-symbol
           selected-withdraw-symbol
           legacy-kind]}]
  (cond
    (and deposit?
         deposit-step-amount-entry?
         (seq selected-deposit-symbol))
    (str "Deposit " selected-deposit-symbol)

    (and withdraw?
         withdraw-step-amount-entry?
         (seq selected-withdraw-symbol))
    (str "Withdraw " selected-withdraw-symbol)

    :else
    (case mode
      :deposit "Deposit"
      :send "Send Tokens"
      :transfer "Perps <-> Spot"
      :withdraw "Withdraw"
      :legacy (str/capitalize (name legacy-kind))
      "Funding")))

(defn- deposit-submit-label
  [{:keys [submitting?
           deposit?
           selected-deposit-flow-kind
           generated-address
           preview-ok?
           selected-deposit-implemented?
           preview-message]}]
  (if submitting?
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
        (or preview-message "Enter a valid amount")))))

(defn- submit-label
  [{:keys [submitting? mode]}]
  (if submitting?
    "Submitting..."
    (case mode
      :send "Send"
      :transfer "Transfer"
      :withdraw "Withdraw"
      "Confirm")))

(defn- deposit-unsupported-detail
  [selected-deposit-flow-kind]
  (case selected-deposit-flow-kind
    :route "Route-based bridge/swap flow will be implemented in the next milestone."
    :hyperunit-address "Address-based deposit instructions will be implemented in the next milestone."
    "Deposit flow details are unavailable."))

(defn- deposit-summary-rows
  [deposit-min-amount
   selected-deposit-symbol
   deposit-estimated-time
   deposit-network-fee]
  [(summary-row "Minimum deposit"
                (str deposit-min-amount
                     " "
                     selected-deposit-symbol))
   (summary-row "Estimated time" deposit-estimated-time)
   (summary-row "Network fee" deposit-network-fee)])

(defn- withdraw-summary-rows
  [withdraw-min-amount
   selected-withdraw-symbol
   withdraw-estimated-time
   withdraw-network-fee]
  (cond-> []
    (and (number? withdraw-min-amount)
         (pos? withdraw-min-amount))
    (conj (summary-row "Minimum withdrawal"
                       (str withdraw-min-amount
                            " "
                            selected-withdraw-symbol)))
    true
    (conj (summary-row "Estimated time" withdraw-estimated-time)
          (summary-row "Network fee" withdraw-network-fee))))

(defn- content-kind
  [{:keys [mode
           deposit-step
           withdraw-step
           selected-deposit-asset
           selected-deposit-flow-kind
           selected-deposit-implemented?
           selected-withdraw-asset]}]
  (case mode
    :deposit (deposit-content-kind deposit-step
                                   selected-deposit-asset
                                   selected-deposit-flow-kind
                                   selected-deposit-implemented?)
    :send :send/form
    :transfer :transfer/form
    :withdraw (if (and (= withdraw-step :amount-entry)
                       selected-withdraw-asset)
                :withdraw/detail
                :withdraw/select)
    :legacy :unsupported/workflow
    :unknown))

(defn- with-presentation-context
  [ctx
   {:keys [non-blank-text estimate-fee-display]}]
  (let [status-message (status-message ctx)
        submit-disabled? (submit-disabled? ctx)
        deposit-estimated-time (deposit-estimated-time non-blank-text ctx)
        deposit-network-fee (deposit-network-fee estimate-fee-display ctx)
        withdraw-estimated-time (withdraw-estimated-time non-blank-text ctx)
        withdraw-network-fee (withdraw-network-fee estimate-fee-display ctx)
        deposit-summary-rows (deposit-summary-rows (:deposit-min-amount ctx)
                                                   (:selected-deposit-symbol ctx)
                                                   deposit-estimated-time
                                                   deposit-network-fee)
        withdraw-summary-rows (withdraw-summary-rows (:withdraw-min-amount ctx)
                                                     (:selected-withdraw-symbol ctx)
                                                     withdraw-estimated-time
                                                     withdraw-network-fee)]
    (assoc ctx
           :status-message status-message
           :show-status-message? (show-status-message? ctx status-message)
           :submit-disabled? submit-disabled?
           :title (title ctx)
           :deposit-submit-label (deposit-submit-label ctx)
           :submit-label (submit-label ctx)
           :content-kind (content-kind ctx)
           :deposit-estimated-time deposit-estimated-time
           :deposit-network-fee deposit-network-fee
           :withdraw-estimated-time withdraw-estimated-time
           :withdraw-network-fee withdraw-network-fee
           :deposit-unsupported-detail (deposit-unsupported-detail
                                        (:selected-deposit-flow-kind ctx))
           :deposit-summary-rows deposit-summary-rows
           :withdraw-summary-rows withdraw-summary-rows)))

(defn- feedback-model
  [{:keys [status-message show-status-message?]}]
  {:message status-message
   :visible? show-status-message?
   :tone :error})

(defn- modal-model
  [{:keys [open? mode title anchor]}]
  {:open? open?
   :mode mode
   :title title
   :anchor anchor})

(defn- deposit-model
  [{:keys [deposit-step
           deposit-search-input
           deposit-assets
           selected-deposit-asset
           selected-deposit-flow-kind
           selected-deposit-implemented?
           generated-address
           generated-signature-count
           hyperunit-fee-estimate-loading?
           hyperunit-fee-estimate-error
           amount-input
           deposit-quick-amounts
           deposit-min-amount
           deposit-min-input
           deposit-summary-rows
           deposit-lifecycle
           deposit-submit-label
           submit-disabled?
           submitting?
           deposit-unsupported-detail]}]
  {:step deposit-step
   :search {:value deposit-search-input
            :placeholder "Search a supported asset"}
   :assets deposit-assets
   :selected-asset selected-deposit-asset
   :flow {:kind selected-deposit-flow-kind
          :supported? selected-deposit-implemented?
          :generated-address generated-address
          :generated-signature-count generated-signature-count
          :unsupported-detail deposit-unsupported-detail
          :fee-estimate {:state (fee-state hyperunit-fee-estimate-loading?
                                           hyperunit-fee-estimate-error)
                         :message hyperunit-fee-estimate-error}}
   :amount {:value amount-input
            :quick-amounts deposit-quick-amounts
            :minimum-value deposit-min-amount
            :minimum-input deposit-min-input}
   :summary {:rows deposit-summary-rows}
   :lifecycle deposit-lifecycle
   :actions {:submit-label deposit-submit-label
             :submit-disabled? submit-disabled?
             :submitting? submitting?}})

(defn- transfer-model
  [{:keys [to-perp?
           amount-input
           transfer-max-display
           transfer-max-input
           submit-disabled?
           submitting?]}]
  {:to-perp? to-perp?
   :amount {:value amount-input
            :max-display transfer-max-display
            :max-input transfer-max-input
            :symbol "USDC"}
   :actions {:submit-label (if submitting? "Submitting..." "Transfer")
             :submit-disabled? submit-disabled?
             :submitting? submitting?}})

(defn- send-model
  [{:keys [send-token
           send-symbol
           send-prefix-label
           destination-input
           amount-input
           send-max-display
           submit-disabled?
           submitting?]}]
  {:asset {:token send-token
           :symbol send-symbol
           :prefix-label send-prefix-label}
   :destination {:value destination-input}
   :amount {:value amount-input
            :max-display send-max-display
            :symbol send-symbol}
   :actions {:submit-label (if submitting? "Submitting..." "Send")
             :submit-disabled? submit-disabled?
             :submitting? submitting?}})

(defn- withdraw-model
  [{:keys [withdraw-assets
           withdraw-step
           withdraw-search-input
           selected-withdraw-asset
           destination-input
           amount-input
           withdraw-max-display
           withdraw-max-input
           selected-withdraw-symbol
           selected-withdraw-flow-kind
           withdraw-generated-address
           hyperunit-fee-estimate-loading?
           hyperunit-fee-estimate-error
           hyperunit-withdrawal-queue-loading?
           withdraw-queue-length
           withdraw-queue-last-operation-tx-id
           withdraw-queue-last-operation-explorer-url
           hyperunit-withdrawal-queue-error
           withdraw-summary-rows
           withdraw-lifecycle
           submit-disabled?
           submitting?]}]
  {:step withdraw-step
   :search {:value withdraw-search-input
            :placeholder "Search a supported asset"}
   :assets withdraw-assets
   :selected-asset selected-withdraw-asset
   :destination {:value destination-input}
   :amount {:value amount-input
            :max-display withdraw-max-display
            :max-input withdraw-max-input
            :available-label (str withdraw-max-input
                                  " "
                                  selected-withdraw-symbol
                                  " available")
            :symbol selected-withdraw-symbol}
   :flow {:kind selected-withdraw-flow-kind
          :protocol-address withdraw-generated-address
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
             :submitting? submitting?}})

(defn- legacy-model
  [{:keys [legacy-kind]}]
  {:kind legacy-kind
   :message (str "The " (name legacy-kind) " funding workflow is not available yet.")})

(defn- build-view-model
  [{:keys [open?
           mode
           legacy-kind
           anchor
           title
           deposit-step
           deposit-search-input
           withdraw-step
           withdraw-search-input
           deposit-assets
           selected-deposit-asset
           selected-deposit-flow-kind
           selected-deposit-implemented?
           generated-address
           generated-signatures
           withdraw-assets
           withdraw-all-assets
           selected-withdraw-asset
           selected-withdraw-asset-key
           selected-withdraw-flow-kind
           withdraw-generated-address
           amount-input
           to-perp?
           destination-input
           hyperunit-lifecycle
           hyperunit-lifecycle-terminal?
           hyperunit-lifecycle-outcome
           hyperunit-lifecycle-outcome-label
           hyperunit-lifecycle-recovery-hint
           hyperunit-lifecycle-destination-explorer-url
           hyperunit-withdrawal-queue
           max-display
           max-input
           max-symbol
           submitting?
           submit-disabled?
           preview-ok?
           status-message
           deposit-submit-label
           deposit-quick-amounts
           deposit-min-usdc
           deposit-min-amount
           deposit-estimated-time
           deposit-network-fee
           send-token
           send-symbol
           send-prefix-label
           send-max-display
           withdraw-estimated-time
           withdraw-network-fee
           withdraw-queue-length
           withdraw-queue-last-operation-tx-id
           withdraw-queue-last-operation-explorer-url
           hyperunit-fee-estimate-loading?
           hyperunit-fee-estimate-error
           hyperunit-withdrawal-queue-loading?
           hyperunit-withdrawal-queue-error
           submit-label
           withdraw-min-usdc
           withdraw-min-amount
           selected-withdraw-symbol
           content-kind] :as ctx}]
  {:open? open?
   :mode mode
   :legacy-kind legacy-kind
   :anchor anchor
   :title title
   :deposit-step deposit-step
   :deposit-search-input deposit-search-input
   :withdraw-step withdraw-step
   :withdraw-search-input withdraw-search-input
   :deposit-assets deposit-assets
   :deposit-selected-asset selected-deposit-asset
   :deposit-flow-kind selected-deposit-flow-kind
   :deposit-flow-supported? selected-deposit-implemented?
   :deposit-generated-address generated-address
   :deposit-generated-signatures generated-signatures
   :withdraw-assets withdraw-assets
   :withdraw-all-assets withdraw-all-assets
   :withdraw-selected-asset selected-withdraw-asset
   :withdraw-selected-asset-key selected-withdraw-asset-key
   :withdraw-flow-kind selected-withdraw-flow-kind
   :withdraw-generated-address withdraw-generated-address
   :amount-input amount-input
   :to-perp? to-perp?
   :destination-input destination-input
   :hyperunit-lifecycle hyperunit-lifecycle
   :hyperunit-lifecycle-terminal? hyperunit-lifecycle-terminal?
   :hyperunit-lifecycle-outcome hyperunit-lifecycle-outcome
   :hyperunit-lifecycle-outcome-label hyperunit-lifecycle-outcome-label
   :hyperunit-lifecycle-recovery-hint hyperunit-lifecycle-recovery-hint
   :hyperunit-lifecycle-destination-explorer-url
   hyperunit-lifecycle-destination-explorer-url
   :hyperunit-withdrawal-queue hyperunit-withdrawal-queue
   :max-display max-display
   :max-input max-input
   :max-symbol max-symbol
   :submitting? submitting?
   :submit-disabled? submit-disabled?
   :preview-ok? preview-ok?
   :status-message status-message
   :deposit-submit-label deposit-submit-label
   :deposit-quick-amounts deposit-quick-amounts
   :deposit-min-usdc deposit-min-usdc
   :deposit-min-amount deposit-min-amount
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
   :submit-label submit-label
   :min-withdraw-usdc withdraw-min-usdc
   :min-withdraw-amount withdraw-min-amount
   :min-withdraw-symbol selected-withdraw-symbol
   :modal (modal-model ctx)
   :content {:kind content-kind}
   :feedback (feedback-model ctx)
   :deposit (deposit-model ctx)
   :send (send-model ctx)
   :transfer (transfer-model ctx)
   :withdraw (withdraw-model ctx)
   :legacy (legacy-model ctx)})

(defn funding-modal-view-model
  [deps
   state]
  (-> (base-context deps state)
      (with-asset-context deps)
      (with-generated-address-context deps)
      (with-preview-context deps)
      (with-async-context deps)
      (with-lifecycle-context deps)
      (with-amount-context deps)
      (with-presentation-context deps)
      build-view-model))
