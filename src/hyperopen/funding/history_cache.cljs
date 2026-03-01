(ns hyperopen.funding.history-cache
  (:require [clojure.string :as str]
            [hyperopen.api.default :as api]
            [hyperopen.platform :as platform]))

(def cache-retention-window-ms
  (* 30 24 60 60 1000))

(def cache-min-refresh-interval-ms
  (* 60 1000))

(def ^:private cache-version
  1)

(def ^:private cache-key-prefix
  "market-funding-history-cache:")

(defn- finite-number?
  [value]
  (and (number? value)
       (not (js/isNaN value))
       (js/isFinite value)))

(defn- parse-decimal
  [value]
  (cond
    (number? value)
    (when (finite-number? value)
      value)

    (string? value)
    (let [parsed (js/parseFloat value)]
      (when (finite-number? parsed)
        parsed))

    :else
    nil))

(defn- parse-ms
  [value]
  (when-let [parsed (parse-decimal value)]
    (js/Math.floor parsed)))

(defn normalize-coin
  [coin]
  (some-> coin str str/trim str/upper-case not-empty))

(defn cache-key
  [coin]
  (when-let [coin* (normalize-coin coin)]
    (str cache-key-prefix coin*)))

(defn normalize-market-funding-history-row
  [coin row]
  (when (map? row)
    (let [coin* (or (normalize-coin coin)
                    (normalize-coin (:coin row)))
          time-ms (or (parse-ms (:time-ms row))
                      (parse-ms (:time row)))
          funding-rate (parse-decimal (or (:funding-rate-raw row)
                                          (:fundingRate row)
                                          (:funding-rate row)))
          premium (parse-decimal (:premium row))]
      (when (and (seq coin*)
                 (number? time-ms)
                 (number? funding-rate))
        {:coin coin*
         :time-ms time-ms
         :time time-ms
         :funding-rate-raw funding-rate
         :fundingRate funding-rate
         :premium premium}))))

(defn normalize-market-funding-history-rows
  [coin rows]
  (->> (or rows [])
       (keep (fn [row]
               (normalize-market-funding-history-row coin row)))
       (sort-by :time-ms)
       vec))

(defn merge-market-funding-history-rows
  [coin existing incoming]
  (->> (concat (or existing []) (or incoming []))
       (reduce (fn [acc row]
                 (if-let [normalized (normalize-market-funding-history-row coin row)]
                   (assoc acc (:time-ms normalized) normalized)
                   acc))
               {})
       vals
       (sort-by :time-ms)
       vec))

(defn trim-market-funding-history-rows
  ([rows now-ms]
   (trim-market-funding-history-rows rows now-ms cache-retention-window-ms))
  ([rows now-ms retention-window-ms]
   (let [now-ms* (or (parse-ms now-ms) 0)
         retention-ms* (max 0 (or (parse-ms retention-window-ms)
                                  cache-retention-window-ms))
         start-ms (max 0 (- now-ms* retention-ms*))]
     (->> (or rows [])
          (keep (fn [row]
                  (when-let [time-ms (parse-ms (:time-ms row))]
                    (when (and (<= start-ms time-ms)
                               (<= time-ms now-ms*))
                      row))))
          (sort-by :time-ms)
          vec))))

(defn- last-row-time-ms
  [rows]
  (some->> rows
           seq
           last
           :time-ms
           parse-ms))

(defn- empty-cache-snapshot
  [coin now-ms]
  {:version cache-version
   :coin coin
   :rows []
   :last-row-time-ms nil
   :last-sync-ms (or (parse-ms now-ms) 0)})

(defn normalize-market-funding-history-cache
  [coin raw-cache now-ms retention-window-ms]
  (let [coin* (normalize-coin coin)
        now-ms* (or (parse-ms now-ms) 0)
        normalized-rows (normalize-market-funding-history-rows coin* (:rows raw-cache))
        trimmed-rows (trim-market-funding-history-rows normalized-rows
                                                       now-ms*
                                                       retention-window-ms)
        last-sync-ms (or (parse-ms (:last-sync-ms raw-cache)) 0)]
    {:version cache-version
     :coin coin*
     :rows trimmed-rows
     :last-row-time-ms (last-row-time-ms trimmed-rows)
     :last-sync-ms last-sync-ms}))

(defn load-market-funding-history-cache
  ([coin]
   (load-market-funding-history-cache coin {}))
  ([coin {:keys [local-storage-get-fn now-ms-fn retention-window-ms]
          :or {local-storage-get-fn platform/local-storage-get
               now-ms-fn platform/now-ms
               retention-window-ms cache-retention-window-ms}}]
   (let [coin* (normalize-coin coin)
         now-ms* (now-ms-fn)]
     (if-not coin*
       nil
       (let [key (cache-key coin*)]
         (try
           (let [raw (local-storage-get-fn key)]
             (if (seq raw)
               (normalize-market-funding-history-cache coin*
                                                       (js->clj (js/JSON.parse raw)
                                                                :keywordize-keys true)
                                                       now-ms*
                                                       retention-window-ms)
               (empty-cache-snapshot coin* now-ms*)))
           (catch :default _
             (empty-cache-snapshot coin* now-ms*))))))))

(defn persist-market-funding-history-cache!
  ([coin snapshot]
   (persist-market-funding-history-cache! coin snapshot {}))
  ([coin snapshot {:keys [local-storage-set-fn]
                   :or {local-storage-set-fn platform/local-storage-set!}}]
   (when-let [coin* (normalize-coin coin)]
     (let [key (cache-key coin*)
           normalized (normalize-market-funding-history-cache coin*
                                                              snapshot
                                                              (platform/now-ms)
                                                              cache-retention-window-ms)]
       (try
         (local-storage-set-fn key (js/JSON.stringify (clj->js normalized)))
         (catch :default e
           (js/console.warn "Failed to persist market funding history cache:" e)))))))

(defn clear-market-funding-history-cache!
  ([coin]
   (clear-market-funding-history-cache! coin {}))
  ([coin {:keys [local-storage-remove-fn]
          :or {local-storage-remove-fn platform/local-storage-remove!}}]
   (when-let [key (cache-key coin)]
     (local-storage-remove-fn key))))

(defn rows-for-window
  [rows now-ms window-ms]
  (let [now-ms* (or (parse-ms now-ms) 0)
        window-ms* (max 0 (or (parse-ms window-ms) 0))
        start-ms (max 0 (- now-ms* window-ms*))]
    (->> (or rows [])
         (keep (fn [row]
                 (when-let [time-ms (parse-ms (:time-ms row))]
                   (when (and (<= start-ms time-ms)
                              (<= time-ms now-ms*))
                     row))))
         (sort-by :time-ms)
         vec)))

(defn- default-load-cache
  [coin {:keys [now-ms-fn retention-window-ms]}]
  (load-market-funding-history-cache coin
                                     {:now-ms-fn now-ms-fn
                                      :retention-window-ms retention-window-ms}))

(defn- default-persist-cache!
  [coin snapshot]
  (persist-market-funding-history-cache! coin snapshot))

(defn sync-market-funding-history-cache!
  ([coin]
   (sync-market-funding-history-cache! coin {}))
  ([coin {:keys [now-ms-fn
                 request-market-funding-history!
                 load-cache-fn
                 persist-cache-fn
                 retention-window-ms
                 min-refresh-interval-ms
                 force?
                 priority]
          :or {now-ms-fn platform/now-ms
               request-market-funding-history! api/request-market-funding-history!
               load-cache-fn default-load-cache
               persist-cache-fn default-persist-cache!
               retention-window-ms cache-retention-window-ms
               min-refresh-interval-ms cache-min-refresh-interval-ms
               force? false
               priority :high}}]
   (let [coin* (normalize-coin coin)
         now-ms* (or (parse-ms (now-ms-fn)) 0)
         retention-window-ms* (max 0 (or (parse-ms retention-window-ms)
                                         cache-retention-window-ms))
         min-refresh-interval-ms* (max 0 (or (parse-ms min-refresh-interval-ms)
                                             cache-min-refresh-interval-ms))]
     (if-not coin*
       (js/Promise.resolve {:coin nil
                            :rows []
                            :source :invalid-coin
                            :fetched-count 0
                            :start-time-ms nil
                            :end-time-ms nil
                            :last-sync-ms now-ms*})
       (let [cached (normalize-market-funding-history-cache coin*
                                                            (or (load-cache-fn coin* {:now-ms-fn now-ms-fn
                                                                                      :retention-window-ms retention-window-ms})
                                                                {})
                                                            now-ms*
                                                            retention-window-ms*)
             retention-start-ms (max 0 (- now-ms* retention-window-ms*))
             previous-last-time-ms (:last-row-time-ms cached)
             start-time-ms (if (number? previous-last-time-ms)
                             (max retention-start-ms (inc previous-last-time-ms))
                             retention-start-ms)
             end-time-ms now-ms*
             recent-sync? (and (not force?)
                               (number? (:last-sync-ms cached))
                               (< (- now-ms* (:last-sync-ms cached))
                                  min-refresh-interval-ms*))
             no-missing-delta? (> start-time-ms end-time-ms)]
         (cond
           recent-sync?
           (js/Promise.resolve {:coin coin*
                                :rows (:rows cached)
                                :source :cache
                                :reason :recent-sync
                                :fetched-count 0
                                :start-time-ms start-time-ms
                                :end-time-ms end-time-ms
                                :last-sync-ms (:last-sync-ms cached)})

           no-missing-delta?
           (let [snapshot* (assoc cached :last-sync-ms now-ms*)]
             (persist-cache-fn coin* snapshot*)
             (js/Promise.resolve {:coin coin*
                                  :rows (:rows snapshot*)
                                  :source :cache
                                  :reason :up-to-date
                                  :fetched-count 0
                                  :start-time-ms start-time-ms
                                  :end-time-ms end-time-ms
                                  :last-sync-ms now-ms*}))

           :else
           (-> (request-market-funding-history! coin*
                                                {:start-time-ms start-time-ms
                                                 :end-time-ms end-time-ms
                                                 :priority priority})
               (.then (fn [rows]
                        (let [incoming (normalize-market-funding-history-rows coin* rows)
                              merged (merge-market-funding-history-rows coin*
                                                                        (:rows cached)
                                                                        incoming)
                              trimmed (trim-market-funding-history-rows merged
                                                                       now-ms*
                                                                       retention-window-ms*)
                              snapshot* {:version cache-version
                                         :coin coin*
                                         :rows trimmed
                                         :last-row-time-ms (last-row-time-ms trimmed)
                                         :last-sync-ms now-ms*}]
                          (persist-cache-fn coin* snapshot*)
                          {:coin coin*
                           :rows trimmed
                           :source :network
                           :fetched-count (count incoming)
                           :start-time-ms start-time-ms
                           :end-time-ms end-time-ms
                           :last-sync-ms now-ms*}))))))))))
