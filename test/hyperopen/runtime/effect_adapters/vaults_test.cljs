(ns hyperopen.runtime.effect-adapters.vaults-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.runtime.effect-adapters.vaults :as vault-adapters]))

(deftest facade-vault-adapters-delegate-to-vault-module-test
  (is (identical? vault-adapters/api-fetch-vault-index-effect
                  effect-adapters/api-fetch-vault-index-effect))
  (is (identical? vault-adapters/api-fetch-vault-summaries-effect
                  effect-adapters/api-fetch-vault-summaries-effect))
  (is (identical? vault-adapters/api-fetch-user-vault-equities-effect
                  effect-adapters/api-fetch-user-vault-equities-effect))
  (is (identical? vault-adapters/api-fetch-vault-details-effect
                  effect-adapters/api-fetch-vault-details-effect))
  (is (identical? vault-adapters/api-fetch-vault-webdata2-effect
                  effect-adapters/api-fetch-vault-webdata2-effect))
  (is (identical? vault-adapters/api-fetch-vault-fills-effect
                  effect-adapters/api-fetch-vault-fills-effect))
  (is (identical? vault-adapters/api-fetch-vault-funding-history-effect
                  effect-adapters/api-fetch-vault-funding-history-effect))
  (is (identical? vault-adapters/api-fetch-vault-order-history-effect
                  effect-adapters/api-fetch-vault-order-history-effect))
  (is (identical? vault-adapters/api-fetch-vault-ledger-updates-effect
                  effect-adapters/api-fetch-vault-ledger-updates-effect)))

(deftest vault-submit-wrapper-injects-order-toast-seam-test
  (let [runtime-store (atom {})
        request {:vault-address "0xvault"}
        transfer-call (atom nil)]
    (letfn [(capture-transfer-call!
              [ctx store* request* opts]
              (reset! transfer-call {:ctx ctx
                                     :store store*
                                     :request request*
                                     :opts opts}))]
      (with-redefs [vault-adapters/api-submit-vault-transfer-effect capture-transfer-call!]
        (effect-adapters/api-submit-vault-transfer-effect nil runtime-store request)))
    (is (nil? (:ctx @transfer-call)))
    (is (identical? runtime-store (:store @transfer-call)))
    (is (= request (:request @transfer-call)))
    (is (fn? (get-in @transfer-call [:opts :show-toast!])))
    ((get-in @transfer-call [:opts :show-toast!]) runtime-store :success "Vault submitted")
    (is (= {:kind :success
            :message "Vault submitted"}
           (get-in @runtime-store [:ui :toast])))))
