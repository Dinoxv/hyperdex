(ns hyperopen.views.trade.order-form-handlers
  (:require [hyperopen.views.trade.order-form-commands :as cmd]))

(defn build-handlers []
  {:entry-mode {:on-close-dropdown (cmd/close-pro-order-type-dropdown)
                :on-select-entry-market (cmd/select-entry-market)
                :on-select-entry-limit (cmd/select-entry-limit)
                :on-toggle-dropdown (cmd/toggle-pro-order-type-dropdown)
                :on-dropdown-keydown (cmd/handle-pro-order-type-dropdown-keydown [:event/key])
                :on-select-pro-order-type cmd/select-pro-order-type}

   :leverage {:on-next-leverage cmd/set-order-ui-leverage}

   :side {:on-select-side cmd/set-order-side}

   :price {:on-set-to-mid (cmd/set-order-price-to-mid)
           :on-focus (cmd/focus-order-price-input)
           :on-blur (cmd/blur-order-price-input)
           :on-change (cmd/set-limit-price-input)}

   :size {:on-change-display (cmd/set-order-size-display-input)
          :on-change-percent (cmd/set-order-size-percent-input)}

   :order-type-sections {:on-set-trigger-price (cmd/set-trigger-price-input)
                         :on-set-scale-start (cmd/set-scale-start-input)
                         :on-set-scale-end (cmd/set-scale-end-input)
                         :on-set-scale-count (cmd/set-scale-count-input)
                         :on-set-scale-skew (cmd/set-scale-skew-input)
                         :on-set-twap-minutes (cmd/set-twap-minutes-input)
                         :on-toggle-twap-randomize (cmd/toggle-twap-randomize)}

   :toggles {:on-toggle-reduce-only (cmd/toggle-reduce-only)
             :on-toggle-post-only (cmd/toggle-post-only)
             :on-toggle-tpsl-panel (cmd/toggle-order-tpsl-panel)}

   :tif {:on-set-tif (cmd/set-tif-input)}

   :tp-sl {:on-toggle-tp-enabled (cmd/toggle-tp-enabled)
           :on-set-tp-trigger (cmd/set-tp-trigger-input)
           :on-toggle-tp-market (cmd/toggle-tp-market)
           :on-set-tp-limit (cmd/set-tp-limit-input)
           :on-toggle-sl-enabled (cmd/toggle-sl-enabled)
           :on-set-sl-trigger (cmd/set-sl-trigger-input)
           :on-toggle-sl-market (cmd/toggle-sl-market)
           :on-set-sl-limit (cmd/set-sl-limit-input)}

   :submit {:on-submit (cmd/submit-order)}})
