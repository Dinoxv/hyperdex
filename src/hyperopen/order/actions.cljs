(ns hyperopen.order.actions
  (:require [hyperopen.api.trading :as trading-api]
            [hyperopen.order.effects :as order-effects]
            [hyperopen.state.trading :as trading]
            [hyperopen.trading.order-form-transitions :as transitions]))

(defn- transition-save-many
  [{:keys [order-form order-form-ui order-form-runtime]}]
  (let [path-values (cond-> []
                      (map? order-form) (conj [[:order-form] order-form])
                      (map? order-form-ui) (conj [[:order-form-ui] order-form-ui])
                      (map? order-form-runtime) (conj [[:order-form-runtime] order-form-runtime]))]
    (if (seq path-values)
      [[:effects/save-many path-values]]
      [])))

(defn select-order-entry-mode [state mode]
  (-> state
      (transitions/select-entry-mode mode)
      transition-save-many))

(defn select-pro-order-type [state order-type]
  (-> state
      (transitions/select-pro-order-type order-type)
      transition-save-many))

(defn toggle-pro-order-type-dropdown [state]
  (let [ui-state (:order-form-ui (transitions/toggle-pro-order-type-dropdown state))
        next-open? (boolean (:pro-order-type-dropdown-open? ui-state))]
    [[:effects/save-many [[[:order-form-ui :pro-order-type-dropdown-open?] next-open?]]]]))

(defn close-pro-order-type-dropdown [state]
  (let [ui-state (:order-form-ui (transitions/close-pro-order-type-dropdown state))]
    [[:effects/save-many [[[:order-form-ui :pro-order-type-dropdown-open?]
                           (boolean (:pro-order-type-dropdown-open? ui-state))]]]]))

(defn handle-pro-order-type-dropdown-keydown [state key]
  (if-let [transition (transitions/handle-pro-order-type-dropdown-keydown state key)]
    (let [ui-state (:order-form-ui transition)]
      [[:effects/save-many [[[:order-form-ui :pro-order-type-dropdown-open?]
                             (boolean (:pro-order-type-dropdown-open? ui-state))]]]])
    []))

(defn set-order-ui-leverage [state leverage]
  (-> state
      (transitions/set-order-ui-leverage leverage)
      transition-save-many))

(defn set-order-size-percent [state percent]
  (-> state
      (transitions/set-order-size-percent percent)
      transition-save-many))

(defn set-order-size-display [state value]
  (-> state
      (transitions/set-order-size-display value)
      transition-save-many))

(defn focus-order-price-input [state]
  (-> state
      transitions/focus-order-price-input
      transition-save-many))

(defn blur-order-price-input [state]
  (-> state
      transitions/blur-order-price-input
      transition-save-many))

(defn set-order-price-to-mid [state]
  (-> state
      transitions/set-order-price-to-mid
      transition-save-many))

(defn toggle-order-tpsl-panel [state]
  (if-let [transition (transitions/toggle-order-tpsl-panel state)]
    (transition-save-many transition)
    []))

(defn update-order-form [state path value]
  (-> state
      (transitions/update-order-form path value)
      transition-save-many))

(defn submit-order [state]
  (let [raw-form (:order-form state)
        agent-ready? (= :ready (get-in state [:wallet :agent :status]))
        submit-policy (trading/submit-policy state raw-form {:mode :submit
                                                             :agent-ready? agent-ready?})
        form (:form submit-policy)
        request (:request submit-policy)
        reason (:reason submit-policy)
        error-message (:error-message submit-policy)]
    (cond
      reason
      [[:effects/save [:order-form-runtime :error] error-message]]

      :else
      [[:effects/save [:order-form-runtime :error] nil]
       [:effects/save [:order-form] form]
       [:effects/api-submit-order request]])))

(defn prune-canceled-open-orders
  [state request]
  (order-effects/prune-canceled-open-orders state request))

(defn cancel-order [state order]
  (let [agent-ready? (= :ready (get-in state [:wallet :agent :status]))
        request (trading-api/build-cancel-order-request state order)]
    (cond
      (not agent-ready?)
      [[:effects/save [:orders :cancel-error] "Enable trading before cancelling orders."]]

      (map? request)
      [[:effects/api-cancel-order request]]

      :else
      [[:effects/save [:orders :cancel-error] "Missing asset or order id."]])))
