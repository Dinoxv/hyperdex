(ns hyperopen.core
  (:require-macros [hyperopen.core.macros :refer [def-public-action-aliases]])
  (:require [replicant.dom :as r]
            [nexus.registry :as nxr]
            [hyperopen.views.app-view :as app-view]
            [hyperopen.websocket.client :as ws-client]
            [hyperopen.account.history.actions :as account-history-actions]
            [hyperopen.core.public-actions :as public-actions]
            [hyperopen.asset-selector.settings :as asset-selector-settings]
            [hyperopen.registry.runtime :as runtime-registry]
            [hyperopen.runtime.action-adapters :as action-adapters]
            [hyperopen.runtime.bootstrap :as runtime-bootstrap]
            [hyperopen.runtime.collaborators :as runtime-collaborators]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.runtime.registry-composition :as registry-composition]
            [hyperopen.runtime.state :as runtime-state]
            [hyperopen.startup.collaborators :as startup-collaborators]
            [hyperopen.startup.composition :as startup-composition]
            [hyperopen.startup.runtime :as startup-runtime-lib]
            [hyperopen.startup.watchers :as startup-watchers]
            [hyperopen.wallet.core :as wallet]
            [hyperopen.wallet.agent-session :as agent-session]
            [hyperopen.wallet.address-watcher :as address-watcher]
            [hyperopen.router :as router]
            [hyperopen.state.app-defaults :as app-defaults]
            [hyperopen.state.trading :as trading]))

(def ^:private default-funding-history-state
  account-history-actions/default-funding-history-state)

(def ^:private default-order-history-state
  account-history-actions/default-order-history-state)

(def ^:private default-trade-history-state
  account-history-actions/default-trade-history-state)

;; App state
(defonce store
  (atom
   (app-defaults/default-app-state
    {:websocket-health (ws-client/get-health-snapshot)
     :default-agent-state (agent-session/default-agent-state)
     :default-order-form (trading/default-order-form)
     :default-trade-history (default-trade-history-state)
     :default-funding-history (default-funding-history-state)
     :default-order-history (default-order-history-state)})))

;; Re-export runtime effect/action adapters so `hyperopen.core/*` remains stable
;; while implementation details live in dedicated runtime namespaces.
(def-public-action-aliases
  effect-adapters
  [append-diagnostics-event!
   sync-websocket-health!
   save
   save-many
   local-storage-set
   local-storage-set-json
   schedule-animation-frame!
   flush-queued-asset-icon-statuses!
   queue-asset-icon-status
   push-state
   replace-state
   fetch-candle-snapshot
   init-websocket
   persist-asset-selector-markets-cache!
   restore-asset-selector-markets-cache!
   persist-active-market-display!
   load-active-market-display
   subscribe-active-asset
   unsubscribe-active-asset
   subscribe-orderbook
   subscribe-trades
   unsubscribe-orderbook
   unsubscribe-trades
   subscribe-webdata2
   unsubscribe-webdata2
   connect-wallet
   disconnect-wallet
   set-agent-storage-mode
   copy-wallet-address
   reconnect-websocket
   refresh-websocket-health
   ws-reset-subscriptions
   confirm-ws-diagnostics-reveal
   copy-websocket-diagnostics
   restore-active-asset!
   api-submit-order
   api-cancel-order
   fetch-asset-selector-markets-effect
   api-load-user-data-effect])

(def-public-action-aliases
  action-adapters
  [init-websockets
   subscribe-to-asset
   subscribe-to-webdata2
   connect-wallet-action
   disconnect-wallet-action
   should-auto-enable-agent-trading?
   handle-wallet-connected
   enable-agent-trading
   enable-agent-trading-action
   set-agent-storage-mode-action
   copy-wallet-address-action
   reconnect-websocket-action
   toggle-ws-diagnostics
   close-ws-diagnostics
   toggle-ws-diagnostics-sensitive
   ws-diagnostics-reconnect-now
   ws-diagnostics-copy
   set-show-surface-freshness-cues
   toggle-show-surface-freshness-cues
   ws-diagnostics-reset-market-subscriptions
   ws-diagnostics-reset-orders-subscriptions
   ws-diagnostics-reset-all-subscriptions])

;; Re-export action aliases from a dedicated module to keep this namespace thin
;; while preserving the legacy `hyperopen.core/*` API surface.
(def-public-action-aliases
  public-actions
  [toggle-asset-dropdown
   close-asset-dropdown
   select-asset
   update-asset-search
   update-asset-selector-sort
   toggle-asset-selector-strict
   toggle-asset-favorite
   set-asset-selector-favorites-only
   set-asset-selector-tab
   set-asset-selector-scroll-top
   increase-asset-selector-render-limit
   show-all-asset-selector-markets
   maybe-increase-asset-selector-render-limit
   refresh-asset-markets
   apply-asset-icon-status-updates
   mark-loaded-asset-icon
   mark-missing-asset-icon
   restore-open-orders-sort-settings!
   restore-order-history-pagination-settings!
   restore-funding-history-pagination-settings!
   restore-trade-history-pagination-settings!
   restore-chart-options!
   restore-orderbook-ui!
   restore-agent-storage-mode!
   restore-ui-font-preference!
   toggle-timeframes-dropdown
   select-chart-timeframe
   toggle-chart-type-dropdown
   select-chart-type
   toggle-indicators-dropdown
   toggle-orderbook-size-unit-dropdown
   select-orderbook-size-unit
   toggle-orderbook-price-aggregation-dropdown
   select-orderbook-price-aggregation
   select-orderbook-tab
   add-indicator
   remove-indicator
   update-indicator-period
   select-account-info-tab
   set-funding-history-filters
   toggle-funding-history-filter-open
   toggle-funding-history-filter-coin
   reset-funding-history-filter-draft
   apply-funding-history-filters
   view-all-funding-history
   export-funding-history-csv
   sort-positions
   sort-balances
   sort-open-orders
   sort-funding-history
   set-funding-history-page-size
   set-funding-history-page
   next-funding-history-page
   prev-funding-history-page
   set-funding-history-page-input
   apply-funding-history-page-input
   handle-funding-history-page-input-keydown
   set-trade-history-page-size
   set-trade-history-page
   next-trade-history-page
   prev-trade-history-page
   set-trade-history-page-input
   apply-trade-history-page-input
   handle-trade-history-page-input-keydown
   sort-trade-history
   sort-order-history
   toggle-order-history-filter-open
   set-order-history-status-filter
   set-order-history-page-size
   set-order-history-page
   next-order-history-page
   prev-order-history-page
   set-order-history-page-input
   apply-order-history-page-input
   handle-order-history-page-input-keydown
   refresh-order-history
   set-hide-small-balances
   select-order-entry-mode
   select-pro-order-type
   toggle-pro-order-type-dropdown
   close-pro-order-type-dropdown
   handle-pro-order-type-dropdown-keydown
   set-order-ui-leverage
   set-order-size-percent
   set-order-size-display
   focus-order-price-input
   blur-order-price-input
   set-order-price-to-mid
   toggle-order-tpsl-panel
   update-order-form
   submit-order
   prune-canceled-open-orders
   cancel-order
   load-user-data
   set-funding-modal])

(defn navigate
  [state path & [opts]]
  (let [p (router/normalize-path path)
        replace? (boolean (:replace? opts))]
    (cond-> [[:effects/save [:router :path] p]]
      replace? (conj [:effects/replace-state p])
      (not replace?) (conj [:effects/push-state p]))))

(defn- runtime-effect-deps
  []
  (runtime-collaborators/runtime-effect-deps
   {:save save
    :save-many save-many
    :local-storage-set local-storage-set
    :local-storage-set-json local-storage-set-json
    :queue-asset-icon-status queue-asset-icon-status
    :push-state push-state
    :replace-state replace-state
    :init-websocket init-websocket
    :subscribe-active-asset subscribe-active-asset
    :subscribe-orderbook subscribe-orderbook
    :subscribe-trades subscribe-trades
    :subscribe-webdata2 subscribe-webdata2
    :fetch-candle-snapshot fetch-candle-snapshot
    :unsubscribe-active-asset unsubscribe-active-asset
    :unsubscribe-orderbook unsubscribe-orderbook
    :unsubscribe-trades unsubscribe-trades
    :unsubscribe-webdata2 unsubscribe-webdata2
    :connect-wallet connect-wallet
    :disconnect-wallet disconnect-wallet
    :enable-agent-trading enable-agent-trading
    :set-agent-storage-mode set-agent-storage-mode
    :copy-wallet-address copy-wallet-address
    :reconnect-websocket reconnect-websocket
    :refresh-websocket-health refresh-websocket-health
    :confirm-ws-diagnostics-reveal confirm-ws-diagnostics-reveal
    :copy-websocket-diagnostics copy-websocket-diagnostics
    :ws-reset-subscriptions ws-reset-subscriptions
    :fetch-asset-selector-markets fetch-asset-selector-markets-effect
    :api-submit-order api-submit-order
    :api-cancel-order api-cancel-order
    :api-load-user-data api-load-user-data-effect}))

(defn- runtime-action-deps
  []
  (runtime-collaborators/runtime-action-deps
   {:init-websockets init-websockets
    :subscribe-to-asset subscribe-to-asset
    :subscribe-to-webdata2 subscribe-to-webdata2
    :enable-agent-trading-action enable-agent-trading-action
    :set-agent-storage-mode-action set-agent-storage-mode-action
    :reconnect-websocket-action reconnect-websocket-action
    :toggle-ws-diagnostics toggle-ws-diagnostics
    :close-ws-diagnostics close-ws-diagnostics
    :toggle-ws-diagnostics-sensitive toggle-ws-diagnostics-sensitive
    :ws-diagnostics-reconnect-now ws-diagnostics-reconnect-now
    :ws-diagnostics-copy ws-diagnostics-copy
    :set-show-surface-freshness-cues set-show-surface-freshness-cues
    :toggle-show-surface-freshness-cues toggle-show-surface-freshness-cues
    :ws-diagnostics-reset-market-subscriptions ws-diagnostics-reset-market-subscriptions
    :ws-diagnostics-reset-orders-subscriptions ws-diagnostics-reset-orders-subscriptions
    :ws-diagnostics-reset-all-subscriptions ws-diagnostics-reset-all-subscriptions
    :refresh-asset-markets refresh-asset-markets
    :load-user-data load-user-data
    :set-funding-modal set-funding-modal
    :navigate navigate}))

(defn- runtime-registration-deps
  []
  (registry-composition/runtime-registration-deps
   {:register-effects! runtime-registry/register-effects!
    :register-actions! runtime-registry/register-actions!
    :register-system-state! runtime-registry/register-system-state!
    :register-placeholders! runtime-registry/register-placeholders!}
   {:effect-deps (runtime-effect-deps)
    :action-deps (runtime-action-deps)}))

(defn- register-runtime!
  []
  (runtime-bootstrap/register-runtime! (runtime-registration-deps)))

(defn- render-app!
  [state]
  (when (exists? js/document)
    (r/render (.getElementById js/document "app")
              (app-view/app-view state))))

(defn- store-cache-watcher-deps
  []
  {:persist-active-market-display! persist-active-market-display!
   :persist-asset-selector-markets-cache! persist-asset-selector-markets-cache!})

(defn- websocket-watcher-deps
  []
  {:store store
   :connection-state ws-client/connection-state
   :stream-runtime ws-client/stream-runtime
   :append-diagnostics-event! append-diagnostics-event!
   :sync-websocket-health! sync-websocket-health!
   :on-websocket-connected! address-watcher/on-websocket-connected!
   :on-websocket-disconnected! address-watcher/on-websocket-disconnected!})

(defn- bootstrap-runtime!
  []
  (runtime-bootstrap/bootstrap-runtime!
   {:register-runtime-deps (runtime-registration-deps)
    :render-loop-deps {:store store
                       :render-watch-key ::render
                       :set-dispatch! r/set-dispatch!
                       :dispatch! nxr/dispatch
                       :render! render-app!
                       :document? (exists? js/document)}
    :watchers-deps {:store store
                    :install-store-cache-watchers! startup-watchers/install-store-cache-watchers!
                    :store-cache-watchers-deps (store-cache-watcher-deps)
                    :install-websocket-watchers! startup-watchers/install-websocket-watchers!
                    :websocket-watchers-deps (websocket-watcher-deps)}}))

(defn- ensure-runtime-bootstrapped!
  []
  (when (compare-and-set! runtime-state/runtime-bootstrapped? false true)
    (bootstrap-runtime!)))

(defn reload []
  (ensure-runtime-bootstrapped!)
  (println "Reloading Hyperopen...")
  (wallet/set-on-connected-handler! handle-wallet-connected)
  (render-app! @store))

(defonce ^:private startup-runtime
  (atom (startup-runtime-lib/default-startup-runtime-state)))

(defn- mark-performance!
  [mark-name]
  (startup-runtime-lib/mark-performance! mark-name))

(defn- schedule-idle-or-timeout!
  [f]
  (startup-runtime-lib/schedule-idle-or-timeout! runtime-state/deferred-bootstrap-delay-ms f))

(defn- startup-base-deps
  []
  (startup-collaborators/startup-base-deps
   {:startup-runtime startup-runtime
    :store store
    :icon-service-worker-path runtime-state/icon-service-worker-path
    :per-dex-stagger-ms runtime-state/per-dex-stagger-ms
    :schedule-idle-or-timeout! schedule-idle-or-timeout!
    :mark-performance! mark-performance!}))

(defn- schedule-startup-summary-log!
  []
  (startup-composition/schedule-startup-summary-log!
   (assoc (startup-base-deps)
          :delay-ms runtime-state/startup-summary-delay-ms)))

(defn- register-icon-service-worker!
  []
  (startup-composition/register-icon-service-worker!
   (startup-base-deps)))

(defn- stage-b-account-bootstrap!
  [address dexs]
  (startup-composition/stage-b-account-bootstrap!
   (startup-base-deps)
   address
   dexs))

(defn- bootstrap-account-data!
  [address]
  (startup-composition/bootstrap-account-data!
   (assoc (startup-base-deps)
          :stage-b-account-bootstrap! stage-b-account-bootstrap!)
   address))

(defn- reify-address-handler
  [on-address-changed-fn handler-name]
  (reify address-watcher/IAddressChangeHandler
    (on-address-changed [_ _ new-address]
      (on-address-changed-fn new-address))
    (get-handler-name [_]
      handler-name)))

(defn- install-address-handlers!
  []
  (startup-composition/install-address-handlers!
   (assoc (startup-base-deps)
          :bootstrap-account-data! bootstrap-account-data!
          :address-handler-reify reify-address-handler
          :address-handler-name "startup-account-bootstrap-handler")))

(defn- start-critical-bootstrap!
  []
  (startup-composition/start-critical-bootstrap!
   (startup-base-deps)))

(defn- run-deferred-bootstrap!
  []
  (startup-composition/run-deferred-bootstrap!
   (startup-base-deps)))

(defn- schedule-deferred-bootstrap!
  []
  (startup-composition/schedule-deferred-bootstrap!
   (assoc (startup-base-deps)
          :run-deferred-bootstrap! run-deferred-bootstrap!)))

(defn initialize-remote-data-streams!
  []
  (startup-composition/initialize-remote-data-streams!
   (assoc (startup-base-deps)
          :install-address-handlers! install-address-handlers!
          :start-critical-bootstrap! start-critical-bootstrap!
          :schedule-deferred-bootstrap! schedule-deferred-bootstrap!)))

(defn init []
  (ensure-runtime-bootstrapped!)
  (startup-composition/init!
   (merge
    (startup-base-deps)
    {:default-startup-runtime-state startup-runtime-lib/default-startup-runtime-state
     :schedule-startup-summary-log! schedule-startup-summary-log!
     :restore-ui-font-preference! restore-ui-font-preference!
     :restore-asset-selector-sort-settings! asset-selector-settings/restore-asset-selector-sort-settings!
     :restore-chart-options! restore-chart-options!
     :restore-orderbook-ui! restore-orderbook-ui!
     :restore-agent-storage-mode! restore-agent-storage-mode!
     :restore-active-asset! restore-active-asset!
     :restore-asset-selector-markets-cache! restore-asset-selector-markets-cache!
     :restore-open-orders-sort-settings! restore-open-orders-sort-settings!
     :restore-funding-history-pagination-settings! restore-funding-history-pagination-settings!
     :restore-trade-history-pagination-settings! restore-trade-history-pagination-settings!
     :restore-order-history-pagination-settings! restore-order-history-pagination-settings!
     :set-on-connected-handler! wallet/set-on-connected-handler!
     :handle-wallet-connected handle-wallet-connected
     :init-wallet! wallet/init-wallet!
     :init-router! router/init!
     :register-icon-service-worker! register-icon-service-worker!
     :initialize-remote-data-streams! initialize-remote-data-streams!
     :kick-render! (fn [runtime-store]
                     (swap! runtime-store identity))})))
