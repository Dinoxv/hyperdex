(ns hyperopen.runtime.action-adapters.websocket)

(defn init-websockets
  [_state]
  [[:effects/init-websocket]])

(defn subscribe-to-asset
  [_state coin]
  [[:effects/subscribe-active-asset coin]
   [:effects/subscribe-orderbook coin]
   [:effects/subscribe-trades coin]
   [:effects/sync-active-asset-funding-predictability coin]])

(defn subscribe-to-webdata2
  [_state address]
  [[:effects/subscribe-webdata2 address]])

(defn refresh-asset-markets
  [_state]
  [[:effects/fetch-asset-selector-markets]])

(defn load-user-data
  [_state address]
  [[:effects/api-load-user-data address]])

(defn reconnect-websocket-action
  [_state]
  [[:effects/reconnect-websocket]])
