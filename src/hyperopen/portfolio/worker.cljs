(ns hyperopen.portfolio.worker
  (:require [hyperopen.portfolio.metrics :as metrics]))

(def ^:private benchmark-relative-metric-keys
  [:r2 :information-ratio])

(defn- request-benchmark-daily-rows
  [portfolio-request]
  (if (contains? portfolio-request :benchmark-daily-rows)
    (or (:benchmark-daily-rows portfolio-request) [])
    (metrics/daily-compounded-returns (or (:benchmark-cumulative-rows portfolio-request)
                                          []))))

(defn- request-strategy-daily-rows
  [request]
  (if (contains? request :strategy-daily-rows)
    (or (:strategy-daily-rows request) [])
    (metrics/daily-compounded-returns (or (:strategy-cumulative-rows request)
                                          []))))

(defn- portfolio-metrics-request
  [portfolio-request]
  (let [benchmark-daily-rows (request-benchmark-daily-rows portfolio-request)]
    {:strategy-cumulative-rows (:strategy-cumulative-rows portfolio-request)
     :strategy-daily-rows (:strategy-daily-rows portfolio-request)
     :benchmark-daily-rows benchmark-daily-rows
     :rf (or (:rf portfolio-request) 0)
     :mar (or (:mar portfolio-request) 0)
     :periods-per-year (or (:periods-per-year portfolio-request) 365)
     :quality-gates (:quality-gates portfolio-request)}))

(defn- benchmark-metrics-request
  [request strategy-daily-rows]
  {:strategy-cumulative-rows (:strategy-cumulative-rows request)
   :strategy-daily-rows strategy-daily-rows
   :rf 0
   :periods-per-year 365})

(defn- overlay-metric-token
  [metric-values token-key metric-key token-value]
  (update metric-values
          token-key
          (fn [tokens]
            (let [tokens* (or tokens {})]
              (if (some? token-value)
                (assoc tokens* metric-key token-value)
                (dissoc tokens* metric-key))))))

(defn- overlay-relative-metric
  [benchmark-values relative-values metric-key]
  (if (or (contains? relative-values metric-key)
          (contains? (or (:metric-status relative-values) {}) metric-key)
          (contains? (or (:metric-reason relative-values) {}) metric-key))
    (-> benchmark-values
        (assoc metric-key (get relative-values metric-key))
        (overlay-metric-token :metric-status
                              metric-key
                              (get-in relative-values [:metric-status metric-key]))
        (overlay-metric-token :metric-reason
                              metric-key
                              (get-in relative-values [:metric-reason metric-key])))
    benchmark-values))

(defn- overlay-relative-benchmark-metrics
  [benchmark-values relative-values]
  (reduce (fn [acc metric-key]
            (overlay-relative-metric acc relative-values metric-key))
          benchmark-values
          benchmark-relative-metric-keys))

(defn- portfolio-relative-benchmark-request
  [portfolio-request benchmark-daily-rows]
  {:strategy-cumulative-rows (:strategy-cumulative-rows portfolio-request)
   :strategy-daily-rows (:strategy-daily-rows portfolio-request)
   :benchmark-daily-rows benchmark-daily-rows
   :rf (or (:rf portfolio-request) 0)
   :mar (or (:mar portfolio-request) 0)
   :periods-per-year (or (:periods-per-year portfolio-request) 365)
   :quality-gates (:quality-gates portfolio-request)})

(defn- metrics-result-payload
  [{:keys [portfolio-request benchmark-requests]}]
  (let [portfolio-result (metrics/compute-performance-metrics
                          (portfolio-metrics-request portfolio-request))
        benchmark-results (into {}
                                (map (fn [{:keys [coin request]}]
                                       (let [benchmark-daily-rows (request-strategy-daily-rows request)
                                             benchmark-values (metrics/compute-performance-metrics
                                                               (benchmark-metrics-request request
                                                                                          benchmark-daily-rows))
                                             relative-values (metrics/compute-performance-metrics
                                                              (portfolio-relative-benchmark-request portfolio-request
                                                                                                    benchmark-daily-rows))]
                                         [coin (overlay-relative-benchmark-metrics benchmark-values
                                                                                   relative-values)])))
                                benchmark-requests)]
    {:portfolio-values portfolio-result
     :benchmark-values-by-coin benchmark-results}))

(defn- handle-message [^js e]
  (let [data (.-data e)
        id (.-id data)
        type (keyword (.-type data))
        payload-js (.-payload data)
        payload (js->clj payload-js :keywordize-keys true)]
    (case type
      :compute-metrics
      (let [payload-result (metrics-result-payload payload)]
        (.postMessage js/self #js {:id id
                                   :type "metrics-result"
                                   :payload (clj->js payload-result)}))
      
      (js/console.warn "Unknown message type received in portfolio worker:" type))))

(defn ^:export init []
  (js/console.log "Portfolio Web Worker initialized.")
  (.addEventListener js/self "message" handle-message))
