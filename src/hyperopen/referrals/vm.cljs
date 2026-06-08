(ns hyperopen.referrals.vm
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.utils.formatting :as formatting]
            [hyperopen.wallet.core :as wallet]))

(defn- field
  [m k]
  (case k
    :cum-vlm (or (:cumVlm m) (:cum-vlm m) (get m "cumVlm") (get m "cum-vlm"))
    :referrer-state (or (:referrerState m) (:referrer-state m) (get m "referrerState") (get m "referrer-state"))
    :reward-history (or (:rewardHistory m) (:reward-history m) (get m "rewardHistory") (get m "reward-history"))
    :token-to-state (or (:tokenToState m) (:token-to-state m) (get m "tokenToState") (get m "token-to-state"))
    :stage (or (:stage m) (get m "stage"))
    :data (or (:data m) (get m "data"))
    :code (or (:code m) (get m "code"))
    :n-referrals (or (:nReferrals m) (:n-referrals m) (get m "nReferrals") (get m "n-referrals"))
    :referral-states (or (:referralStates m) (:referral-states m) (get m "referralStates") (get m "referral-states"))
    :unclaimed-rewards (or (:unclaimedRewards m) (:unclaimed-rewards m) (get m "unclaimedRewards") (get m "unclaimed-rewards"))
    :claimed-rewards (or (:claimedRewards m) (:claimed-rewards m) (get m "claimedRewards") (get m "claimed-rewards"))
    :builder-rewards (or (:builderRewards m) (:builder-rewards m) (get m "builderRewards") (get m "builder-rewards"))
    :user (or (:user m) (:address m) (get m "user") (get m "address"))
    :time (or (:time m) (:timestamp m) (get m "time") (get m "timestamp"))
    :amount (or (:amount m) (get m "amount"))
    :token (or (:token m) (get m "token"))
    :description (or (:description m) (:type m) (get m "description") (get m "type"))
    nil))

(defn- numberish
  [value]
  (cond
    (number? value) (when (js/isFinite value) value)
    (string? value) (let [parsed (js/parseFloat value)]
                      (when (js/isFinite parsed)
                        parsed))
    :else nil))

(defn- format-number
  [value]
  (or (formatting/format-intl-number value {:maximumFractionDigits 2})
      "0"))

(defn- format-usd
  [value]
  (or (formatting/format-intl-number value
                                     {:style "currency"
                                      :currency "USD"
                                      :maximumFractionDigits 2})
      "$0"))

(defn- normalize-stage
  [value]
  (let [stage (some-> value str str/trim str/lower-case)]
    (case stage
      "ready" :ready
      "needtocreatecode" :need-to-create-code
      "need-to-create-code" :need-to-create-code
      "need_to_create_code" :need-to-create-code
      "needtotrade" :need-to-trade
      "need-to-trade" :need-to-trade
      "need_to_trade" :need-to-trade
      :unknown)))

(defn- token-state-rows
  [payload]
  (let [token-to-state (field payload :token-to-state)]
    (cond
      (map? token-to-state)
      (mapv (fn [[token state]]
              {:token (name token)
               :unclaimed (or (numberish (field state :unclaimed-rewards)) 0)
               :claimed (or (numberish (field state :claimed-rewards)) 0)
               :builder (or (numberish (field state :builder-rewards)) 0)})
            token-to-state)

      (sequential? token-to-state)
      (mapv (fn [state]
              {:token (str (or (field state :token) "USDC"))
               :unclaimed (or (numberish (field state :unclaimed-rewards)) 0)
               :claimed (or (numberish (field state :claimed-rewards)) 0)
               :builder (or (numberish (field state :builder-rewards)) 0)})
            token-to-state)

      :else
      [])))

(defn- sum-rewards
  [rows k]
  (reduce + 0 (map #(or (numberish (get % k)) 0) rows)))

(defn- reward-summary
  [payload]
  (let [rows (token-state-rows payload)
        unclaimed (sum-rewards rows :unclaimed)
        claimed (sum-rewards rows :claimed)]
    {:rows rows
     :claimable unclaimed
     :earned (+ claimed unclaimed)
     :claimable-label (format-usd unclaimed)
     :earned-label (format-usd (+ claimed unclaimed))}))

(defn- referral-rows
  [referrer-state]
  (let [data (field referrer-state :data)]
    (vec (or (field data :referral-states) []))))

(defn- legacy-rows
  [payload]
  (vec (or (field payload :reward-history) [])))

(defn referrals-vm
  [state]
  (let [payload (or (get-in state [:referrals :raw]) {})
        referrer-state (or (field payload :referrer-state) {})
        referrer-data (or (field referrer-state :data) {})
        rewards (reward-summary payload)
        owner (account-context/owner-address state)
        blocked-message (or (account-context/mutations-blocked-message state)
                            (when (account-context/selected-subaccount-owned-by-owner? state)
                              "Switch to the master account before changing referral settings."))
        stage (normalize-stage (field referrer-state :stage))
        code (field referrer-data :code)]
    {:connected? (some? owner)
     :owner owner
     :owner-label (or (wallet/short-addr owner) "Not connected")
     :loading? (true? (get-in state [:referrals :loading?]))
     :error (get-in state [:referrals :error])
     :last-error (get-in state [:referrals-ui :last-error])
     :submitting? (get-in state [:referrals-ui :submitting?])
     :active-tab (or (get-in state [:referrals-ui :active-tab]) :referrals)
     :form {:code (or (get-in state [:referrals-ui :form :code]) "")
            :new-code (or (get-in state [:referrals-ui :form :new-code]) "")}
     :pending-code (get-in state [:referrals-ui :pending-code])
     :mutation-blocked-message blocked-message
     :stage stage
     :stage-label (case stage
                    :ready "Ready"
                    :need-to-create-code "Eligible to create code"
                    :need-to-trade "Trade more volume to create a code"
                    "Unavailable")
     :referral-code code
     :join-link (when (seq code)
                  (str "/join/" code))
     :traders-referred (or (field referrer-data :n-referrals) 0)
     :traders-referred-label (format-number (or (field referrer-data :n-referrals) 0))
     :cum-vlm (or (numberish (field payload :cum-vlm)) 0)
     :cum-vlm-label (format-usd (or (numberish (field payload :cum-vlm)) 0))
     :rewards rewards
     :referral-rows (referral-rows referrer-state)
     :legacy-rows (legacy-rows payload)}))
