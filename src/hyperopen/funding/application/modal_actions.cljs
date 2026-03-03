(ns hyperopen.funding.application.modal-actions
  (:require [clojure.string :as str]
            [hyperopen.funding.application.modal-commands :as modal-commands]
            [hyperopen.funding.application.modal-vm :as modal-vm]
            [hyperopen.funding.domain.lifecycle :as lifecycle-domain]
            [hyperopen.domain.trading :as trading-domain]))

(def ^:private funding-modal-path
  [:funding-ui :modal])

(def ^:private withdraw-min-usdc
  5)

(def ^:private deposit-min-usdc
  5)

(def ^:private deposit-chain-id-mainnet
  "0xa4b1")

(def ^:private deposit-chain-id-testnet
  "0x66eee")

(def ^:private deposit-quick-amounts
  [5 1000 10000 100000])

(def ^:private anchor-keys
  [:left :right :top :bottom :width :height :viewport-width :viewport-height])

(def ^:private deposit-assets-base
  [{:key :usdc
    :symbol "USDC"
    :name "USDC"
    :network "Arbitrum"
    :flow-kind :bridge2
    :minimum deposit-min-usdc}
   {:key :usdt
    :symbol "USDT"
    :name "Tether"
    :network "Arbitrum"
    :flow-kind :route
    :route-key "lifi"}
   {:key :btc
    :symbol "BTC"
    :name "Bitcoin"
    :network "Bitcoin"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "bitcoin"}
   {:key :eth
    :symbol "ETH"
    :name "Ethereum"
    :network "Ethereum"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "ethereum"}
   {:key :sol
    :symbol "SOL"
    :name "Solana"
    :network "Solana"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "solana"}
   {:key :2z
    :symbol "2Z"
    :name "ZZ"
    :network "Solana"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "solana"}
   {:key :bonk
    :symbol "BONK"
    :name "Bonk"
    :network "Solana"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "solana"}
   {:key :ena
    :symbol "ENA"
    :name "Ethena"
    :network "Ethereum"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "ethereum"}
   {:key :fart
    :symbol "FARTCOIN"
    :name "Fartcoin"
    :network "Solana"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "solana"}
   {:key :mon
    :symbol "MON"
    :name "Monad"
    :network "Monad"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "monad"}
   {:key :pump
    :symbol "PUMP"
    :name "Pump"
    :network "Solana"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "solana"}
   {:key :spxs
    :symbol "SPX"
    :name "SPX"
    :network "Solana"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "solana"}
   {:key :xpl
    :symbol "XPL"
    :name "Plasma"
    :network "Plasma"
    :flow-kind :hyperunit-address
    :hyperunit-source-chain "plasma"}
   {:key :usdh
    :symbol "USDH"
    :name "USDH"
    :network "Arbitrum"
    :flow-kind :route
    :route-key "arbitrum_across"
    :maximum 1000000}])

(def ^:private deposit-asset-keys
  (set (map :key deposit-assets-base)))

(def ^:private deposit-implemented-asset-keys
  #{:usdc :usdt :btc :eth :sol :2z :bonk :ena :fart :mon :pump :spxs :xpl :usdh})

(def ^:private withdraw-default-asset-key
  :usdc)

(def ^:private withdraw-supported-asset-keys
  (->> deposit-assets-base
       (filter (fn [{:keys [key flow-kind]}]
                 (or (= key :usdc)
                     (= flow-kind :hyperunit-address))))
       (map :key)
       set))

(def ^:private hyperunit-withdraw-minimum-by-asset-key
  {:btc 0.0003
   :eth 0.007
   :sol 0.12
   :2z 150
   :bonk 1800000
   :ena 120
   :fart 55
   :mon 450
   :pump 5500
   :spxs 32
   :xpl 60})

(def ^:private hyperunit-lifecycle-failure-fragments
  ["fail" "error" "revert" "cancel" "refund" "drop"])

(def ^:private hyperunit-explorer-tx-base-by-chain
  {"arbitrum" "https://arbiscan.io/tx/"
   "bitcoin" "https://mempool.space/tx/"
   "ethereum" "https://etherscan.io/tx/"
   "solana" "https://solscan.io/tx/"})

(def ^:private hyperliquid-explorer-tx-base-url
  "https://app.hyperliquid.xyz/explorer/tx/")

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn- parse-num
  [value]
  (trading-domain/parse-num value))

(defn- finite-number?
  [value]
  (and (number? value)
       (js/isFinite value)
       (not (js/isNaN value))))

(defn- clamp
  [value min-value max-value]
  (cond
    (< value min-value) min-value
    (> value max-value) max-value
    :else value))

(defn- amount->text
  [value]
  (if (finite-number? value)
    (trading-domain/number->clean-string (max 0 value) 6)
    "0"))

(defn- normalize-amount-input
  [value]
  (-> (or value "")
      str
      (str/replace #"," "")
      (str/replace #"\s+" "")))

(defn- parse-input-amount
  [value]
  (let [parsed (parse-num (normalize-amount-input value))]
    (when (finite-number? parsed)
      (max 0 parsed))))

(defn- normalize-evm-address
  [value]
  (let [text (some-> value str str/trim str/lower-case)]
    (when (and (string? text)
               (re-matches #"^0x[0-9a-f]{40}$" text))
      text)))

(defn- normalize-withdraw-destination
  [value]
  (non-blank-text value))

(defn- wallet-address
  [state]
  (normalize-evm-address (get-in state [:wallet :address])))

(defn- normalize-mode
  [value]
  (let [mode (cond
               (keyword? value) value
               (string? value) (some-> value str/trim str/lower-case keyword)
               :else nil)]
    (cond
      (= :deposit mode) :deposit
      (= :transfer mode) :transfer
      (= :withdraw mode) :withdraw
      (= :legacy mode) :legacy
      :else nil)))

(defn- normalize-deposit-step
  [value]
  (let [step (cond
               (keyword? value) value
               (string? value) (some-> value str/trim str/lower-case keyword)
               :else nil)]
    (if (= step :amount-entry)
      :amount-entry
      :asset-select)))

(defn- normalize-chain-id
  [value]
  (let [raw (some-> value str str/trim)]
    (when (seq raw)
      (let [hex? (str/starts-with? raw "0x")
            source (if hex? (subs raw 2) raw)
            base (if hex? 16 10)
            parsed (js/parseInt source base)]
        (when (and (number? parsed)
                   (not (js/isNaN parsed)))
          (str "0x" (.toString (js/Math.floor parsed) 16)))))))

(defn- normalize-anchor
  [anchor]
  (when (map? anchor)
    (let [normalized (reduce (fn [acc k]
                               (if-let [num (parse-num (get anchor k))]
                                 (assoc acc k num)
                                 acc))
                             {}
                             anchor-keys)]
      (when (seq normalized)
        normalized))))

(defn- normalize-deposit-asset-key
  [value]
  (let [asset-key (cond
                    (keyword? value) value
                    (string? value) (some-> value str/trim str/lower-case keyword)
                    :else nil)]
    (when (contains? deposit-asset-keys asset-key)
      asset-key)))

(defn- normalize-withdraw-asset-key
  [value]
  (let [asset-key (cond
                    (keyword? value) value
                    (string? value) (some-> value str/trim str/lower-case keyword)
                    :else nil)]
    (when (contains? withdraw-supported-asset-keys asset-key)
      asset-key)))

(defn- normalize-lifecycle-direction
  [value]
  (let [direction (cond
                    (keyword? value) value
                    (string? value) (some-> value str/trim str/lower-case keyword)
                    :else nil)]
    (when (contains? #{:deposit :withdraw} direction)
      direction)))

(defn- lifecycle-token
  [value]
  (some-> value
          name
          str/lower-case
          (str/replace #"[^a-z0-9]+" "-")
          (str/replace #"^-+|-+$" "")))

(defn- lifecycle-fragment-match?
  [value fragments]
  (let [token (lifecycle-token value)]
    (and (seq token)
         (some #(str/includes? token %) fragments))))

(def hyperunit-lifecycle-terminal? lifecycle-domain/hyperunit-lifecycle-terminal?)
(def default-hyperunit-lifecycle-state lifecycle-domain/default-hyperunit-lifecycle-state)
(def normalize-hyperunit-lifecycle lifecycle-domain/normalize-hyperunit-lifecycle)
(def default-hyperunit-fee-estimate-state lifecycle-domain/default-hyperunit-fee-estimate-state)
(def normalize-hyperunit-fee-estimate lifecycle-domain/normalize-hyperunit-fee-estimate)
(def default-hyperunit-withdrawal-queue-state lifecycle-domain/default-hyperunit-withdrawal-queue-state)
(def normalize-hyperunit-withdrawal-queue lifecycle-domain/normalize-hyperunit-withdrawal-queue)

(defn- resolve-deposit-network
  [state]
  (let [wallet-chain-id (normalize-chain-id (get-in state [:wallet :chain-id]))]
    (if (= wallet-chain-id deposit-chain-id-testnet)
      {:chain-id deposit-chain-id-testnet
       :chain-label "Arbitrum Sepolia"}
      {:chain-id deposit-chain-id-mainnet
       :chain-label "Arbitrum"})))

(defn- deposit-assets
  [state]
  (let [{:keys [chain-id chain-label]} (resolve-deposit-network state)]
    (mapv (fn [asset]
            (if (= :usdc (:key asset))
              (assoc asset
                     :network chain-label
                     :chain-id chain-id)
              asset))
          deposit-assets-base)))

(defn- deposit-asset
  [state modal]
  (let [selected-key (normalize-deposit-asset-key (:deposit-selected-asset-key modal))]
    (some (fn [asset]
            (when (= selected-key (:key asset))
              asset))
          (deposit-assets state))))

(defn- deposit-asset-implemented?
  [asset]
  (contains? deposit-implemented-asset-keys (:key asset)))

(defn- deposit-assets-filtered
  [state modal]
  (let [search-term (-> (or (:deposit-search-input modal) "")
                        str
                        str/trim
                        str/lower-case)
        assets (deposit-assets state)]
    (if-not (seq search-term)
      assets
      (filterv (fn [{:keys [symbol name network]}]
                 (let [symbol* (str/lower-case (or symbol ""))
                       name* (str/lower-case (or name ""))
                       network* (str/lower-case (or network ""))]
                   (or (str/includes? symbol* search-term)
                       (str/includes? name* search-term)
                       (str/includes? network* search-term))))
               assets))))

(defn- withdraw-assets
  [state]
  (->> (deposit-assets state)
       (filterv (fn [asset]
                  (contains? withdraw-supported-asset-keys (:key asset))))))

(defn- withdraw-asset
  [state modal]
  (let [selected-key (or (normalize-withdraw-asset-key (:withdraw-selected-asset-key modal))
                         withdraw-default-asset-key)
        assets (withdraw-assets state)]
    (or (some (fn [asset]
                (when (= selected-key (:key asset))
                  asset))
              assets)
        (first assets))))

(defn default-funding-modal-state
  []
  {:open? false
   :mode nil
   :legacy-kind nil
   :anchor nil
   :deposit-step :asset-select
   :deposit-search-input ""
   :deposit-selected-asset-key nil
   :deposit-generated-address nil
   :deposit-generated-signatures nil
   :deposit-generated-asset-key nil
   :amount-input ""
   :to-perp? true
   :destination-input ""
   :withdraw-selected-asset-key withdraw-default-asset-key
   :withdraw-generated-address nil
   :hyperunit-lifecycle (default-hyperunit-lifecycle-state)
   :hyperunit-fee-estimate (default-hyperunit-fee-estimate-state)
   :hyperunit-withdrawal-queue (default-hyperunit-withdrawal-queue-state)
   :submitting? false
   :error nil})

(defn- modal-state
  [state]
  (let [stored-modal (if (map? (get-in state funding-modal-path))
                       (get-in state funding-modal-path)
                       {})
        modal (merge (default-funding-modal-state)
                     stored-modal)]
    (assoc modal
           :anchor (normalize-anchor (:anchor modal))
           :withdraw-selected-asset-key (or (normalize-withdraw-asset-key
                                             (:withdraw-selected-asset-key modal))
                                            withdraw-default-asset-key)
           :hyperunit-fee-estimate (normalize-hyperunit-fee-estimate
                                    (:hyperunit-fee-estimate modal))
           :hyperunit-withdrawal-queue (normalize-hyperunit-withdrawal-queue
                                        (:hyperunit-withdrawal-queue modal))
           :hyperunit-lifecycle (normalize-hyperunit-lifecycle
                                 (:hyperunit-lifecycle modal)))))

(defn modal-open?
  [state]
  (true? (:open? (modal-state state))))

(defn- usdc-coin?
  [coin]
  (and (string? coin)
       (str/starts-with? (str/upper-case (str/trim coin)) "USDC")))

(defn- normalize-coin-token
  [value]
  (some-> value str str/trim str/upper-case))

(defn- balance-row-available
  [row]
  (when (map? row)
    (let [available-direct (or (parse-num (:available row))
                               (parse-num (:availableBalance row))
                               (parse-num (:free row)))
          total (or (parse-num (:total row))
                    (parse-num (:totalBalance row)))
          hold (parse-num (:hold row))
          derived (cond
                    (finite-number? total)
                    (if (finite-number? hold)
                      (- total hold)
                      total)

                    :else nil)
          available (or available-direct derived)]
      (when (finite-number? available)
        (max 0 available)))))

(defn- spot-usdc-available
  [state]
  (some (fn [row]
          (when (usdc-coin? (:coin row))
            (balance-row-available row)))
        (get-in state [:spot :clearinghouse-state :balances])))

(defn- spot-asset-available
  [state symbol]
  (let [target (normalize-coin-token symbol)]
    (some (fn [row]
            (when (= target
                     (normalize-coin-token (:coin row)))
              (balance-row-available row)))
          (get-in state [:spot :clearinghouse-state :balances]))))

(defn- summary-derived-withdrawable
  [summary]
  (let [account-value (parse-num (:accountValue summary))
        margin-used (parse-num (:totalMarginUsed summary))]
    (when (and (finite-number? account-value)
               (finite-number? margin-used))
      (max 0 (- account-value margin-used)))))

(defn- perps-withdrawable
  [state]
  (let [clearinghouse-state (or (get-in state [:webdata2 :clearinghouseState]) {})
        direct (some parse-num
                     [(:withdrawable clearinghouse-state)
                      (:withdrawableUsd clearinghouse-state)
                      (:withdrawableUSDC clearinghouse-state)
                      (:availableToWithdraw clearinghouse-state)
                      (:availableToWithdrawUsd clearinghouse-state)
                      (:availableToWithdrawUSDC clearinghouse-state)])
        summary (or (:marginSummary clearinghouse-state)
                    (:crossMarginSummary clearinghouse-state)
                    {})
        derived (summary-derived-withdrawable summary)
        value (or direct derived)]
    (if (finite-number? value)
      (max 0 value)
      0)))

(defn- transfer-max-amount
  [state {:keys [to-perp?]}]
  (if (true? to-perp?)
    (or (spot-usdc-available state) 0)
    (perps-withdrawable state)))

(defn- withdraw-max-amount
  [state selected-asset]
  (cond
    (nil? selected-asset)
    0

    (= :usdc (:key selected-asset))
    (perps-withdrawable state)

    :else
    (or (spot-asset-available state (:symbol selected-asset))
        0)))

(defn- format-usdc-display
  [value]
  (.toLocaleString (js/Number. (max 0 (or (parse-num value) 0)))
                   "en-US"
                   #js {:minimumFractionDigits 2
                        :maximumFractionDigits 2}))

(defn- format-usdc-input
  [value]
  (amount->text (max 0 (or (parse-num value) 0))))

(defn- withdraw-minimum-amount
  [asset]
  (let [asset-key (:key asset)]
    (cond
      (= asset-key :usdc)
      withdraw-min-usdc

      (contains? hyperunit-withdraw-minimum-by-asset-key asset-key)
      (get hyperunit-withdraw-minimum-by-asset-key asset-key)

      :else 0)))

(defn- hyperunit-source-chain
  [asset]
  (some-> (:hyperunit-source-chain asset)
          non-blank-text
          str/lower-case))

(defn- hyperunit-lifecycle-failure?
  [lifecycle]
  (let [lifecycle* (normalize-hyperunit-lifecycle lifecycle)]
    (or (lifecycle-fragment-match? (:state lifecycle*)
                                   hyperunit-lifecycle-failure-fragments)
        (lifecycle-fragment-match? (:status lifecycle*)
                                   hyperunit-lifecycle-failure-fragments)
        (and (hyperunit-lifecycle-terminal? lifecycle*)
             (seq (non-blank-text (:error lifecycle*)))))))

(defn- hyperunit-lifecycle-recovery-hint
  [lifecycle]
  (let [lifecycle* (normalize-hyperunit-lifecycle lifecycle)
        refunded? (or (lifecycle-fragment-match? (:state lifecycle*) ["refund"])
                      (lifecycle-fragment-match? (:status lifecycle*) ["refund"]))
        canceled? (or (lifecycle-fragment-match? (:state lifecycle*) ["cancel"])
                      (lifecycle-fragment-match? (:status lifecycle*) ["cancel"]))]
    (cond
      refunded?
      "Funds were refunded on the source chain. Confirm the wallet balance, then retry."

      canceled?
      "The operation was canceled. Retry if you still want to continue."

      (= :withdraw (:direction lifecycle*))
      "Verify the destination address and network, then submit a new withdrawal."

      (= :deposit (:direction lifecycle*))
      "Verify the source transfer network and amount, then generate a new deposit address."

      :else
      "Retry the operation and monitor the lifecycle status.")))

(defn- hyperunit-explorer-tx-base-url
  [direction chain]
  (if (= direction :deposit)
    hyperliquid-explorer-tx-base-url
    (get hyperunit-explorer-tx-base-by-chain chain)))

(defn- hyperunit-explorer-tx-url
  [direction chain tx-id]
  (let [direction* (normalize-lifecycle-direction direction)
        chain* (some-> chain non-blank-text str/lower-case)
        tx-id* (non-blank-text tx-id)]
    (when (seq tx-id*)
      (when-let [base-url (hyperunit-explorer-tx-base-url direction* chain*)]
        (str base-url (js/encodeURIComponent tx-id*))))))

(defn- hyperunit-fee-entry
  [fee-estimate chain]
  (let [estimate* (normalize-hyperunit-fee-estimate fee-estimate)
        chain* (some-> chain non-blank-text str/lower-case)]
    (when (and (seq chain*)
               (map? (:by-chain estimate*)))
      (get (:by-chain estimate*) chain*))))

(defn- hyperunit-withdrawal-queue-entry
  [withdrawal-queue chain]
  (let [queue* (normalize-hyperunit-withdrawal-queue withdrawal-queue)
        chain* (some-> chain non-blank-text str/lower-case)]
    (when (and (seq chain*)
               (map? (:by-chain queue*)))
      (get (:by-chain queue*) chain*))))

(def ^:private chain-fee-format-by-chain
  {"bitcoin" {:symbol "BTC" :decimals 8}
   "ethereum" {:symbol "ETH" :decimals 18}
   "solana" {:symbol "SOL" :decimals 9}})

(defn- integer-like-number?
  [value]
  (and (number? value)
       (finite-number? value)
       (= value (js/Math.floor value))))

(defn- fee-value->number
  [value]
  (cond
    (and (number? value) (finite-number? value))
    value

    (string? value)
    (parse-input-amount value)

    :else nil))

(defn- fee-value->chain-units
  [value chain]
  (let [chain* (some-> chain non-blank-text str/lower-case)
        {:keys [decimals]} (get chain-fee-format-by-chain chain*)
        decimals* (or decimals 0)
        parsed-number (fee-value->number value)
        raw-text (when (string? value) (non-blank-text value))
        integer-text? (and (string? raw-text)
                           (re-matches #"^\d+$" raw-text))
        integer-like? (or integer-text?
                          (integer-like-number? parsed-number))]
    (cond
      (nil? parsed-number)
      nil

      (and (> decimals* 0) integer-like?)
      (/ parsed-number (js/Math.pow 10 decimals*))

      :else
      parsed-number)))

(defn- estimate-fee-display
  [value chain]
  (let [chain* (some-> chain non-blank-text str/lower-case)
        {:keys [symbol decimals]} (get chain-fee-format-by-chain chain*)
        normalized-value (fee-value->chain-units value chain*)
        display-text (when (finite-number? normalized-value)
                       (trading-domain/number->clean-string (max 0 normalized-value)
                                                            (if (number? decimals)
                                                              (min 8 (max 2 decimals))
                                                              6)))
        fallback-text (non-blank-text value)]
    (cond
      (and (seq display-text) (seq symbol))
      (str display-text " " symbol)

      (seq display-text)
      display-text

      :else
      fallback-text)))

(defn- transfer-preview
  [state modal]
  (let [amount (parse-input-amount (:amount-input modal))
        to-perp? (true? (:to-perp? modal))
        max-amount (transfer-max-amount state modal)]
    (cond
      (not (finite-number? max-amount))
      {:ok? false
       :display-message "Unable to determine transfer balance."}

      (<= max-amount 0)
      {:ok? false
       :display-message (if to-perp?
                          "No spot USDC available to transfer."
                          "No perps balance available to transfer.")}

      (not (finite-number? amount))
      {:ok? false
       :display-message "Enter a valid amount."}

      (<= amount 0)
      {:ok? false
       :display-message "Enter an amount greater than 0."}

      (> amount max-amount)
      {:ok? false
       :display-message "Amount exceeds available balance."}

      :else
      {:ok? true
       :request {:action {:type "usdClassTransfer"
                          :amount (amount->text amount)
                          :toPerp to-perp?}}})))

(defn- withdraw-preview
  [state modal]
  (let [selected-asset (withdraw-asset state modal)
        flow-kind (:flow-kind selected-asset)
        asset-key (:key selected-asset)
        asset-symbol (or (:symbol selected-asset) "Asset")
        destination-chain (non-blank-text (:hyperunit-source-chain selected-asset))
        amount (parse-input-amount (:amount-input modal))
        destination (if (= flow-kind :hyperunit-address)
                      (normalize-withdraw-destination (:destination-input modal))
                      (normalize-evm-address (:destination-input modal)))
        max-amount (withdraw-max-amount state selected-asset)
        min-amount (withdraw-minimum-amount selected-asset)]
    (cond
      (nil? selected-asset)
      {:ok? false
       :display-message "Select an asset to withdraw."}

      (nil? destination)
      {:ok? false
       :display-message "Enter a valid destination address."}

      (and (= flow-kind :hyperunit-address)
           (not (seq destination-chain)))
      {:ok? false
       :display-message (str "Withdrawal source chain is unavailable for " asset-symbol ".")}

      (<= max-amount 0)
      {:ok? false
       :display-message "No withdrawable balance available."}

      (not (finite-number? amount))
      {:ok? false
       :display-message "Enter a valid amount."}

      (and (finite-number? min-amount)
           (> min-amount 0)
           (< amount min-amount))
      {:ok? false
       :display-message (str "Minimum withdrawal is "
                             (amount->text min-amount)
                             " "
                             asset-symbol
                             ".")}

      (> amount max-amount)
      {:ok? false
       :display-message "Amount exceeds withdrawable balance."}

      :else
      (if (= flow-kind :hyperunit-address)
        {:ok? true
         :request {:action {:type "hyperunitSendAssetWithdraw"
                            :asset (name asset-key)
                            :token asset-symbol
                            :amount (amount->text amount)
                            :destination destination
                            :destinationChain destination-chain
                            :network (:network selected-asset)}}}
        {:ok? true
         :request {:action {:type "withdraw3"
                            :amount (amount->text amount)
                            :destination destination}}}))))

(defn- deposit-preview
  [state modal]
  (let [deposit-step (normalize-deposit-step (:deposit-step modal))
        selected-asset (deposit-asset state modal)
        amount (parse-input-amount (:amount-input modal))
        min-amount (or (:minimum selected-asset) deposit-min-usdc)
        flow-kind (:flow-kind selected-asset)]
    (cond
      (not= deposit-step :amount-entry)
      {:ok? false}

      (nil? selected-asset)
      {:ok? false
       :display-message "Select an asset to deposit."}

      (not (deposit-asset-implemented? selected-asset))
      {:ok? false
       :display-message (str (:symbol selected-asset)
                             " deposits are not implemented yet in Hyperopen.")}

      (= flow-kind :hyperunit-address)
      (if-let [from-chain (non-blank-text (:hyperunit-source-chain selected-asset))]
        {:ok? true
         :request {:action {:type "hyperunitGenerateDepositAddress"
                            :asset (name (:key selected-asset))
                            :fromChain from-chain
                            :network (:network selected-asset)}}}
        {:ok? false
         :display-message (str (:symbol selected-asset)
                               " address deposits are not implemented yet in Hyperopen.")})

      (= flow-kind :route)
      (cond
        (not (finite-number? amount))
        {:ok? false
         :display-message "Enter a valid amount."}

        (<= amount 0)
        {:ok? false
         :display-message "Enter an amount greater than 0."}

        (< amount min-amount)
        {:ok? false
         :display-message (str "Minimum deposit is " min-amount " "
                               (:symbol selected-asset) ".")}

        (and (finite-number? (:maximum selected-asset))
             (> amount (:maximum selected-asset)))
        {:ok? false
         :display-message (str "Maximum deposit is " (:maximum selected-asset) " "
                               (:symbol selected-asset) ".")}

        (= (:key selected-asset) :usdt)
        {:ok? true
         :request {:action {:type "lifiUsdtToUsdcBridge2Deposit"
                            :asset (name (:key selected-asset))
                            :amount (amount->text amount)
                            :chainId deposit-chain-id-mainnet}}}

        (= (:key selected-asset) :usdh)
        {:ok? true
         :request {:action {:type "acrossUsdcToUsdhDeposit"
                            :asset (name (:key selected-asset))
                            :amount (amount->text amount)
                            :chainId deposit-chain-id-mainnet}}}

        :else
        {:ok? false
         :display-message (str (:symbol selected-asset)
                               " route deposits are not implemented yet in Hyperopen.")})

      (not= flow-kind :bridge2)
      {:ok? false
       :display-message "Deposit flow unavailable."}

      (not (finite-number? amount))
      {:ok? false
       :display-message "Enter a valid amount."}

      (<= amount 0)
      {:ok? false
       :display-message "Enter an amount greater than 0."}

      (< amount min-amount)
      {:ok? false
       :display-message (str "Minimum deposit is " min-amount " "
                             (:symbol selected-asset) ".")}

      :else
      {:ok? true
       :request {:action {:type "bridge2Deposit"
                          :asset (name (:key selected-asset))
                          :amount (amount->text amount)
                          :chainId (:chain-id selected-asset)}}})))

(defn- preview
  [state modal]
  (case (normalize-mode (:mode modal))
    :deposit (deposit-preview state modal)
    :transfer (transfer-preview state modal)
    :withdraw (withdraw-preview state modal)
    {:ok? false
     :display-message "Funding action unavailable."}))

(defn funding-modal-view-model
  [state]
  (modal-vm/funding-modal-view-model
   {:modal-state modal-state
    :normalize-mode normalize-mode
    :normalize-hyperunit-lifecycle normalize-hyperunit-lifecycle
    :normalize-deposit-step normalize-deposit-step
    :deposit-assets-filtered deposit-assets-filtered
    :deposit-asset deposit-asset
    :withdraw-assets withdraw-assets
    :withdraw-asset withdraw-asset
    :deposit-asset-implemented? deposit-asset-implemented?
    :normalize-deposit-asset-key normalize-deposit-asset-key
    :non-blank-text non-blank-text
    :preview preview
    :normalize-hyperunit-fee-estimate normalize-hyperunit-fee-estimate
    :normalize-hyperunit-withdrawal-queue normalize-hyperunit-withdrawal-queue
    :hyperunit-source-chain hyperunit-source-chain
    :hyperunit-fee-entry hyperunit-fee-entry
    :hyperunit-withdrawal-queue-entry hyperunit-withdrawal-queue-entry
    :hyperunit-explorer-tx-url hyperunit-explorer-tx-url
    :hyperunit-lifecycle-terminal? hyperunit-lifecycle-terminal?
    :hyperunit-lifecycle-failure? hyperunit-lifecycle-failure?
    :hyperunit-lifecycle-recovery-hint hyperunit-lifecycle-recovery-hint
    :estimate-fee-display estimate-fee-display
    :transfer-max-amount transfer-max-amount
    :withdraw-max-amount withdraw-max-amount
    :withdraw-minimum-amount withdraw-minimum-amount
    :format-usdc-display format-usdc-display
    :format-usdc-input format-usdc-input
    :deposit-quick-amounts deposit-quick-amounts
    :deposit-min-usdc deposit-min-usdc
    :withdraw-min-usdc withdraw-min-usdc}
   state))

(declare close-funding-modal
         open-funding-deposit-modal
         open-funding-withdraw-modal
         open-funding-transfer-modal
         open-legacy-funding-modal)

(defn- command-deps
  []
  {:modal-state modal-state
   :normalize-anchor normalize-anchor
   :default-funding-modal-state default-funding-modal-state
   :wallet-address wallet-address
   :funding-modal-path funding-modal-path
   :normalize-withdraw-asset-key normalize-withdraw-asset-key
   :withdraw-default-asset-key withdraw-default-asset-key
   :close-funding-modal-fn close-funding-modal
   :open-funding-deposit-modal-fn open-funding-deposit-modal
   :open-funding-withdraw-modal-fn open-funding-withdraw-modal
   :open-funding-transfer-modal-fn open-funding-transfer-modal
   :open-legacy-funding-modal-fn open-legacy-funding-modal
   :normalize-amount-input normalize-amount-input
   :normalize-deposit-step normalize-deposit-step
   :normalize-deposit-asset-key normalize-deposit-asset-key
   :default-hyperunit-lifecycle-state default-hyperunit-lifecycle-state
   :default-hyperunit-withdrawal-queue-state default-hyperunit-withdrawal-queue-state
   :normalize-mode normalize-mode
   :normalize-hyperunit-lifecycle normalize-hyperunit-lifecycle
   :non-blank-text non-blank-text
   :transfer-max-amount transfer-max-amount
   :withdraw-max-amount withdraw-max-amount
   :withdraw-asset withdraw-asset
   :format-usdc-input format-usdc-input
   :transfer-preview transfer-preview
   :withdraw-preview withdraw-preview
   :deposit-preview deposit-preview})

(defn open-funding-deposit-modal
  ([state]
   (open-funding-deposit-modal state nil))
  ([state anchor]
   (modal-commands/open-funding-deposit-modal (command-deps) state anchor)))

(defn open-funding-transfer-modal
  ([state]
   (open-funding-transfer-modal state nil))
  ([state anchor]
   (modal-commands/open-funding-transfer-modal (command-deps) state anchor)))

(defn open-funding-withdraw-modal
  ([state]
   (open-funding-withdraw-modal state nil))
  ([state anchor]
   (modal-commands/open-funding-withdraw-modal (command-deps) state anchor)))

(defn- open-legacy-funding-modal
  [state legacy-kind]
  (modal-commands/open-legacy-funding-modal (command-deps) state legacy-kind))

(defn close-funding-modal
  [state]
  (modal-commands/close-funding-modal (command-deps) state))

(defn handle-funding-modal-keydown
  [state key]
  (modal-commands/handle-funding-modal-keydown (command-deps) state key))

(defn set-funding-modal-field
  [state path value]
  (modal-commands/set-funding-modal-field (command-deps) state path value))

(defn set-hyperunit-lifecycle
  [state lifecycle]
  (modal-commands/set-hyperunit-lifecycle (command-deps) state lifecycle))

(defn clear-hyperunit-lifecycle
  [state]
  (modal-commands/clear-hyperunit-lifecycle (command-deps) state))

(defn set-hyperunit-lifecycle-error
  [state error]
  (modal-commands/set-hyperunit-lifecycle-error (command-deps) state error))

(defn set-funding-transfer-direction
  [state to-perp?]
  (modal-commands/set-funding-transfer-direction (command-deps) state to-perp?))

(defn set-funding-amount-to-max
  [state]
  (modal-commands/set-funding-amount-to-max (command-deps) state))

(defn submit-funding-transfer
  [state]
  (modal-commands/submit-funding-transfer (command-deps) state))

(defn submit-funding-withdraw
  [state]
  (modal-commands/submit-funding-withdraw (command-deps) state))

(defn submit-funding-deposit
  [state]
  (modal-commands/submit-funding-deposit (command-deps) state))

(defn set-funding-modal-compat
  [state modal]
  (modal-commands/set-funding-modal-compat (command-deps) state modal))
