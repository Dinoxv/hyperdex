(ns hyperopen.views.trade.order-form-commands)

(defn select-entry-mode [mode]
  [[:actions/select-order-entry-mode mode]])

(defn select-entry-market []
  (select-entry-mode :market))

(defn select-entry-limit []
  (select-entry-mode :limit))

(defn toggle-pro-order-type-dropdown []
  [[:actions/toggle-pro-order-type-dropdown]])

(defn close-pro-order-type-dropdown []
  [[:actions/close-pro-order-type-dropdown]])

(defn handle-pro-order-type-dropdown-keydown [event-key]
  [[:actions/handle-pro-order-type-dropdown-keydown event-key]])

(defn select-pro-order-type [order-type]
  [[:actions/select-pro-order-type order-type]])

(defn set-order-ui-leverage [leverage]
  [[:actions/set-order-ui-leverage leverage]])

(defn- update-order-field [path value]
  [[:actions/update-order-form path value]])

(defn update-order-form [path value]
  (update-order-field path value))

(defn set-order-side [side]
  (update-order-field [:side] side))

(defn set-limit-price-input []
  (update-order-field [:price] [:event.target/value]))

(defn set-order-size-display-input []
  [[:actions/set-order-size-display [:event.target/value]]])

(defn set-order-size-percent-input []
  [[:actions/set-order-size-percent [:event.target/value]]])

(defn focus-order-price-input []
  [[:actions/focus-order-price-input]])

(defn blur-order-price-input []
  [[:actions/blur-order-price-input]])

(defn set-order-price-to-mid []
  [[:actions/set-order-price-to-mid]])

(defn toggle-order-tpsl-panel []
  [[:actions/toggle-order-tpsl-panel]])

(defn toggle-reduce-only []
  (update-order-field [:reduce-only] [:event.target/checked]))

(defn toggle-post-only []
  (update-order-field [:post-only] [:event.target/checked]))

(defn set-tif-input []
  (update-order-field [:tif] [:event.target/value]))

(defn set-trigger-price-input []
  (update-order-field [:trigger-px] [:event.target/value]))

(defn set-scale-start-input []
  (update-order-field [:scale :start] [:event.target/value]))

(defn set-scale-end-input []
  (update-order-field [:scale :end] [:event.target/value]))

(defn set-scale-count-input []
  (update-order-field [:scale :count] [:event.target/value]))

(defn set-scale-skew-input []
  (update-order-field [:scale :skew] [:event.target/value]))

(defn set-twap-minutes-input []
  (update-order-field [:twap :minutes] [:event.target/value]))

(defn toggle-twap-randomize []
  (update-order-field [:twap :randomize] [:event.target/checked]))

(defn toggle-tp-enabled []
  (update-order-field [:tp :enabled?] [:event.target/checked]))

(defn set-tp-trigger-input []
  (update-order-field [:tp :trigger] [:event.target/value]))

(defn toggle-tp-market []
  (update-order-field [:tp :is-market] [:event.target/checked]))

(defn set-tp-limit-input []
  (update-order-field [:tp :limit] [:event.target/value]))

(defn toggle-sl-enabled []
  (update-order-field [:sl :enabled?] [:event.target/checked]))

(defn set-sl-trigger-input []
  (update-order-field [:sl :trigger] [:event.target/value]))

(defn toggle-sl-market []
  (update-order-field [:sl :is-market] [:event.target/checked]))

(defn set-sl-limit-input []
  (update-order-field [:sl :limit] [:event.target/value]))

(defn submit-order []
  [[:actions/submit-order]])
