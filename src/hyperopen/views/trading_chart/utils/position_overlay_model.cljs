(ns hyperopen.views.trading-chart.utils.position-overlay-model
  (:require [clojure.string :as str]
            [hyperopen.utils.interval :as interval]
            [hyperopen.views.account-info.projections :as projections]))

(def ^:private long-marker-color "#26a69a")
(def ^:private short-marker-color "#ef5350")

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))))

(defn- parse-num
  [value]
  (projections/parse-optional-num value))

(defn- parse-time-ms
  [value]
  (projections/parse-time-ms value))

(defn- non-blank-text
  [value]
  (projections/non-blank-text value))

(defn- normalize-token
  [value]
  (some-> value non-blank-text str/upper-case))

(defn- spot-like-coin?
  [coin]
  (let [coin* (some-> coin non-blank-text)]
    (boolean
     (and coin*
          (or (str/includes? coin* "/")
              (str/starts-with? coin* "@"))))))

(defn- fill-coin
  [fill]
  (or (:coin fill)
      (:symbol fill)
      (:asset fill)))

(defn- resolve-base-token
  [coin market-by-key]
  (let [{:keys [base-label]} (projections/resolve-coin-display coin (or market-by-key {}))]
    (normalize-token base-label)))

(defn- asset-fill-match?
  [active-asset fill market-by-key]
  (let [active-asset* (non-blank-text active-asset)
        fill-coin* (non-blank-text (fill-coin fill))
        active-base (resolve-base-token active-asset* market-by-key)
        fill-base (resolve-base-token fill-coin* market-by-key)
        raw-match? (= active-asset* fill-coin*)
        active-spot? (spot-like-coin? active-asset*)
        fill-spot? (spot-like-coin? fill-coin*)
        same-market-kind? (= active-spot? fill-spot?)]
    (boolean
     (and (seq active-base)
          (seq fill-base)
          (or raw-match?
              (and same-market-kind?
                   (= active-base fill-base)))))))

(defn- fill-side-sign
  [fill]
  (let [side (some-> (:side fill) str str/trim str/upper-case)]
    (cond
      (contains? #{"B" "BUY" "BID" "LONG"} side) 1
      (contains? #{"A" "S" "SELL" "ASK" "SHORT"} side) -1
      :else nil)))

(defn- fill-time
  [fill]
  (or (parse-time-ms (:time fill))
      (parse-time-ms (:timestamp fill))
      (parse-time-ms (:ts fill))
      (parse-time-ms (:t fill))))

(defn- open-direction-from-dir-text
  [fill]
  (let [dir* (some-> (:dir fill) str str/trim str/lower-case)]
    (cond
      (and dir* (str/includes? dir* "open long")) :long
      (and dir* (str/includes? dir* "open short")) :short
      :else nil)))

(defn- entry-transition-fill?
  [fill direction]
  (let [start-position (parse-num (:startPosition fill))
        fill-size (parse-num (:sz fill))
        side-sign (fill-side-sign fill)
        open-direction (open-direction-from-dir-text fill)]
    (cond
      (and (#{:long :short} open-direction)
           (= open-direction direction))
      true

      (and (finite-number? start-position)
           (finite-number? fill-size)
           (finite-number? side-sign))
      (let [end-position (+ start-position (* side-sign fill-size))]
        (case direction
          :long (and (<= start-position 0)
                     (> end-position 0))
          :short (and (>= start-position 0)
                      (< end-position 0))
          false))

      :else false)))

(defn- latest-entry-fill
  [fills direction]
  (->> (or fills [])
       (filter map?)
       (sort-by fill-time)
       (filter #(entry-transition-fill? % direction))
       last))

(defn- timeframe-bucket-seconds
  [timeframe]
  (let [interval-ms (interval/interval-to-milliseconds timeframe)]
    (if (and (finite-number? interval-ms) (pos? interval-ms))
      (max 1 (js/Math.floor (/ interval-ms 1000)))
      1)))

(defn- align-time-to-timeframe
  [time-ms timeframe]
  (let [time-ms* (parse-time-ms time-ms)]
    (when (finite-number? time-ms*)
      (let [time-sec (js/Math.floor (/ time-ms* 1000))
            bucket-sec (timeframe-bucket-seconds timeframe)]
        (* bucket-sec
           (js/Math.floor (/ time-sec bucket-sec)))))))

(defn- marker-position
  [side]
  (if (= side :long) "belowBar" "aboveBar"))

(defn- marker-color
  [side]
  (if (= side :long) long-marker-color short-marker-color))

(defn- marker-label
  [side]
  (if (= side :long) "L" "S"))

(defn- open-position-side
  [position]
  (let [size (parse-num (:szi position))]
    (cond
      (and (finite-number? size) (pos? size)) :long
      (and (finite-number? size) (neg? size)) :short
      :else nil)))

(defn build-position-overlay
  [{:keys [active-asset
           position
           fills
           market-by-key
           selected-timeframe
           candle-data]}]
  (let [position* (or position {})
        side (open-position-side position*)
        size (parse-num (:szi position*))
        abs-size (when (finite-number? size)
                   (js/Math.abs size))]
    (when (and (#{:long :short} side)
               (finite-number? abs-size)
               (pos? abs-size))
      (let [entry-price (parse-num (:entryPx position*))
            unrealized-pnl (or (parse-num (:unrealizedPnl position*)) 0)
            liquidation-price (parse-num (:liquidationPx position*))
            matching-fills (->> (or fills [])
                                (filter #(asset-fill-match? active-asset % market-by-key)))
            entry-fill (latest-entry-fill matching-fills side)
            entry-time-ms (fill-time entry-fill)
            entry-time (align-time-to-timeframe entry-time-ms selected-timeframe)
            latest-time (when (seq candle-data)
                          (:time (last candle-data)))]
        (when (and (finite-number? entry-price)
                   (pos? entry-price))
          {:side side
           :size size
           :abs-size abs-size
           :entry-price entry-price
           :unrealized-pnl unrealized-pnl
           :liquidation-price (when (and (finite-number? liquidation-price)
                                         (pos? liquidation-price))
                                liquidation-price)
           :entry-time entry-time
           :entry-time-ms entry-time-ms
           :latest-time latest-time
           :entry-marker (when (finite-number? entry-time)
                           {:time entry-time
                            :position (marker-position side)
                            :shape "circle"
                            :color (marker-color side)
                            :text (marker-label side)})})))))
