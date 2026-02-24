(ns hyperopen.views.trading-chart.utils.chart-interop.visible-range-persistence-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.platform :as platform]
            [hyperopen.views.trading-chart.utils.chart-interop :as chart-interop]))

(defn- v2-storage-key
  [timeframe asset]
  (str "chart-visible-time-range:v2:"
       (if (keyword? timeframe) (name timeframe) timeframe)
       ":"
       (js/encodeURIComponent asset)))

(defn- daily-candles
  [count*]
  (vec
   (for [idx (range count*)]
     {:time (+ 1700000000 (* idx 86400))
      :open (+ 100 idx)
      :high (+ 101 idx)
      :low (+ 99 idx)
      :close (+ 100 idx)
      :volume (+ 1000 idx)})))

(deftest apply-persisted-visible-range-applies-stored-time-range-test
  (let [requested-key (atom nil)
        applied-range (atom nil)
        time-scale #js {:setVisibleRange (fn [range]
                                           (reset! applied-range (js->clj range :keywordize-keys true)))}
        chart #js {:timeScale (fn [] time-scale)}]
    (with-redefs [platform/local-storage-get (fn [key]
                                               (reset! requested-key key)
                                               "{\"kind\":\"time\",\"from\":10,\"to\":20}")]
      (is (= true (chart-interop/apply-persisted-visible-range! chart
                                                                 :1d
                                                                 {:asset "BTC"
                                                                  :candles [{:time 10}
                                                                            {:time 20}]})))
      (is (= (v2-storage-key :1d "BTC") @requested-key))
      (is (= {:from 10 :to 20} @applied-range)))))

(deftest apply-persisted-visible-range-ignores-invalid-storage-test
  (let [applied? (atom false)
        time-scale #js {:setVisibleRange (fn [_] (reset! applied? true))}
        chart #js {:timeScale (fn [] time-scale)}]
    (with-redefs [platform/local-storage-get (fn [_] "not-json")]
      (is (= false (chart-interop/apply-persisted-visible-range! chart :1d)))
      (is (false? @applied?)))))

(deftest subscribe-visible-range-persistence-persists-time-range-and-cleans-up-test
  (let [handler* (atom nil)
        unsubscribe-called? (atom false)
        writes (atom [])
        time-scale #js {:getVisibleRange (fn [] #js {:from 31 :to 45})
                        :subscribeVisibleTimeRangeChange (fn [handler]
                                                           (reset! handler* handler))
                        :unsubscribeVisibleTimeRangeChange (fn [handler]
                                                             (when (identical? handler @handler*)
                                                               (reset! unsubscribe-called? true)))}
        chart #js {:timeScale (fn [] time-scale)}]
    (with-redefs [platform/local-storage-set! (fn [key value]
                                                (swap! writes conj [key value]))]
      (let [cleanup (chart-interop/subscribe-visible-range-persistence! chart :1h {:asset "BTC"})]
        (is (fn? @handler*))
        (@handler* #js {})
        (is (= 1 (count @writes)))
        (let [[key raw] (first @writes)
              payload (js->clj (js/JSON.parse raw) :keywordize-keys true)]
          (is (= (v2-storage-key :1h "BTC") key))
          (is (= {:kind "time" :from 31 :to 45} payload)))
        (cleanup)
        (is @unsubscribe-called?)))))

(deftest subscribe-visible-range-persistence-falls-back-to-logical-range-test
  (let [handler* (atom nil)
        writes (atom [])
        time-scale #js {:getVisibleLogicalRange (fn [] #js {:from 5 :to 8})
                        :subscribeVisibleLogicalRangeChange (fn [handler]
                                                              (reset! handler* handler))
                        :unsubscribeVisibleLogicalRangeChange (fn [_] nil)}
        chart #js {:timeScale (fn [] time-scale)}]
    (with-redefs [platform/local-storage-set! (fn [key value]
                                                (swap! writes conj [key value]))]
      (let [cleanup (chart-interop/subscribe-visible-range-persistence! chart :5m {:asset "BTC"})]
        (is (fn? @handler*))
        (@handler* #js {})
        (let [[key raw] (first @writes)
              payload (js->clj (js/JSON.parse raw) :keywordize-keys true)]
          (is (= (v2-storage-key :5m "BTC") key))
          (is (= {:kind "logical" :from 5 :to 8} payload)))
        (cleanup)))))


(deftest apply-persisted-visible-range-supports-injected-storage-get-test
  (let [requested-key (atom nil)
        applied-range (atom nil)
        time-scale #js {:setVisibleRange (fn [range]
                                           (reset! applied-range (js->clj range :keywordize-keys true)))}
        chart #js {:timeScale (fn [] time-scale)}]
    (is (= true (chart-interop/apply-persisted-visible-range! chart
                                                               :4h
                                                               {:asset "BTC"
                                                                :candles [{:time 3}
                                                                          {:time 9}]
                                                                :storage-get (fn [key]
                                                                               (reset! requested-key key)
                                                                               "{\"kind\":\"time\",\"from\":3,\"to\":9}")})))
    (is (= (v2-storage-key :4h "BTC") @requested-key))
    (is (= {:from 3 :to 9} @applied-range))))


(deftest subscribe-visible-range-persistence-supports-injected-storage-set-test
  (let [handler* (atom nil)
        writes (atom [])
        storage-set! (fn [key value]
                       (swap! writes conj [key value]))
        time-scale #js {:subscribeVisibleTimeRangeChange (fn [handler]
                                                           (reset! handler* handler))
                        :unsubscribeVisibleTimeRangeChange (fn [_] nil)}
        chart #js {:timeScale (fn [] time-scale)}
        cleanup (chart-interop/subscribe-visible-range-persistence! chart :15m
                                                                    {:asset "BTC"
                                                                     :storage-set! storage-set!})]
    (@handler* #js {:from 11 :to 19})
    (let [[key raw] (first @writes)
          payload (js->clj (js/JSON.parse raw) :keywordize-keys true)]
      (is (= (v2-storage-key :15m "BTC") key))
      (is (= {:kind "time" :from 11 :to 19} payload)))
    (cleanup)))

(deftest apply-persisted-visible-range-invalid-logical-range-falls-back-to-fit-and-right-anchor-test
  (let [requested-key (atom nil)
        applied-logical-ranges (atom [])
        fit-content-calls (atom 0)
        right-offset-calls (atom [])
        scroll-to-realtime-calls (atom 0)
        time-scale #js {:setVisibleLogicalRange (fn [range]
                                                  (swap! applied-logical-ranges conj (js->clj range :keywordize-keys true)))
                        :fitContent (fn []
                                      (swap! fit-content-calls inc))
                        :setRightOffset (fn [offset]
                                          (swap! right-offset-calls conj offset))
                        :scrollToRealTime (fn []
                                            (swap! scroll-to-realtime-calls inc))}
        chart #js {:timeScale (fn [] time-scale)}
        candles (daily-candles 61)]
    (with-redefs [platform/local-storage-get (fn [key]
                                               (reset! requested-key key)
                                               "{\"kind\":\"logical\",\"from\":38.1045943304008,\"to\":169.33333333333331}")]
      (is (= false (chart-interop/apply-persisted-visible-range! chart
                                                                  :1d
                                                                  {:asset "xyz:SILVER"
                                                                   :candles candles})))
      (is (= (v2-storage-key :1d "xyz:SILVER") @requested-key))
      (is (empty? @applied-logical-ranges))
      (is (= 1 @fit-content-calls))
      (is (= [8] @right-offset-calls))
      (is (= 1 @scroll-to-realtime-calls)))))

(deftest apply-persisted-visible-range-missing-range-uses-recent-window-for-large-datasets-test
  (let [applied-logical-ranges (atom [])
        fit-content-calls (atom 0)
        right-offset-calls (atom 0)
        scroll-to-realtime-calls (atom 0)
        time-scale #js {:setVisibleLogicalRange (fn [range]
                                                  (swap! applied-logical-ranges conj (js->clj range :keywordize-keys true)))
                        :fitContent (fn []
                                      (swap! fit-content-calls inc))
                        :setRightOffset (fn [_]
                                          (swap! right-offset-calls inc))
                        :scrollToRealTime (fn []
                                            (swap! scroll-to-realtime-calls inc))}
        chart #js {:timeScale (fn [] time-scale)}
        candles (daily-candles 331)]
    (with-redefs [platform/local-storage-get (fn [_] nil)]
      (is (= false (chart-interop/apply-persisted-visible-range! chart
                                                                  :1d
                                                                  {:asset "BTC"
                                                                   :candles candles})))
      (is (= [{:from 211 :to 338}] @applied-logical-ranges))
      (is (= 1 @fit-content-calls))
      (is (zero? @right-offset-calls))
      (is (zero? @scroll-to-realtime-calls)))))
