(ns hyperopen.api.facade-runtime-test
  (:require [cljs.test :refer-macros [deftest is use-fixtures]]
            [hyperopen.api :as api]
            [hyperopen.api.service :as api-service]))

(defn- stub-client
  [{:keys [stats reset-calls]}]
  {:request-info! (fn [& _] (js/Promise.resolve {}))
   :get-request-stats (fn [] stats)
   :reset! (fn [] (swap! reset-calls inc))})

(use-fixtures
  :each
  {:before (fn []
             (api/reset-api-service!)
             (api/reset-request-runtime!))
   :after (fn []
            (api/reset-api-service!)
            (api/reset-request-runtime!))})

(deftest install-api-service-switches-active-service-test
  (let [reset-a (atom 0)
        reset-b (atom 0)
        service-a (api-service/make-service
                   {:info-client-instance (stub-client {:stats {:source :a}
                                                        :reset-calls reset-a})})
        service-b (api-service/make-service
                   {:info-client-instance (stub-client {:stats {:source :b}
                                                        :reset-calls reset-b})})]
    (api/install-api-service! service-a)
    (is (= {:source :a}
           (api/get-request-stats)))
    (api/install-api-service! service-b)
    (is (= {:source :b}
           (api/get-request-stats)))))

(deftest configure-api-service-supports-injected-client-test
  (let [reset-calls (atom 0)
        stats {:configured true}
        client (stub-client {:stats stats
                             :reset-calls reset-calls})]
    (api/configure-api-service! {:info-client-instance client
                                 :log-fn (fn [& _] nil)})
    (is (= stats
           (api/get-request-stats)))
    (api/reset-api-service!)
    (is (not= stats
              (api/get-request-stats)))))

(deftest reset-request-runtime-resets-installed-service-client-test
  (let [reset-calls (atom 0)
        service (api-service/make-service
                 {:info-client-instance (stub-client {:stats {}
                                                      :reset-calls reset-calls})})]
    (api/install-api-service! service)
    (api/reset-request-runtime!)
    (is (= 1 @reset-calls))))
