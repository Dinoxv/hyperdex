(ns hyperopen.startup.collaborators
  (:require [nexus.registry :as nxr]
            [hyperopen.api :as api]
            [hyperopen.account.history.effects :as account-history-effects]
            [hyperopen.wallet.address-watcher :as address-watcher]
            [hyperopen.websocket.active-asset-ctx :as active-ctx]
            [hyperopen.websocket.client :as ws-client]
            [hyperopen.websocket.orderbook :as orderbook]
            [hyperopen.websocket.trades :as trades]
            [hyperopen.websocket.user :as user-ws]
            [hyperopen.websocket.webdata2 :as webdata2]))

(def ^:private default-ws-url
  "wss://api.hyperliquid.xyz/ws")

(defn startup-base-deps
  [overrides]
  (merge
   {:log-fn println
    :get-request-stats api/get-request-stats
    :fetch-frontend-open-orders! api/fetch-frontend-open-orders!
    :fetch-clearinghouse-state! api/fetch-clearinghouse-state!
    :fetch-user-fills! api/fetch-user-fills!
    :fetch-spot-clearinghouse-state! api/fetch-spot-clearinghouse-state!
    :fetch-user-abstraction! api/fetch-user-abstraction!
    :fetch-and-merge-funding-history! account-history-effects/fetch-and-merge-funding-history!
    :ensure-perp-dexs! api/ensure-perp-dexs!
    :fetch-asset-contexts! api/fetch-asset-contexts!
    :fetch-asset-selector-markets! api/fetch-asset-selector-markets!
    :ws-url default-ws-url
    :init-connection! ws-client/init-connection!
    :init-active-ctx! active-ctx/init!
    :init-orderbook! orderbook/init!
    :init-trades! trades/init!
    :init-user-ws! user-ws/init!
    :init-webdata2! webdata2/init!
    :dispatch! nxr/dispatch
    :init-with-webdata2! address-watcher/init-with-webdata2!
    :add-handler! address-watcher/add-handler!
    :sync-current-address! address-watcher/sync-current-address!
    :create-user-handler user-ws/create-user-handler
    :subscribe-user! user-ws/subscribe-user!
    :unsubscribe-user! user-ws/unsubscribe-user!
    :subscribe-webdata2! webdata2/subscribe-webdata2!
    :unsubscribe-webdata2! webdata2/unsubscribe-webdata2!}
   overrides))
