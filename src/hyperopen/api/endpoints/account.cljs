(ns hyperopen.api.endpoints.account
  (:require [clojure.string :as str]))

(defn- user-funding-request-body
  [address start-time-ms end-time-ms]
  (cond-> {"type" "userFunding"
           "user" address}
    (number? start-time-ms) (assoc "startTime" (js/Math.floor start-time-ms))
    (number? end-time-ms) (assoc "endTime" (js/Math.floor end-time-ms))))

(defn- fetch-user-funding-page!
  [post-info! address start-time-ms end-time-ms opts]
  (post-info! (user-funding-request-body address start-time-ms end-time-ms)
              (merge {:priority :high}
                     opts)))

(defn- funding-history-seq
  [payload]
  (cond
    (sequential? payload)
    payload

    (map? payload)
    (let [data (:data payload)
          nested (or (:fundings payload)
                     (:userFunding payload)
                     (:userFundings payload)
                     (when (map? data)
                       (or (:fundings data)
                           (:userFunding data)
                           (:userFundings data)))
                     data)]
      (if (sequential? nested) nested []))

    :else
    []))

(defn- warn-funding-normalization-drop!
  [start-time-ms end-time-ms raw-rows]
  (let [console-object (some-> js/globalThis .-console)
        warn-fn (some-> console-object .-warn)]
    (when (and (fn? warn-fn)
               (seq raw-rows))
      (.warn console-object
             "Funding history normalization dropped all rows on a non-empty page."
             (clj->js {:event "funding-history-normalization-drop"
                       :start-time-ms start-time-ms
                       :end-time-ms end-time-ms
                       :raw-row-count (count raw-rows)})))))

(defn- fetch-user-funding-history-loop!
  [post-info! normalize-info-funding-rows-fn sort-funding-history-rows-fn
   address start-time-ms end-time-ms opts acc]
  (-> (fetch-user-funding-page! post-info! address start-time-ms end-time-ms opts)
      (.then (fn [payload]
               (let [raw-rows (funding-history-seq payload)
                     rows (normalize-info-funding-rows-fn raw-rows)]
                 (when (and (seq raw-rows)
                            (empty? rows))
                   (warn-funding-normalization-drop! start-time-ms end-time-ms raw-rows))
                 (if (seq rows)
                   (let [max-time-ms (apply max (map :time-ms rows))
                         next-start-ms (inc max-time-ms)
                         acc* (into acc rows)
                         exhausted? (or (nil? max-time-ms)
                                        (= next-start-ms start-time-ms)
                                        (and (number? end-time-ms)
                                             (> next-start-ms end-time-ms)))]
                     (if exhausted?
                       (sort-funding-history-rows-fn acc*)
                       (fetch-user-funding-history-loop! post-info!
                                                        normalize-info-funding-rows-fn
                                                        sort-funding-history-rows-fn
                                                        address
                                                        next-start-ms
                                                        end-time-ms
                                                        opts
                                                        acc*)))
                   (sort-funding-history-rows-fn acc)))))))

(defn request-user-funding-history!
  [post-info! normalize-info-funding-rows-fn sort-funding-history-rows-fn
   address start-time-ms end-time-ms opts]
  (if-not address
    (js/Promise.resolve [])
    (fetch-user-funding-history-loop! post-info!
                                      normalize-info-funding-rows-fn
                                      sort-funding-history-rows-fn
                                      address
                                      start-time-ms
                                      end-time-ms
                                      (merge {:priority :high}
                                             (or opts {}))
                                      [])))

(defn request-spot-clearinghouse-state!
  [post-info! address opts]
  (if-not address
    (js/Promise.resolve nil)
    (post-info! {"type" "spotClearinghouseState"
                 "user" address}
                (merge {:priority :high}
                       opts))))

(defn normalize-user-abstraction-mode
  [abstraction]
  (let [abstraction* (some-> abstraction str str/trim)]
    (case abstraction*
      "unifiedAccount" :unified
      "portfolioMargin" :unified
      "dexAbstraction" :unified
      "default" :classic
      "disabled" :classic
      :classic)))

(defn request-user-abstraction!
  [post-info! address opts]
  (if-not address
    (js/Promise.resolve nil)
    (let [requested-address (some-> address str str/lower-case)
          opts* (merge {:priority :high
                        :dedupe-key [:user-abstraction requested-address]}
                       opts)]
      (post-info! {"type" "userAbstraction"
                   "user" address}
                  opts*))))

(defn request-clearinghouse-state!
  [post-info! address dex opts]
  (if-not address
    (js/Promise.resolve nil)
    (let [body (cond-> {"type" "clearinghouseState"
                        "user" address}
                 (and dex (not= dex "")) (assoc "dex" dex))]
      (post-info! body
                  (merge {:priority :high}
                         opts)))))
