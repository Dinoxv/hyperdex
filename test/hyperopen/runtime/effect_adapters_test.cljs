(ns hyperopen.runtime.effect-adapters-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.runtime.app-effects :as app-effects]
            [hyperopen.runtime.effect-adapters :as effect-adapters]
            [hyperopen.websocket.subscriptions-runtime :as subscriptions-runtime]))

(deftest subscribe-active-asset-persists-through-local-storage-effect-boundary-test
  (let [persist-calls (atom [])
        store (atom {:asset-selector {:market-by-key {}}
                     :chart-options {:selected-timeframe :1d}})]
    (with-redefs [app-effects/local-storage-set!
                  (fn [key value]
                    (swap! persist-calls conj [key value]))
                  subscriptions-runtime/subscribe-active-asset!
                  (fn [{:keys [persist-active-asset!]}]
                    (persist-active-asset! "ETH"))]
      (effect-adapters/subscribe-active-asset nil store "ETH"))
    (is (= [["active-asset" "ETH"]] @persist-calls))))

(deftest core-effect-handler-adapters-preserve-dispatch-signatures-test
  (let [calls (atom {:save nil
                     :save-many nil
                     :local-storage-set nil
                     :local-storage-set-json nil
                     :push-state nil
                     :replace-state nil})
        store (atom {})]
    (with-redefs [app-effects/save!
                  (fn [store* path value]
                    (swap! calls assoc :save [store* path value]))
                  app-effects/save-many!
                  (fn [store* path-values]
                    (swap! calls assoc :save-many [store* path-values]))
                  app-effects/local-storage-set!
                  (fn [key value]
                    (swap! calls assoc :local-storage-set [key value]))
                  app-effects/local-storage-set-json!
                  (fn [key value]
                    (swap! calls assoc :local-storage-set-json [key value]))
                  app-effects/push-state!
                  (fn [path]
                    (swap! calls assoc :push-state path))
                  app-effects/replace-state!
                  (fn [path]
                    (swap! calls assoc :replace-state path))]
      (effect-adapters/save :ctx store [:router :path] "/trade")
      (effect-adapters/save-many :ctx store [[[:router :path] "/wallet"]])
      (effect-adapters/local-storage-set :ctx store "active-asset" "ETH")
      (effect-adapters/local-storage-set-json :ctx store "asset-favorites" {:ETH true})
      (effect-adapters/push-state :ctx store "/trade")
      (effect-adapters/replace-state :ctx store "/wallet"))
    (is (= [store [:router :path] "/trade"]
           (:save @calls)))
    (is (= [store [[[:router :path] "/wallet"]]]
           (:save-many @calls)))
    (is (= ["active-asset" "ETH"]
           (:local-storage-set @calls)))
    (is (= ["asset-favorites" {:ETH true}]
           (:local-storage-set-json @calls)))
    (is (= "/trade" (:push-state @calls)))
    (is (= "/wallet" (:replace-state @calls)))))

(deftest make-init-and-reconnect-websocket-build-injected-effect-handlers-test
  (let [calls (atom [])
        log-fn (fn [& _] nil)
        init-connection! (fn [& _] nil)
        force-reconnect! (fn [] nil)
        store (atom {})
        init-websocket (effect-adapters/make-init-websocket {:ws-url "wss://custom.test/ws"
                                                              :log-fn log-fn
                                                              :init-connection! init-connection!})
        reconnect-websocket (effect-adapters/make-reconnect-websocket {:log-fn log-fn
                                                                        :force-reconnect! force-reconnect!})]
    (with-redefs [app-effects/init-websocket!
                  (fn [opts]
                    (swap! calls conj [:init opts]))
                  app-effects/reconnect-websocket!
                  (fn [opts]
                    (swap! calls conj [:reconnect opts]))]
      (init-websocket :ctx store)
      (reconnect-websocket :ctx store))
    (let [[_ init-opts] (first @calls)
          [_ reconnect-opts] (second @calls)]
      (is (= store (:store init-opts)))
      (is (= "wss://custom.test/ws" (:ws-url init-opts)))
      (is (identical? log-fn (:log-fn init-opts)))
      (is (identical? init-connection! (:init-connection! init-opts)))
      (is (identical? log-fn (:log-fn reconnect-opts)))
      (is (identical? force-reconnect! (:force-reconnect! reconnect-opts))))))
