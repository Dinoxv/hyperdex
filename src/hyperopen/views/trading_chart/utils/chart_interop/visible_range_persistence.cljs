(ns hyperopen.views.trading-chart.utils.chart-interop.visible-range-persistence
  (:require [hyperopen.platform :as platform]
            [hyperopen.schema.chart-interop-contracts :as chart-contracts]))

(def ^:private visible-range-storage-key-prefix "chart-visible-time-range")
(def ^:private visible-range-storage-key-version "v2")
(def ^:private default-recent-logical-bars 120)
(def ^:private default-recent-right-offset-bars 8)
(def ^:private logical-range-overhang-bars 24)
(def ^:private time-range-overhang-bars 24)

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

(defn- normalize-timeframe-token
  [timeframe]
  (cond
    (keyword? timeframe) (name timeframe)
    (string? timeframe) timeframe
    :else "default"))

(defn- normalize-asset-token
  [asset]
  (let [asset* (cond
                 (keyword? asset) (name asset)
                 (string? asset) asset
                 :else "default")]
    (if (seq asset*)
      (js/encodeURIComponent asset*)
      "default")))

(defn- visible-range-storage-key
  [timeframe asset]
  (str visible-range-storage-key-prefix
       ":"
       visible-range-storage-key-version
       ":"
       (normalize-timeframe-token timeframe)
       ":"
       (normalize-asset-token asset)))

(defn- persist-visible-range!
  [asset timeframe range-data storage-set!]
  (when-let [normalized (normalize-visible-range range-data)]
    (chart-contracts/assert-visible-range! normalized
                                           {:boundary :chart-interop/persist-visible-range
                                            :timeframe timeframe
                                            :asset asset})
    (try
      (storage-set!
       (visible-range-storage-key timeframe asset)
       (js/JSON.stringify (clj->js normalized)))
      true
      (catch :default _
        false))))

(defn- load-persisted-visible-range
  [asset timeframe storage-get]
  (try
    (let [raw (storage-get (visible-range-storage-key timeframe asset))]
      (when (seq raw)
        (normalize-visible-range (js->clj (js/JSON.parse raw) :keywordize-keys true))))
    (catch :default _
      nil)))

(defn- candle-time-seconds
  [candle]
  (let [raw-time (or (:time candle)
                     (get candle "time")
                     (:t candle)
                     (get candle "t"))
        parsed (parse-range-number raw-time)]
    (when (some? parsed)
      (if (> parsed 100000000000)
        (/ parsed 1000)
        parsed))))

(defn- infer-candles-time-domain
  [candles]
  (let [times (->> candles
                   (keep candle-time-seconds)
                   sort
                   vec)]
    (when (seq times)
      (let [deltas (->> (map - (rest times) times)
                        (filter pos?)
                        vec)
            interval (if (seq deltas)
                       (apply min deltas)
                       1)]
        {:first-time (first times)
         :last-time (peek times)
         :interval interval}))))

(defn- logical-range-valid-for-candles?
  [{:keys [from to]} candles]
  (let [candle-count (count candles)
        max-logical (+ (dec candle-count) logical-range-overhang-bars)
        min-logical (- logical-range-overhang-bars)]
    (and (pos? candle-count)
         (< from to)
         (<= from max-logical)
         (>= from min-logical)
         (<= to max-logical)
         (>= to min-logical))))

(defn- time-range-valid-for-candles?
  [{:keys [from to]} candles]
  (when-let [{:keys [first-time last-time interval]} (infer-candles-time-domain candles)]
    (let [margin (* interval time-range-overhang-bars)
          min-time (- first-time margin)
          max-time (+ last-time margin)]
      (and (< from to)
           (<= from max-time)
           (>= from min-time)
           (<= to max-time)
           (>= to min-time)))))

(defn- persisted-range-valid-for-candles?
  [{:keys [kind] :as persisted} candles]
  (and (seq candles)
       (case kind
         :logical (logical-range-valid-for-candles? persisted candles)
         :time (time-range-valid-for-candles? persisted candles)
         false)))

(defn- apply-visible-range!
  [time-scale {:keys [kind from to]}]
  (try
    (case kind
      :time (if (fn? (.-setVisibleRange ^js time-scale))
              (do
                (.setVisibleRange ^js time-scale
                                  (clj->js {:from from
                                            :to to}))
                true)
              false)
      :logical (if (fn? (.-setVisibleLogicalRange ^js time-scale))
                 (do
                   (.setVisibleLogicalRange ^js time-scale
                                            (clj->js {:from from
                                                      :to to}))
                   true)
                 false)
      false)
    (catch :default _
      false)))

(defn- apply-recent-default-visible-range!
  [chart candles]
  (let [time-scale (.timeScale ^js chart)
        candle-count (count candles)]
    (when (and time-scale (pos? candle-count))
      (when (fn? (.-fitContent ^js time-scale))
        (try
          (.fitContent ^js time-scale)
          (catch :default _
            nil)))
      (if (> candle-count default-recent-logical-bars)
        (let [to (+ (dec candle-count) default-recent-right-offset-bars)
              from (- candle-count default-recent-logical-bars)]
          (when (fn? (.-setVisibleLogicalRange ^js time-scale))
            (try
              (.setVisibleLogicalRange ^js time-scale
                                       (clj->js {:from from :to to}))
              (catch :default _
                nil))))
        (do
          (when (fn? (.-setRightOffset ^js time-scale))
            (try
              (.setRightOffset ^js time-scale default-recent-right-offset-bars)
              (catch :default _
                nil)))
          (when (fn? (.-scrollToRealTime ^js time-scale))
            (try
              (.scrollToRealTime ^js time-scale)
              (catch :default _
                nil))))))))

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
  [asset timeframe kind range storage-set!]
  (let [range-data (cond
                     (map? range) range
                     (some? range) (js->clj range :keywordize-keys true)
                     :else nil)]
    (when (some? range-data)
      (persist-visible-range! asset timeframe (assoc range-data :kind kind) storage-set!))))

(defn apply-persisted-visible-range!
  "Apply persisted visible range (asset + timeframe) to chart time scale if available."
  ([chart timeframe]
   (apply-persisted-visible-range! chart timeframe {}))
  ([chart timeframe {:keys [storage-get asset candles]}]
   (let [storage-get* (or storage-get platform/local-storage-get)
         time-scale (.timeScale ^js chart)
         persisted (load-persisted-visible-range asset timeframe storage-get*)
         persisted-valid? (persisted-range-valid-for-candles? persisted candles)
         persisted-applied? (if (and time-scale persisted-valid?)
                             (do
                               (chart-contracts/assert-visible-range! persisted
                                                                      {:boundary :chart-interop/load-visible-range
                                                                       :timeframe timeframe
                                                                       :asset asset})
                               (apply-visible-range! time-scale persisted))
                             false)]
     (when (and time-scale (seq candles) (not persisted-applied?))
       (apply-recent-default-visible-range! chart candles))
     persisted-applied?)))

(defn subscribe-visible-range-persistence!
  "Subscribe to visible-range changes and persist them by asset + timeframe."
  ([chart timeframe]
   (subscribe-visible-range-persistence! chart timeframe {}))
  ([chart timeframe {:keys [storage-set! asset]}]
   (let [storage-set!* (or storage-set! platform/local-storage-set!)
         time-scale (.timeScale ^js chart)]
     (if-not time-scale
       (fn [] nil)
       (let [persist-current! (fn []
                                (when-let [range-data (visible-range-from-time-scale time-scale)]
                                  (persist-visible-range! asset timeframe range-data storage-set!*)))
             logical-handler (fn [range]
                               (when-not (persist-range-candidate! asset timeframe :logical range storage-set!*)
                                 (persist-current!)))
             time-handler (fn [range]
                            (when-not (persist-range-candidate! asset timeframe :time range storage-set!*)
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
