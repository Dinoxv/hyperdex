(ns hyperopen.api.compat-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.compat :as api-compat]))

(deftest fetch-user-fills-projects-rows-to-store-test
  (async done
    (let [store (atom {:orders {}})
          deps {:log-fn (fn [& _] nil)
                :request-user-fills! (fn [_address _opts]
                                       (js/Promise.resolve [{:tid 1 :coin "BTC"}]))}]
      (-> (api-compat/fetch-user-fills! deps store "0xabc" {:priority :high})
          (.then (fn [rows]
                   (is (= [{:tid 1 :coin "BTC"}] rows))
                   (is (= rows (get-in @store [:orders :fills])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))

(deftest fetch-frontend-open-orders-projects-by-dex-test
  (async done
    (let [store (atom {:orders {}})
          calls (atom [])
          deps {:log-fn (fn [& _] nil)
                :request-frontend-open-orders! (fn [_address opts]
                                                 (swap! calls conj opts)
                                                 (js/Promise.resolve [{:oid 7 :coin "ETH"}]))}]
      (-> (api-compat/fetch-frontend-open-orders! deps store "0xabc" "dex-a" {:priority :high})
          (.then (fn [rows]
                   (is (= [{:oid 7 :coin "ETH"}] rows))
                   (is (= [{:dex "dex-a"
                            :priority :high}]
                          @calls))
                   (is (= rows
                          (get-in @store [:orders :open-orders-snapshot-by-dex "dex-a"])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))

(deftest fetch-user-abstraction-normalizes-and-projects-account-test
  (async done
    (let [store (atom {:wallet {:address "0xabc"}
                       :account {:mode :classic :abstraction-raw nil}})
          deps {:log-fn (fn [& _] nil)
                :request-user-abstraction! (fn [_address _opts]
                                             (js/Promise.resolve "portfolioMargin"))}]
      (-> (api-compat/fetch-user-abstraction! deps store "0xAbC" {:priority :high})
          (.then (fn [snapshot]
                   (is (= {:mode :unified
                           :abstraction-raw "portfolioMargin"}
                          snapshot))
                   (is (= snapshot (:account @store)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))
