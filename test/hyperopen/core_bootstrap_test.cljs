(ns hyperopen.core-bootstrap-test
  (:require [cljs.test :refer-macros [async deftest is use-fixtures]]
            [hyperopen.api :as api]
            [hyperopen.core :as core]
            [hyperopen.wallet.address-watcher :as address-watcher]
            [hyperopen.websocket.active-asset-ctx :as active-ctx]
            [hyperopen.websocket.client :as ws-client]
            [hyperopen.websocket.orderbook :as orderbook]
            [hyperopen.websocket.trades :as trades]
            [hyperopen.websocket.user :as user-ws]
            [hyperopen.websocket.webdata2 :as webdata2]))

(defn- reset-startup-runtime! []
  (reset! @#'hyperopen.core/startup-runtime
          {:deferred-scheduled? false
           :bootstrapped-address nil
           :summary-logged? false}))

(use-fixtures
  :each
  (fn [f]
    (reset-startup-runtime!)
    (swap! core/store assoc :active-asset nil)
    (f)
    (reset-startup-runtime!)))

(deftest initialize-remote-data-streams-phased-bootstrap-test
  (let [phases (atom [])
        critical-fetches (atom 0)
        deferred-callback (atom nil)]
    (with-redefs [ws-client/init-connection! (fn [_] nil)
                  active-ctx/init! (fn [_] nil)
                  orderbook/init! (fn [_] nil)
                  trades/init! (fn [_] nil)
                  user-ws/init! (fn [_] nil)
                  webdata2/init! (fn [_] nil)
                  address-watcher/init-with-webdata2! (fn [& _] nil)
                  address-watcher/add-handler! (fn [& _] nil)
                  address-watcher/sync-current-address! (fn [& _] nil)
                  api/fetch-asset-contexts! (fn [& _]
                                              (swap! critical-fetches inc)
                                              (js/Promise.resolve nil))
                  api/fetch-asset-selector-markets! (fn [_ opts]
                                                      (swap! phases conj (:phase opts))
                                                      (js/Promise.resolve []))
                  hyperopen.core/schedule-idle-or-timeout! (fn [f]
                                                              (reset! deferred-callback f)
                                                              :scheduled)]
      (core/initialize-remote-data-streams!)
      (is (= 1 @critical-fetches))
      (is (= [:bootstrap] @phases))
      (is (fn? @deferred-callback))
      (@deferred-callback)
      (is (= [:bootstrap :full] @phases)))))

(deftest account-bootstrap-two-stage-and-guarded-test
  (async done
    (let [stage-a-calls (atom [])
          stage-b-calls (atom [])]
      (swap! core/store assoc-in [:wallet :address] "0xabc")
      (with-redefs [api/fetch-frontend-open-orders! (fn [& args]
                                                       (swap! stage-a-calls conj [:open-orders args])
                                                       (js/Promise.resolve nil))
                    api/fetch-user-fills! (fn [& args]
                                            (swap! stage-a-calls conj [:fills args])
                                            (js/Promise.resolve nil))
                    api/fetch-spot-clearinghouse-state! (fn [& args]
                                                          (swap! stage-a-calls conj [:spot args])
                                                          (js/Promise.resolve nil))
                    api/ensure-perp-dexs! (fn [& _]
                                            (js/Promise.resolve ["dex-1" "dex-2"]))
                    hyperopen.core/stage-b-account-bootstrap! (fn [address dexs]
                                                                (swap! stage-b-calls conj [address dexs]))]
        (@#'hyperopen.core/bootstrap-account-data! "0xabc")
        (js/setTimeout
         (fn []
           (is (= 3 (count @stage-a-calls)))
           (is (= [["0xabc" ["dex-1" "dex-2"]]] @stage-b-calls))
           ;; Same address should not trigger stage A/B again.
           (@#'hyperopen.core/bootstrap-account-data! "0xabc")
           (js/setTimeout
            (fn []
              (is (= 3 (count @stage-a-calls)))
              (is (= 1 (count @stage-b-calls)))
              (done))
            0))
         0)))))
