(ns hyperopen.subaccounts.management
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]))

(def missing-owner-message
  "Connect your wallet before selecting a subaccount.")

(def invalid-subaccount-selection-message
  "Select a subaccount owned by the connected master wallet.")

(def invalid-subaccount-name-message
  "Subaccount name must be 1-16 characters.")

(def invalid-transfer-amount-message
  "Enter a positive USDC amount with at most 6 decimal places.")

(def invalid-spot-transfer-amount-message
  "Enter a positive spot amount.")

(def invalid-spot-transfer-token-message
  "Select a spot asset to transfer.")

(defn- subaccount-row-address
  [row]
  (account-context/normalize-address
   (or (:sub-account-user row)
       (:subAccountUser row)
       (get row "subAccountUser")
       (get row "sub-account-user"))))

(defn- subaccount-row-master
  [row]
  (account-context/normalize-address
   (or (:master row)
       (get row "master"))))

(defn- owned-subaccount-row
  [state owner-address subaccount-address]
  (some (fn [row]
          (when (and (= subaccount-address (subaccount-row-address row))
                     (= owner-address (subaccount-row-master row)))
            row))
        (get-in state [:account-context :subaccounts :rows])))

(defn- row-name
  [row]
  (let [name* (some-> (or (:name row)
                          (get row "name"))
                      str
                      str/trim)]
    (or name* "")))

(defn- normalize-form-field
  [field]
  (let [field-id (-> field
                     name
                     str/lower-case
                     (str/replace #"[-_\s]" ""))]
    (case field-id
      "createname" :create-name
      "renamename" :rename-name
      "transferamount" :transfer-amount
      "transferdirection" :transfer-direction
      "transferaccount" :transfer-account
      "transferaccountmenuopen?" :transfer-account-menu-open?
      "transferaccountmenuopen" :transfer-account-menu-open?
      "transfertoken" :transfer-token
      "transfertokenmenuopen?" :transfer-token-menu-open?
      "transfertokenmenuopen" :transfer-token-menu-open?
      nil)))

(defn- normalize-transfer-direction
  [value]
  (if (= "withdraw" (-> value name str/lower-case))
    :withdraw
    :deposit))

(defn- normalize-transfer-account
  [value]
  (if (= "spot" (-> value name str/lower-case))
    :spot
    :trading))

(defn- normalize-boolean
  [value]
  (or (true? value)
      (= "true" (-> value str str/lower-case))))

(defn- normalize-form-value
  [field value]
  (case field
    :transfer-direction (normalize-transfer-direction value)
    :transfer-account (normalize-transfer-account value)
    :transfer-account-menu-open? (normalize-boolean value)
    :transfer-token-menu-open? (normalize-boolean value)
    (str (or value ""))))

(defn set-subaccount-form-field
  [_state field value]
  (if-let [field* (normalize-form-field field)]
    [[:effects/save-many (cond-> [[[:account-context :subaccounts field*]
                                    (normalize-form-value field* value)]]
                           (= field* :transfer-account)
                           (conj [[:account-context :subaccounts :transfer-account-menu-open?] false]
                                 [[:account-context :subaccounts :transfer-token-menu-open?] false])

                           (= field* :transfer-account-menu-open?)
                           (conj [[:account-context :subaccounts :transfer-token-menu-open?] false])

                           (= field* :transfer-token-menu-open?)
                           (conj [[:account-context :subaccounts :transfer-account-menu-open?] false])

                           (= field* :transfer-token)
                           (conj [[:account-context :subaccounts :transfer-account-menu-open?] false]
                                 [[:account-context :subaccounts :transfer-token-menu-open?] false])

                           true
                           (conj [[:account-context :subaccounts :error] nil]))]]
    []))

(defn toggle-transfer-direction
  [state]
  (let [current (normalize-transfer-direction
                 (get-in state [:account-context :subaccounts :transfer-direction]))
        next-direction (if (= :deposit current) :withdraw :deposit)]
    [[:effects/save-many [[[:account-context :subaccounts :transfer-direction] next-direction]
                          [[:account-context :subaccounts :transfer-account-menu-open?] false]
                          [[:account-context :subaccounts :transfer-token-menu-open?] false]
                          [[:account-context :subaccounts :error] nil]]]]))

(defn open-create-popover
  [_state]
  [[:effects/save-many [[[:account-context :subaccounts :create-popover-open?] true]
                        [[:account-context :subaccounts :create-name] ""]
                        [[:account-context :subaccounts :error] nil]]]])

(defn close-create-popover
  [_state]
  [[:effects/save-many [[[:account-context :subaccounts :create-popover-open?] false]
                        [[:account-context :subaccounts :create-name] ""]
                        [[:account-context :subaccounts :error] nil]]]])

(defn copy-subaccount-address
  [_state address]
  [[:effects/copy-wallet-address (account-context/normalize-address address)]])

(defn- normalize-subaccount-name
  [value]
  (some-> (str (or value ""))
          str/trim))

(defn- valid-subaccount-name?
  [value]
  (let [name* (normalize-subaccount-name value)
        length (count name*)]
    (and (pos? length)
         (<= length 16))))

(defn parse-usdc-amount->micros
  [value]
  (let [amount-text (str/trim (str (or value "")))]
    (when (re-matches #"^\d+(?:\.\d{0,6})?$" amount-text)
      (let [[whole frac] (str/split amount-text #"\." 2)
            micros-text (str whole
                             (subs (str (or frac "") "000000") 0 6))
            parsed (js/parseInt micros-text 10)]
        (when (and (number? parsed)
                   (not (js/isNaN parsed))
                   (pos? parsed)
                   (<= parsed js/Number.MAX_SAFE_INTEGER))
          parsed)))))

(defn- invalid-owner-effect
  []
  [[:effects/save [:account-context :subaccounts :error]
    missing-owner-message]])

(defn- invalid-row-effect
  []
  [[:effects/save [:account-context :subaccounts :error]
    invalid-subaccount-selection-message]])

(defn- invalid-create-name-effect
  []
  [[:effects/save-many [[[:account-context :subaccounts :creating?] false]
                        [[:account-context :subaccounts :error]
                         invalid-subaccount-name-message]]]])

(defn- invalid-rename-name-effect
  []
  [[:effects/save-many [[[:account-context :subaccounts :renaming-address] nil]
                        [[:account-context :subaccounts :error]
                         invalid-subaccount-name-message]]]])

(defn submit-create-subaccount
  [state]
  (let [owner-address (account-context/owner-address state)
        name* (normalize-subaccount-name
               (get-in state [:account-context :subaccounts :create-name]))]
    (cond
      (not (seq owner-address)) (invalid-owner-effect)
      (not (valid-subaccount-name? name*)) (invalid-create-name-effect)
      :else
      [[:effects/save-many [[[:account-context :subaccounts :creating?] true]
                            [[:account-context :subaccounts :create-popover-open?] true]
                            [[:account-context :subaccounts :error] nil]]]
       [:effects/api-create-subaccount {:name name*}]])))

(defn start-rename-subaccount
  [state address]
  (let [owner-address (account-context/owner-address state)
        address* (account-context/normalize-address address)
        row (owned-subaccount-row state owner-address address*)]
    (cond
      (not (seq owner-address)) (invalid-owner-effect)
      (not row) (invalid-row-effect)
      :else
      [[:effects/save-many [[[:account-context :subaccounts :renaming-address] address*]
                            [[:account-context :subaccounts :rename-name] (row-name row)]
                            [[:account-context :subaccounts :error] nil]]]])))

(defn cancel-rename-subaccount
  [_state]
  [[:effects/save-many [[[:account-context :subaccounts :renaming-address] nil]
                        [[:account-context :subaccounts :rename-name] ""]
                        [[:account-context :subaccounts :error] nil]]]])

(defn submit-rename-subaccount
  [state address]
  (let [owner-address (account-context/owner-address state)
        address* (account-context/normalize-address address)
        row (owned-subaccount-row state owner-address address*)
        name* (normalize-subaccount-name
               (get-in state [:account-context :subaccounts :rename-name]))]
    (cond
      (not (seq owner-address)) (invalid-owner-effect)
      (not row) (invalid-row-effect)
      (not (valid-subaccount-name? name*)) (invalid-rename-name-effect)
      :else
      [[:effects/save-many [[[:account-context :subaccounts :renaming-address] address*]
                            [[:account-context :subaccounts :error] nil]]]
       [:effects/api-rename-subaccount {:sub-account-user address*
                                        :name name*}]])))

(defn start-transfer-subaccount
  [state address]
  (let [owner-address (account-context/owner-address state)
        address* (account-context/normalize-address address)]
    (cond
      (not (seq owner-address)) (invalid-owner-effect)
      (not (owned-subaccount-row state owner-address address*)) (invalid-row-effect)
      :else
      [[:effects/save-many [[[:account-context :subaccounts :transferring-address] address*]
                            [[:account-context :subaccounts :transfer-amount] ""]
                            [[:account-context :subaccounts :transfer-direction] :deposit]
                            [[:account-context :subaccounts :transfer-account] :trading]
                            [[:account-context :subaccounts :transfer-account-menu-open?] false]
                            [[:account-context :subaccounts :transfer-token] "USDC"]
                            [[:account-context :subaccounts :transfer-token-menu-open?] false]
                            [[:account-context :subaccounts :error] nil]]]])))

(defn cancel-transfer-subaccount
  [_state]
  [[:effects/save-many [[[:account-context :subaccounts :transferring-address] nil]
                        [[:account-context :subaccounts :transfer-amount] ""]
                        [[:account-context :subaccounts :transfer-direction] :deposit]
                        [[:account-context :subaccounts :transfer-account] :trading]
                        [[:account-context :subaccounts :transfer-account-menu-open?] false]
                        [[:account-context :subaccounts :transfer-token] "USDC"]
                        [[:account-context :subaccounts :transfer-token-menu-open?] false]
                        [[:account-context :subaccounts :error] nil]]]])

(defn- valid-spot-amount?
  [value]
  (boolean
   (let [amount-text (str/trim (str (or value "")))]
     (when (re-matches #"^\d+(?:\.\d+)?$" amount-text)
       (let [parsed (js/Number amount-text)]
         (and (number? parsed)
              (not (js/isNaN parsed))
              (js/isFinite parsed)
              (pos? parsed)))))))

(defn- normalize-transfer-token
  [value]
  (some-> value str str/trim not-empty))

(defn submit-transfer-subaccount
  [state address]
  (let [owner-address (account-context/owner-address state)
        address* (account-context/normalize-address address)
        row (owned-subaccount-row state owner-address address*)
        amount-text (str (or (get-in state [:account-context :subaccounts :transfer-amount])
                             ""))
        usd (parse-usdc-amount->micros amount-text)
        direction (normalize-transfer-direction
                   (get-in state [:account-context :subaccounts :transfer-direction]))
        account-kind (normalize-transfer-account
                      (get-in state [:account-context :subaccounts :transfer-account]))
        transfer-token (normalize-transfer-token
                        (get-in state [:account-context :subaccounts :transfer-token]))]
    (cond
      (not (seq owner-address)) (invalid-owner-effect)
      (not row) (invalid-row-effect)
      (= :spot account-kind)
      (cond
        (not (valid-spot-amount? amount-text))
        [[:effects/save-many [[[:account-context :subaccounts :transferring-address] nil]
                              [[:account-context :subaccounts :error]
                               invalid-spot-transfer-amount-message]]]]

        (not transfer-token)
        [[:effects/save-many [[[:account-context :subaccounts :transferring-address] nil]
                              [[:account-context :subaccounts :error]
                               invalid-spot-transfer-token-message]]]]

        :else
        [[:effects/save-many [[[:account-context :subaccounts :transferring-address] address*]
                              [[:account-context :subaccounts :error] nil]]]
         [:effects/api-transfer-subaccount {:sub-account-user address*
                                            :is-deposit (= :deposit direction)
                                            :amount amount-text
                                            :account-kind :spot
                                            :token transfer-token}]])

      (not usd)
      [[:effects/save-many [[[:account-context :subaccounts :transferring-address] nil]
                            [[:account-context :subaccounts :error]
                             invalid-transfer-amount-message]]]]
      :else
      [[:effects/save-many [[[:account-context :subaccounts :transferring-address] address*]
                            [[:account-context :subaccounts :error] nil]]]
       [:effects/api-transfer-subaccount {:sub-account-user address*
                                          :is-deposit (= :deposit direction)
                                          :usd usd
                                          :amount amount-text}]])))
