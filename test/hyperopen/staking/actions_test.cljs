(ns hyperopen.staking.actions-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.staking.actions :as actions]))

(def ^:private wallet-address
  "0x1234567890abcdef1234567890abcdef12345678")

(deftest parse-staking-route-supports-route-and-non-route-paths-test
  (is (= {:kind :page
          :path "/staking"}
         (actions/parse-staking-route "/staking/")))
  (is (= {:kind :page
          :path "/staking"}
         (actions/parse-staking-route "/staking?tab=validators")))
  (is (= {:kind :other
          :path "/trade"}
         (actions/parse-staking-route "/trade"))))

(deftest load-staking-route-emits-validator-and-user-load-effects-test
  (is (= [[:effects/save [:staking-ui :form-error] nil]
          [:effects/api-fetch-staking-validator-summaries]
          [:effects/api-fetch-staking-delegator-summary wallet-address]
          [:effects/api-fetch-staking-delegations wallet-address]
          [:effects/api-fetch-staking-rewards wallet-address]
          [:effects/api-fetch-staking-history wallet-address]
          [:effects/api-fetch-staking-spot-state wallet-address]]
         (actions/load-staking-route
          {:wallet {:address wallet-address}}
          "/staking")))
  (is (= [[:effects/save [:staking-ui :form-error] nil]
          [:effects/api-fetch-staking-validator-summaries]
          [:effects/save-many
           [[[:staking :delegator-summary] nil]
            [[:staking :delegations] []]
            [[:staking :rewards] []]
            [[:staking :history] []]
            [[:staking :errors :delegator-summary] nil]
            [[:staking :errors :delegations] nil]
            [[:staking :errors :rewards] nil]
            [[:staking :errors :history] nil]]]]
         (actions/load-staking-route {} "/staking")))
  (is (= []
         (actions/load-staking-route {} "/portfolio"))))

(deftest set-staking-form-field-normalizes-validator-and-ignores-unknown-fields-test
  (is (= [[:effects/save [:staking-ui :selected-validator]
           "0x1234567890abcdef1234567890abcdef12345678"]]
         (actions/set-staking-form-field
          {}
          :selected-validator
          " 0x1234567890ABCDEF1234567890ABCDEF12345678 ")))
  (is (= [[:effects/save [:staking-ui :selected-validator] ""]]
         (actions/set-staking-form-field
          {}
          :selected-validator
          "not-an-address")))
  (is (= []
         (actions/set-staking-form-field {} :not-a-field "1"))))

(deftest submit-staking-deposit-validates-wallet-and-builds-cdeposit-request-test
  (is (= [[:effects/save [:staking-ui :form-error]
           "Connect your wallet before transferring to staking balance."]
          [:effects/save [:staking-ui :submitting :deposit?] false]]
         (actions/submit-staking-deposit
          {:staking-ui {:deposit-amount "1"}})))
  (is (= [[:effects/save [:staking-ui :form-error] nil]
          [:effects/save [:staking-ui :submitting :deposit?] true]
          [:effects/api-submit-staking-deposit
           {:kind :deposit
            :action {:type "cDeposit"
                     :wei 125000000}}]]
         (actions/submit-staking-deposit
          {:wallet {:address wallet-address}
           :spot {:clearinghouse-state {:balances [{:coin "HYPE"
                                                    :available 2}]}}
           :staking-ui {:deposit-amount "1.25"}}))))

(deftest submit-staking-delegate-requires-validator-selection-test
  (is (= [[:effects/save [:staking-ui :form-error]
           "Select a validator before staking."]
          [:effects/save [:staking-ui :submitting :delegate?] false]]
         (actions/submit-staking-delegate
          {:wallet {:address wallet-address}
           :staking {:delegator-summary {:undelegated 5}}
           :staking-ui {:delegate-amount "1"
                        :selected-validator ""}}))))
