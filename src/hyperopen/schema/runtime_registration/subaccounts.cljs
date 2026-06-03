(ns hyperopen.schema.runtime-registration.subaccounts)

(def effect-binding-rows
  [[:effects/api-load-subaccounts :api-load-subaccounts]
   [:effects/api-create-subaccount :api-create-subaccount]
   [:effects/api-rename-subaccount :api-rename-subaccount]
   [:effects/api-transfer-subaccount :api-transfer-subaccount]])

(def action-binding-rows
  [[:actions/load-subaccounts-route :load-subaccounts-route]
   [:actions/select-subaccount :select-subaccount]
   [:actions/select-master-account :select-master-account]
   [:actions/set-subaccount-form-field :set-subaccount-form-field]
   [:actions/open-subaccount-create-popover :open-subaccount-create-popover]
   [:actions/close-subaccount-create-popover :close-subaccount-create-popover]
   [:actions/copy-subaccount-address :copy-subaccount-address]
   [:actions/submit-create-subaccount :submit-create-subaccount]
   [:actions/start-rename-subaccount :start-rename-subaccount]
   [:actions/cancel-rename-subaccount :cancel-rename-subaccount]
   [:actions/submit-rename-subaccount :submit-rename-subaccount]
   [:actions/start-transfer-subaccount :start-transfer-subaccount]
   [:actions/cancel-transfer-subaccount :cancel-transfer-subaccount]
   [:actions/submit-transfer-subaccount :submit-transfer-subaccount]])
