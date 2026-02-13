(ns hyperopen.api.fetch-compat-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.fetch-compat :as fetch-compat]))

(deftest fetch-asset-contexts-applies-success-and-error-projections-test
  (async done
    (let [store (atom {})
          deps {:log-fn (fn [& _] nil)
                :request-asset-contexts! (fn [_opts]
                                           (js/Promise.resolve {:BTC {:idx 0}}))
                :apply-asset-contexts-success (fn [state rows]
                                                (assoc state :asset-contexts rows))
                :apply-asset-contexts-error (fn [state err]
                                              (assoc state :asset-contexts-error (str err)))}]
      (-> (fetch-compat/fetch-asset-contexts! deps store {:priority :high})
          (.then (fn [rows]
                   (is (= {:BTC {:idx 0}} rows))
                   (is (= {:BTC {:idx 0}} (:asset-contexts @store)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))

(deftest fetch-user-abstraction-projects-normalized-mode-test
  (async done
    (let [store (atom {:wallet {:address "0xabc"}})
          deps {:log-fn (fn [& _] nil)
                :request-user-abstraction! (fn [_address _opts]
                                             (js/Promise.resolve "portfolioMargin"))
                :normalize-user-abstraction-mode (fn [_] :unified)
                :apply-user-abstraction-snapshot
                (fn [state requested-address snapshot]
                  (assoc state :projection [requested-address snapshot]))}]
      (-> (fetch-compat/fetch-user-abstraction! deps store "0xAbC" {})
          (.then (fn [snapshot]
                   (is (= {:mode :unified
                           :abstraction-raw "portfolioMargin"}
                          snapshot))
                   (is (= ["0xabc" snapshot] (:projection @store)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))
