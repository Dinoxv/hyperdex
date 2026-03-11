(ns hyperopen.views.trading-chart.utils.chart-interop.series-sync
  (:require [hyperopen.views.trading-chart.utils.chart-interop.baseline :as baseline]
            [hyperopen.views.trading-chart.utils.chart-interop.candle-sync-policy :as candle-sync-policy]
            [hyperopen.views.trading-chart.utils.chart-interop.price-format :as price-format]
            [hyperopen.views.trading-chart.utils.chart-interop.series :as series]
            [hyperopen.views.trading-chart.utils.chart-interop.transforms :as transforms]))

(defonce ^:private main-series-sync-sidecar (js/WeakMap.))
(defonce ^:private volume-series-sync-sidecar (js/WeakMap.))

(defn- sidecar-state
  [sidecar series*]
  (if series*
    (or (.get sidecar series*) {})
    {}))

(defn- remember-state!
  [sidecar series* state]
  (when series*
    (.set sidecar series* state))
  state)

(defn- vectorize-candles
  [candles]
  (if (vector? candles) candles (vec candles)))

(defn- config-reset-decision
  [previous-candles next-candles]
  {:mode :full-reset
   :reason :config-changed
   :previous-count (count (or previous-candles []))
   :next-count (count next-candles)})

(defn- update-series-point!
  [series* point]
  (let [update-fn (when series*
                    (aget series* "update"))]
    (if (and (fn? update-fn) (some? point))
      (do
        (.call update-fn series* (clj->js point))
        true)
      false)))

(defn- apply-sync-decision!
  [series* decision transformed-data]
  (case (:mode decision)
    :append-last
    (when-not (update-series-point! series* (peek transformed-data))
      (.setData series* (clj->js transformed-data)))

    :update-last
    (when-not (update-series-point! series* (peek transformed-data))
      (.setData series* (clj->js transformed-data)))

    :full-reset
    (.setData series* (clj->js transformed-data))

    nil))

(defn sync-main-series!
  [series* candles chart-type {:keys [price-decimals]}]
  (let [candles* (vectorize-candles candles)
        chart-type* (transforms/normalize-main-chart-type chart-type)
        state (sidecar-state main-series-sync-sidecar series*)
        config-changed?
        (or (not= chart-type* (:chart-type state))
            (not= price-decimals (:price-decimals state)))
        decision (if config-changed?
                   (config-reset-decision (:source-candles state) candles*)
                   (candle-sync-policy/infer-decision (:source-candles state) candles*))]
    (when (or config-changed?
              (not= :noop (:mode decision)))
      (let [transformed-data (series/transform-main-series-data candles* chart-type*)
            base-value (when (= chart-type* :baseline)
                         (baseline/infer-baseline-base-value transformed-data))
            price-format* (price-format/infer-series-price-format
                           transformed-data
                           (fn [points]
                             (series/extract-series-prices points chart-type*))
                           {:price-decimals price-decimals})
            series-options (cond-> {:priceFormat price-format*}
                             (some? base-value)
                             (assoc :baseValue {:type "price" :price base-value}))
            options-changed? (not= series-options (:series-options state))]
        (when options-changed?
          (.applyOptions ^js series* (clj->js series-options)))
        (apply-sync-decision! series* decision transformed-data)
        (remember-state!
         main-series-sync-sidecar
         series*
         {:source-candles candles*
          :chart-type chart-type*
          :price-decimals price-decimals
          :series-options series-options})))))

(defn sync-volume-series!
  [volume-series candles]
  (let [candles* (vectorize-candles candles)
        state (sidecar-state volume-series-sync-sidecar volume-series)
        decision (candle-sync-policy/infer-decision (:source-candles state) candles*)]
    (when (not= :noop (:mode decision))
      (let [volume-data (vec (transforms/transform-data-for-volume candles*))]
        (apply-sync-decision! volume-series decision volume-data)
        (remember-state!
         volume-series-sync-sidecar
         volume-series
         {:source-candles candles*})))))
