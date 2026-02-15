(ns hyperopen.views.trade.order-form-vm
  (:require [hyperopen.state.trading :as trading]
            [hyperopen.trading.order-type-registry :as order-types]
            [hyperopen.views.trade.order-form-vm-selectors :as selectors]
            [hyperopen.views.trade.order-form-vm-submit :as submit]))

(def order-type-config
  order-types/order-type-config)

(defn order-type-label [order-type]
  (order-types/order-type-label order-type))

(defn order-type-sections [order-type]
  (order-types/order-type-sections order-type))

(defn pro-dropdown-options []
  (order-types/pro-order-types))

(defn pro-tab-label [entry-mode order-type]
  (if (= entry-mode :pro)
    (order-type-label order-type)
    "Pro"))

(defn order-form-vm [state]
  (let [normalized-form (trading/order-form-draft state)
        ui-state (trading/order-form-ui-state state)
        runtime-state (trading/order-form-runtime-state state)
        {:keys [base-symbol
                quote-symbol
                spot?
                hip3?
                read-only?
                sz-decimals
                max-leverage] :as market-info}
        (trading/market-info state)
        side (:side normalized-form)
        type (:type normalized-form)
        entry-mode (:entry-mode normalized-form)
        pro-dropdown-open? (boolean (:pro-order-type-dropdown-open? ui-state))
        market-mode? (= entry-mode :market)
        pro-mode? (= entry-mode :pro)
        limit-like? (trading/limit-like-type? type)
        show-limit-like-controls? (selectors/show-limit-like-controls? entry-mode type)
        summary (trading/order-summary state normalized-form)
        summary-display (selectors/summary-display summary sz-decimals)
        ui-leverage (:ui-leverage normalized-form)
        size-percent (trading/clamp-percent (:size-percent normalized-form))
        price (selectors/price-model state normalized-form ui-state limit-like?)
        scale-preview-lines (selectors/scale-preview-lines state
                                                          normalized-form
                                                          base-symbol
                                                          quote-symbol
                                                          sz-decimals)
        submitting? (:submitting? runtime-state)
        submit-policy (trading/submit-policy state normalized-form {:mode :view
                                                                    :submitting? submitting?})
        submit-form (:form submit-policy)
        submit-errors (:errors submit-policy)
        required-submit-fields (:required-fields submit-policy)
        submit-tooltip (submit/submit-tooltip-from-policy submit-policy)
        submit-disabled? (:disabled? submit-policy)]
    {:form normalized-form
     :ui-state ui-state
     :identity market-info
     :side side
     :type type
     :entry-mode entry-mode
     :pro-dropdown-open? pro-dropdown-open?
     :tpsl-panel-open? (boolean (:tpsl-panel-open? ui-state))
     :pro-dropdown-options (pro-dropdown-options)
     :pro-tab-label (pro-tab-label entry-mode type)
     :order-type-sections (order-type-sections type)
     :market-mode? market-mode?
     :pro-mode? pro-mode?
     :limit-like? limit-like?
     :show-limit-like-controls? show-limit-like-controls?
     :spot? spot?
     :hip3? hip3?
     :read-only? read-only?
     :summary summary
     :display summary-display
     :ui-leverage ui-leverage
     :next-leverage (selectors/next-leverage ui-leverage max-leverage)
     :size-percent size-percent
     :display-size-percent (selectors/display-size-percent size-percent)
     :notch-overlap-threshold selectors/notch-overlap-threshold
     :size-display (:size-display normalized-form)
     :price price
     :base-symbol base-symbol
     :quote-symbol quote-symbol
     :scale-preview-lines scale-preview-lines
     :order-value (:order-value summary)
     :margin-required (:margin-required summary)
     :liq-price (:liquidation-price summary)
     :slippage-est (:slippage-est summary)
     :slippage-max (:slippage-max summary)
     :fees (:fees summary)
     :error (:error runtime-state)
     :submitting? submitting?
     :submit {:form submit-form
              :errors submit-errors
              :required-fields required-submit-fields
              :reason (:reason submit-policy)
              :error-message (:error-message submit-policy)
              :tooltip submit-tooltip
              :market-price-missing? (:market-price-missing? submit-policy)
              :disabled? submit-disabled?}}))
