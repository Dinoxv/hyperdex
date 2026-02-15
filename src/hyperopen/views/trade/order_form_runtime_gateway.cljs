(ns hyperopen.views.trade.order-form-runtime-gateway
  (:require [hyperopen.views.trade.order-form-placeholders :as placeholders]))

(defprotocol OrderFormRuntimeGateway
  (command->runtime-actions [gateway command]))

(def ^:private action-id-by-command-id
  {:order-form/select-entry-mode :actions/select-order-entry-mode
   :order-form/toggle-pro-order-type-dropdown :actions/toggle-pro-order-type-dropdown
   :order-form/close-pro-order-type-dropdown :actions/close-pro-order-type-dropdown
   :order-form/handle-pro-order-type-dropdown-keydown :actions/handle-pro-order-type-dropdown-keydown
   :order-form/select-pro-order-type :actions/select-pro-order-type
   :order-form/set-order-ui-leverage :actions/set-order-ui-leverage
   :order-form/update-order-form :actions/update-order-form
   :order-form/set-order-size-display :actions/set-order-size-display
   :order-form/set-order-size-percent :actions/set-order-size-percent
   :order-form/focus-order-price-input :actions/focus-order-price-input
   :order-form/blur-order-price-input :actions/blur-order-price-input
   :order-form/set-order-price-to-mid :actions/set-order-price-to-mid
   :order-form/toggle-order-tpsl-panel :actions/toggle-order-tpsl-panel
   :order-form/submit-order :actions/submit-order})

(defn- resolve-command-arg [arg]
  (placeholders/resolve-placeholder-token arg))

(defrecord DefaultOrderFormRuntimeGateway []
  OrderFormRuntimeGateway
  (command->runtime-actions [_ {:keys [command-id args] :as command}]
    (let [action-id (get action-id-by-command-id command-id)]
      (when-not action-id
        (throw (js/Error.
                (str "Unknown order-form command id: "
                     (pr-str command-id)
                     " command="
                     (pr-str command)))))
      [(into [action-id] (map resolve-command-arg args))])))

(defn default-gateway []
  (->DefaultOrderFormRuntimeGateway))
