(ns hyperopen.platform.indexed-db-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.core-bootstrap.test-support.browser-mocks :as browser-mocks]
            [hyperopen.platform.indexed-db :as indexed-db]
            [hyperopen.test-support.async :as async-support]))

(deftest indexed-db-json-roundtrip-test
  (async done
    (browser-mocks/with-test-indexed-db
      (fn []
        (-> (indexed-db/put-json! indexed-db/asset-selector-markets-store
                                  "selector-cache"
                                  {:rows [{:coin "BTC"}
                                          {:coin "ETH"}]
                                   :saved-at-ms 123})
            (.then (fn [persisted?]
                     (is (true? persisted?))
                     (-> (indexed-db/get-json! indexed-db/asset-selector-markets-store
                                               "selector-cache")
                         (.then (fn [record]
                                  (is (= {:rows [{:coin "BTC"}
                                                 {:coin "ETH"}]
                                          :saved-at-ms 123}
                                         record))
                                  (-> (indexed-db/delete-key! indexed-db/asset-selector-markets-store
                                                              "selector-cache")
                                      (.then (fn [deleted?]
                                               (is (true? deleted?))
                                               (-> (indexed-db/get-json! indexed-db/asset-selector-markets-store
                                                                         "selector-cache")
                                                   (.then (fn [missing]
                                                            (is (nil? missing))
                                                            (done)))
                                                   (.catch (async-support/unexpected-error done)))))
                                      (.catch (async-support/unexpected-error done)))))
                         (.catch (async-support/unexpected-error done)))))
            (.catch (async-support/unexpected-error done)))))))

(deftest indexed-db-helpers-gracefully-handle-unavailable-browser-api-test
  (async done
    (let [original-indexed-db (.-indexedDB js/globalThis)
          restore! (fn []
                     (set! (.-indexedDB js/globalThis) original-indexed-db)
                     (indexed-db/clear-open-db-cache!))
          fail! (fn [error]
                  (restore!)
                  ((async-support/unexpected-error done) error))]
      (indexed-db/clear-open-db-cache!)
      (set! (.-indexedDB js/globalThis) nil)
      (-> (indexed-db/get-json! indexed-db/funding-history-store "BTC")
          (.then (fn [result]
                   (is (nil? result))
                   (-> (indexed-db/put-json! indexed-db/funding-history-store
                                             "BTC"
                                             {:rows []})
                       (.then (fn [persisted?]
                                (is (false? persisted?))
                                (-> (indexed-db/delete-key! indexed-db/funding-history-store "BTC")
                                    (.then (fn [deleted?]
                                             (is (false? deleted?))
                                             (restore!)
                                             (done)))
                                    (.catch fail!))))
                       (.catch fail!))))
          (.catch fail!)))))
