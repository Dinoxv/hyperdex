(ns hyperopen.views.trading-chart.utils.chart-interop.visible-range-persistence
  (:require [hyperopen.platform :as platform]
            [hyperopen.schema.chart-interop-contracts :as chart-contracts]))

(def ^:private visible-range-storage-key-prefix "chart-visible-time-range")

(defn- normalize-visible-range-kind
  [kind]
  (cond
    (= kind :time) :time
    (= kind :logical) :logical
    (= kind "time") :time
    (= kind "logical") :logical
    :else nil))

(defn- parse-range-number
  [value]
  (cond
    (number? value) value
    (string? value) (let [parsed (js/parseFloat value)]
                      (when-not (js/isNaN parsed) parsed))
    :else nil))

(defn- normalize-visible-range
  [range-data]
  (let [kind (normalize-visible-range-kind (:kind range-data))
        from (parse-range-number (:from range-data))
        to (parse-range-number (:to range-data))]
    (when (and kind
               (some? from)
               (some? to)
               (<= from to))
      {:kind kind
       :from from
       :to to})))

(defn- visible-range-storage-key
  [timeframe]
  (let [timeframe-token (cond
                          (keyword? timeframe) (name timeframe)
                          (string? timeframe) timeframe
                          :else "default")]
    (str visible-range-storage-key-prefix ":" timeframe-token)))

(defn- persist-visible-range!
  [timeframe range-data storage-set!]
  (when-let [normalized (normalize-visible-range range-data)]
    (chart-contracts/assert-visible-range! normalized
                                           {:boundary :chart-interop/persist-visible-range
                                            :timeframe timeframe})
    (try
      (storage-set!
       (visible-range-storage-key timeframe)
       (js/JSON.stringify (clj->js normalized)))
      true
      (catch :default _
        false))))

(defn- load-persisted-visible-range
  [timeframe storage-get]
  (try
    (let [raw (storage-get (visible-range-storage-key timeframe))]
      (when (seq raw)
        (normalize-visible-range (js->clj (js/JSON.parse raw) :keywordize-keys true))))
    (catch :default _
      nil)))

(defn- visible-range-from-time-scale
  [time-scale]
  (or (try
        (when (fn? (.-getVisibleLogicalRange ^js time-scale))
          (some-> (.getVisibleLogicalRange ^js time-scale)
                  (js->clj :keywordize-keys true)
                  (assoc :kind :logical)
                  normalize-visible-range))
        (catch :default _
          nil))
      (try
        (when (fn? (.-getVisibleRange ^js time-scale))
          (some-> (.getVisibleRange ^js time-scale)
                  (js->clj :keywordize-keys true)
                  (assoc :kind :time)
                  normalize-visible-range))
        (catch :default _
          nil))))

(defn- persist-range-candidate!
  [timeframe kind range storage-set!]
  (let [range-data (cond
                     (map? range) range
                     (some? range) (js->clj range :keywordize-keys true)
                     :else nil)]
    (when (some? range-data)
      (persist-visible-range! timeframe (assoc range-data :kind kind) storage-set!))))

(defn apply-persisted-visible-range!
  "Apply persisted visible range to chart time scale if available."
  ([chart timeframe]
   (apply-persisted-visible-range! chart timeframe {}))
  ([chart timeframe {:keys [storage-get]
                     :or {storage-get platform/local-storage-get}}]
   (let [time-scale (.timeScale ^js chart)
         persisted (load-persisted-visible-range timeframe storage-get)]
     (when persisted
       (chart-contracts/assert-visible-range! persisted
                                              {:boundary :chart-interop/load-visible-range
                                               :timeframe timeframe}))
     (if (and time-scale persisted)
       (try
         (case (:kind persisted)
           :time (if (fn? (.-setVisibleRange ^js time-scale))
                   (do
                     (.setVisibleRange ^js time-scale
                                       (clj->js {:from (:from persisted)
                                                 :to (:to persisted)}))
                     true)
                   false)
           :logical (if (fn? (.-setVisibleLogicalRange ^js time-scale))
                      (do
                        (.setVisibleLogicalRange ^js time-scale
                                                 (clj->js {:from (:from persisted)
                                                           :to (:to persisted)}))
                        true)
                      false)
           false)
         (catch :default _
           false))
       false))))

(defn subscribe-visible-range-persistence!
  "Subscribe to visible-range changes and persist them by timeframe."
  ([chart timeframe]
   (subscribe-visible-range-persistence! chart timeframe {}))
  ([chart timeframe {:keys [storage-set!]
                     :or {storage-set! platform/local-storage-set!}}]
   (let [time-scale (.timeScale ^js chart)]
     (if-not time-scale
       (fn [] nil)
       (let [persist-current! (fn []
                                (when-let [range-data (visible-range-from-time-scale time-scale)]
                                  (persist-visible-range! timeframe range-data storage-set!)))
             logical-handler (fn [range]
                               (when-not (persist-range-candidate! timeframe :logical range storage-set!)
                                 (persist-current!)))
             time-handler (fn [range]
                            (when-not (persist-range-candidate! timeframe :time range storage-set!)
                              (persist-current!)))
             unsubscribe! (cond
                            (fn? (.-subscribeVisibleLogicalRangeChange ^js time-scale))
                            (do
                              (.subscribeVisibleLogicalRangeChange ^js time-scale logical-handler)
                              (fn []
                                (try
                                  (.unsubscribeVisibleLogicalRangeChange ^js time-scale logical-handler)
                                  (catch :default _
                                    nil))))

                            (fn? (.-subscribeVisibleTimeRangeChange ^js time-scale))
                            (do
                              (.subscribeVisibleTimeRangeChange ^js time-scale time-handler)
                              (fn []
                                (try
                                  (.unsubscribeVisibleTimeRangeChange ^js time-scale time-handler)
                                  (catch :default _
                                    nil))))

                            :else
                            (fn [] nil))]
         (fn []
           (unsubscribe!)))))))
