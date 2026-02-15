(ns hyperopen.trading.order-form-application
  (:require [hyperopen.state.trading :as trading]
            [hyperopen.trading.order-type-registry :as order-types]))

(def ^:private order-type-capability-keys
  [:limit-like?
   :supports-tpsl?
   :supports-post-only?
   :show-scale-preview?
   :show-liquidation-row?
   :show-slippage-row?])

(defn- state->order-form-inputs
  [state]
  {:draft (trading/order-form-draft state)
   :ui-state (trading/order-form-ui-state state)
   :runtime-state (trading/order-form-runtime-state state)
   :market-info (trading/market-info state)})

(defn build-order-form-context
  [state {:keys [draft ui-state runtime-state market-info]}]
  (let [order-type (:type draft)
        capabilities (select-keys (order-types/order-type-entry order-type)
                                  order-type-capability-keys)
        summary (trading/order-summary state draft)
        submitting? (:submitting? runtime-state)
        submit-policy (trading/submit-policy state draft {:mode :view
                                                          :submitting? submitting?})]
    {:draft draft
     :ui-state ui-state
     :runtime-state runtime-state
     :market-info market-info
     :order-type-capabilities capabilities
     :summary summary
     :submitting? submitting?
     :submit-policy submit-policy}))

(defn order-form-context [state]
  (build-order-form-context state (state->order-form-inputs state)))
