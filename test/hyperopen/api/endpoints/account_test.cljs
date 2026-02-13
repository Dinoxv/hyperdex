(ns hyperopen.api.endpoints.account-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.endpoints.account :as account]))

(deftest request-user-funding-history-paginates-forward-by-time-test
  (async done
    (let [calls (atom [])
          post-info! (fn [body _opts]
                       (swap! calls conj body)
                       (let [start-time (get body "startTime")]
                         (js/Promise.resolve
                          (cond
                            (= start-time 1000)
                            [{:time-ms 1000} {:time-ms 2000}]

                            (= start-time 2001)
                            [{:time-ms 3000}]

                            :else
                            []))))
          normalize-rows-fn identity
          sort-rows-fn (fn [rows]
                         (->> rows
                              (sort-by :time-ms >)
                              vec))]
      (-> (account/request-user-funding-history! post-info!
                                                 normalize-rows-fn
                                                 sort-rows-fn
                                                 "0xabc"
                                                 1000
                                                 5000
                                                 {})
          (.then (fn [rows]
                   (is (= [3000 2000 1000] (mapv :time-ms rows)))
                   (is (= [1000 2001 3001] (mapv #(get % "startTime") @calls)))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))

(deftest request-user-funding-history-supports-wrapped-payloads-test
  (async done
    (let [post-info! (fn [body _opts]
                       (let [start-time (get body "startTime")]
                         (js/Promise.resolve
                          (if (= start-time 0)
                            {:data {:fundings [{:time 1000
                                                :delta {:type "funding"
                                                        :coin "HYPE"
                                                        :usdc "1.0"
                                                        :szi "2.0"
                                                        :fundingRate "0.0001"}}]}}
                            {:data {:fundings []}}))))
          normalize-rows-fn (fn [rows]
                              (mapv (fn [row]
                                      {:time-ms (:time row)
                                       :coin (get-in row [:delta :coin])})
                                    rows))
          sort-rows-fn identity]
      (-> (account/request-user-funding-history! post-info!
                                                 normalize-rows-fn
                                                 sort-rows-fn
                                                 "0xabc"
                                                 0
                                                 5000
                                                 {})
          (.then (fn [rows]
                   (is (= 1 (count rows)))
                   (is (= [{:time-ms 1000
                            :coin "HYPE"}]
                          rows))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))

(deftest request-user-funding-history-warns-on-non-empty-page-when-normalization-drops-all-rows-test
  (async done
    (let [warnings (atom [])
          console-object (or (.-console js/globalThis) #js {})
          original-warn (.-warn console-object)
          post-info! (fn [_body _opts]
                       (js/Promise.resolve [{:time 1000
                                             :delta {:type "funding"
                                                     :coin "HYPE"
                                                     :usdc "1.0"
                                                     :szi "2.0"
                                                     :fundingRate "0.0001"}}]))
          normalize-rows-fn (fn [_rows] [])
          sort-rows-fn identity]
      (set! (.-warn console-object)
            (fn [& args]
              (swap! warnings conj (vec args))))
      (-> (account/request-user-funding-history! post-info!
                                                 normalize-rows-fn
                                                 sort-rows-fn
                                                 "0xabc"
                                                 0
                                                 5000
                                                 {})
          (.then (fn [rows]
                   (is (= [] rows))
                   (is (= 1 (count @warnings)))
                   (let [[message payload] (first @warnings)
                         payload* (js->clj payload :keywordize-keys true)]
                     (is (= "Funding history normalization dropped all rows on a non-empty page."
                            message))
                     (is (= "funding-history-normalization-drop" (:event payload*)))
                     (is (= 1 (:raw-row-count payload*)))
                     (is (= 0 (:start-time-ms payload*)))
                     (is (= 5000 (:end-time-ms payload*))))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))
          (.finally (fn []
                      (set! (.-warn console-object) original-warn)))))))

(deftest request-spot-clearinghouse-state-short-circuits-without-address-test
  (async done
    (let [calls (atom 0)
          post-info! (fn [_body _opts]
                       (swap! calls inc)
                       (js/Promise.resolve {}))]
      (-> (account/request-spot-clearinghouse-state! post-info! nil {})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= 0 @calls))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))

(deftest request-user-abstraction-builds-dedupe-key-per-address-test
  (async done
    (let [calls (atom [])
          post-info! (fn [body opts]
                       (swap! calls conj [body opts])
                       (js/Promise.resolve "unifiedAccount"))]
      (-> (account/request-user-abstraction! post-info! "0xAbC" {})
          (.then (fn [_]
                   (let [[body opts] (first @calls)]
                     (is (= {"type" "userAbstraction"
                             "user" "0xAbC"}
                            body))
                     (is (= [:user-abstraction "0xabc"] (:dedupe-key opts)))
                     (done))))
          (.catch (fn [err]
                    (is false (str "Unexpected error: " err))
                    (done)))))))

(deftest normalize-user-abstraction-mode-maps-known-values-test
  (is (= :unified (account/normalize-user-abstraction-mode "unifiedAccount")))
  (is (= :unified (account/normalize-user-abstraction-mode "portfolioMargin")))
  (is (= :classic (account/normalize-user-abstraction-mode "default")))
  (is (= :classic (account/normalize-user-abstraction-mode nil))))

(deftest request-clearinghouse-state-uses-optional-dex-test
  (let [calls (atom [])
        post-info! (fn [body opts]
                     (swap! calls conj [body opts])
                     (js/Promise.resolve {}))]
    (account/request-clearinghouse-state! post-info! "0xabc" nil {})
    (account/request-clearinghouse-state! post-info! "0xabc" "vault" {:priority :low})
    (is (= [{"type" "clearinghouseState"
             "user" "0xabc"}
            {"type" "clearinghouseState"
             "user" "0xabc"
             "dex" "vault"}]
           (mapv first @calls)))
    (is (= [{:priority :high}
            {:priority :low}]
           (mapv second @calls)))))
