(ns hyperopen.startup.collaborators
  (:require [clojure.string :as str]
            [nexus.registry :as nxr]
            [hyperopen.api :as api]
            [hyperopen.api.endpoints.account :as account-endpoints]
            [hyperopen.api.projections :as api-projections]
            [hyperopen.account.history.effects :as account-history-effects]
            [hyperopen.runtime.api-effects :as runtime-api-effects]
            [hyperopen.runtime.state :as runtime-state]
            [hyperopen.wallet.address-watcher :as address-watcher]
            [hyperopen.websocket.active-asset-ctx :as active-ctx]
            [hyperopen.websocket.client :as ws-client]
            [hyperopen.websocket.orderbook :as orderbook]
            [hyperopen.websocket.trades :as trades]
            [hyperopen.websocket.user :as user-ws]
            [hyperopen.websocket.webdata2 :as webdata2]))

(defn- fetch-frontend-open-orders!
  ([store address]
   (fetch-frontend-open-orders! store address nil {}))
  ([store address dex-or-opts]
   (if (map? dex-or-opts)
     (fetch-frontend-open-orders! store address nil dex-or-opts)
     (fetch-frontend-open-orders! store address dex-or-opts {})))
  ([store address dex opts]
   (-> (api/request-frontend-open-orders! address dex opts)
       (.then (fn [rows]
                (swap! store api-projections/apply-open-orders-success dex rows)
                rows))
       (.catch (fn [err]
                 (swap! store api-projections/apply-open-orders-error err)
                 (js/Promise.reject err))))))

(defn- fetch-clearinghouse-state!
  ([store address dex]
   (fetch-clearinghouse-state! store address dex {}))
  ([store address dex opts]
   (-> (api/request-clearinghouse-state! address dex opts)
       (.then (fn [data]
                (swap! store api-projections/apply-perp-dex-clearinghouse-success dex data)
                data))
       (.catch (fn [err]
                 (swap! store api-projections/apply-perp-dex-clearinghouse-error err)
                 (js/Promise.reject err))))))

(defn- fetch-user-fills!
  ([store address]
   (fetch-user-fills! store address {}))
  ([store address opts]
   (-> (api/request-user-fills! address opts)
       (.then (fn [rows]
                (swap! store api-projections/apply-user-fills-success rows)
                rows))
       (.catch (fn [err]
                 (swap! store api-projections/apply-user-fills-error err)
                 (js/Promise.reject err))))))

(defn- fetch-spot-clearinghouse-state!
  ([store address]
   (fetch-spot-clearinghouse-state! store address {}))
  ([store address opts]
   (if-not address
     (js/Promise.resolve nil)
     (do
       (swap! store api-projections/begin-spot-balances-load)
       (-> (api/request-spot-clearinghouse-state! address opts)
           (.then (fn [data]
                    (swap! store api-projections/apply-spot-balances-success data)
                    data))
           (.catch (fn [err]
                     (swap! store api-projections/apply-spot-balances-error err)
                     (js/Promise.reject err))))))))

(defn- fetch-user-abstraction!
  ([store address]
   (fetch-user-abstraction! store address {}))
  ([store address opts]
   (if-not address
     (js/Promise.resolve {:mode :classic
                          :abstraction-raw nil})
     (let [requested-address (some-> address str str/lower-case)]
       (-> (api/request-user-abstraction! address opts)
           (.then (fn [payload]
                    (let [snapshot {:mode (account-endpoints/normalize-user-abstraction-mode payload)
                                    :abstraction-raw payload}]
                      (swap! store api-projections/apply-user-abstraction-snapshot requested-address snapshot)
                      snapshot)))
           (.catch (fn [err]
                     (js/Promise.reject err))))))))

(defn- ensure-perp-dexs!
  ([store]
   (ensure-perp-dexs! store {}))
  ([store opts]
   (-> (api/ensure-perp-dexs-data! store opts)
       (.then (fn [dexs]
                (swap! store api-projections/apply-perp-dexs-success dexs)
                dexs))
       (.catch (fn [err]
                 (swap! store api-projections/apply-perp-dexs-error err)
                 (js/Promise.reject err))))))

(defn- fetch-asset-contexts!
  ([store]
   (fetch-asset-contexts! store {}))
  ([store opts]
   (-> (api/request-asset-contexts! opts)
       (.then (fn [rows]
                (swap! store api-projections/apply-asset-contexts-success rows)
                rows))
       (.catch (fn [err]
                 (swap! store api-projections/apply-asset-contexts-error err)
                 (js/Promise.reject err))))))

(defn- fetch-asset-selector-markets!
  ([store]
   (fetch-asset-selector-markets! store {:phase :full}))
  ([store opts]
   (runtime-api-effects/fetch-asset-selector-markets!
    {:store store
     :opts opts
     :request-asset-selector-markets-fn api/request-asset-selector-markets!
     :begin-asset-selector-load api-projections/begin-asset-selector-load
     :apply-asset-selector-success api-projections/apply-asset-selector-success
     :apply-asset-selector-error api-projections/apply-asset-selector-error})))

(defn startup-base-deps
  [overrides]
  (merge
   {:log-fn println
    :get-request-stats api/get-request-stats
    :fetch-frontend-open-orders! fetch-frontend-open-orders!
    :fetch-clearinghouse-state! fetch-clearinghouse-state!
    :fetch-user-fills! fetch-user-fills!
    :fetch-spot-clearinghouse-state! fetch-spot-clearinghouse-state!
    :fetch-user-abstraction! fetch-user-abstraction!
    :fetch-and-merge-funding-history! account-history-effects/fetch-and-merge-funding-history!
    :ensure-perp-dexs! ensure-perp-dexs!
    :fetch-asset-contexts! fetch-asset-contexts!
    :fetch-asset-selector-markets! fetch-asset-selector-markets!
    :ws-url runtime-state/websocket-url
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
