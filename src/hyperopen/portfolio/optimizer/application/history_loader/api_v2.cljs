(ns hyperopen.portfolio.optimizer.application.history-loader.api-v2
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [hyperopen.portfolio.metrics.history :as metrics-history]
            [hyperopen.portfolio.optimizer.application.history-loader.instruments :as instruments]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def contract-version
  "optimizer-history-api-v2")

(def default-min-observations
  2)

(defn- non-blank-text
  [value]
  (coercion/non-blank-text value))

(defn- finite-number?
  [value]
  (coercion/finite-number? value))

(defn- parse-number
  [value]
  (coercion/parse-number value))

(defn- parse-ms
  [value]
  (coercion/parse-ms value))

(defn- kebab-token
  [value]
  (-> value
      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
      (str/replace #"_" "-")
      str/lower-case))

(defn- key->kebab-keyword
  [key]
  (cond
    (keyword? key) (keyword (kebab-token (name key)))
    (string? key) (keyword (kebab-token key))
    :else key))

(defn normalize-api-map
  [value]
  (cond
    (map? value)
    (into {}
          (map (fn [[k v]]
                 [(key->kebab-keyword k) (normalize-api-map v)]))
          value)

    (sequential? value)
    (mapv normalize-api-map value)

    :else
    value))

(def ^:private history-keyed-fields
  [:series_by_instrument
   :series-by-instrument
   "series_by_instrument"
   "series-by-instrument"
   :aligned_returns_by_instrument
   :aligned-returns-by-instrument
   "aligned_returns_by_instrument"
   "aligned-returns-by-instrument"])

(defn- keyed-map-field
  [m snake-key kebab-key]
  (or (get m (keyword snake-key))
      (get m (keyword kebab-key))
      (get m snake-key)
      (get m kebab-key)
      {}))

(defn- keyed-map-id
  [key]
  (cond
    (keyword? key) (subs (str key) 1)
    (string? key) key
    :else (str key)))

(defn- keyword-like
  [value]
  (coercion/normalize-keyword-like value))

(defn- normalize-warning
  [warning]
  (let [warning* (normalize-api-map warning)]
    (cond-> warning*
      (:code warning*) (update :code keyword-like))))

(defn- normalize-warnings
  [warnings]
  (mapv normalize-warning (or warnings [])))

(defn- normalize-proxy
  [proxy]
  (when (map? proxy)
    (cond-> (normalize-api-map proxy)
      (:mapping-kind proxy) (update :mapping-kind keyword-like))))

(defn- normalize-history-coverage
  [history]
  (when (map? history)
    (let [history* (normalize-api-map history)]
      (cond-> history*
        (:status history*) (update :status keyword-like)
        (:quality-status history*) (update :quality-status keyword-like)
        (map? (:native-only history*))
        (update-in [:native-only :status] keyword-like)
        (map? (:approved-proxy-allowed history*))
        (update-in [:approved-proxy-allowed :status] keyword-like)
        (get-in history* [:approved-proxy-allowed :lineage-kind])
        (update-in [:approved-proxy-allowed :lineage-kind] keyword-like)
        (get-in history* [:approved-proxy-allowed :series-kind])
        (update-in [:approved-proxy-allowed :series-kind] keyword-like)))))

(defn- normalize-discovery-instrument
  [instrument]
  (let [instrument* (normalize-api-map instrument)]
    (cond-> instrument*
      (:instrument-kind instrument*) (update :instrument-kind keyword-like)
      (:proxy instrument*) (update :proxy normalize-proxy)
      (:history instrument*) (update :history normalize-history-coverage))))

(defn normalize-discovery
  [api-body]
  (let [body (normalize-api-map api-body)
        instruments (mapv normalize-discovery-instrument
                          (:instruments body))
        instruments-by-backend-id (into {}
                                        (keep (fn [{:keys [instrument-id] :as instrument}]
                                                (when (seq instrument-id)
                                                  [instrument-id instrument])))
                                        instruments)
        backend-id-by-local-id (into {}
                                     (keep (fn [{:keys [instrument-id aliases]}]
                                             (when-let [local-id (non-blank-text
                                                                  (:hyperopen-market-key aliases))]
                                               [local-id instrument-id])))
                                     instruments)]
    {:status (or (keyword-like (:status body)) :idle)
     :contract-version (:contract-version body)
     :request-id (:request-id body)
     :dataset-version (:dataset-version body)
     :loaded-at-ms (:loaded-at-ms body)
     :instruments-by-backend-id instruments-by-backend-id
     :backend-id-by-local-id backend-id-by-local-id
     :warnings (normalize-warnings (:warnings body))
     :error (:error body)}))

(defn with-discovery-metadata
  [local-market discovery]
  (let [local-id (non-blank-text (or (:key local-market)
                                     (:instrument-id local-market)))
        backend-id (get-in discovery [:backend-id-by-local-id local-id])
        instrument (get-in discovery [:instruments-by-backend-id backend-id])
        history (:history instrument)]
    (cond-> local-market
      backend-id
      (assoc :optimizer-history/instrument-id backend-id)

      (:display-symbol instrument)
      (assoc :optimizer-history/display-symbol (:display-symbol instrument))

      (:instrument-kind instrument)
      (assoc :optimizer-history/instrument-kind (:instrument-kind instrument))

      (:status history)
      (assoc :optimizer-history/history-status (:status history))

      (:quality-status history)
      (assoc :optimizer-history/quality-status (:quality-status history))

      (:proxy instrument)
      (assoc :optimizer-history/proxy (:proxy instrument)))))

(defn- normalize-point
  [point]
  (let [point* (normalize-api-map point)
        time-ms (parse-ms (:time-ms point*))
        close (parse-number (:close point*))
        index-value (parse-number (:index-value point*))
        return-value (parse-number (:return point*))]
    (cond-> point*
      time-ms (assoc :time-ms time-ms)
      close (assoc :close close)
      index-value (assoc :index-value index-value)
      (or (finite-number? return-value)
          (contains? point* :return))
      (assoc :return return-value)
      (:component point*) (update :component keyword-like))))

(defn- normalize-funding
  [funding]
  (when (map? funding)
    (let [funding* (normalize-api-map funding)]
      (cond-> funding*
        (:status funding*) (update :status keyword-like)
        (:annualized-carry funding*) (update :annualized-carry parse-number)
        (:observation-count funding*) (update :observation-count parse-ms)))))

(defn- normalize-quality
  [quality]
  (when (map? quality)
    (let [quality* (normalize-api-map quality)]
      (cond-> quality*
        (:status quality*) (update :status keyword-like)
        (:warnings quality*) (update :warnings normalize-warnings)))))

(defn- normalize-series
  [local-id series]
  (let [series* (normalize-api-map series)]
    (cond-> series*
      true (assoc :local-instrument-id local-id)
      (:lineage-kind series*) (update :lineage-kind keyword-like)
      (:series-kind series*) (update :series-kind keyword-like)
      (:points series*) (update :points #(mapv normalize-point %))
      (:funding series*) (update :funding normalize-funding)
      (:quality series*) (update :quality normalize-quality)
      (:proxy series*) (update :proxy normalize-proxy)
      true (update :warnings (fn [warnings]
                               (mapv #(cond-> (normalize-warning %)
                                        (not (:instrument-id %))
                                        (assoc :instrument-id local-id))
                                     (or warnings [])))))))

(defn- aligned-entry
  [entry]
  (let [entry* (normalize-api-map entry)]
    (cond-> entry*
      (:returns entry*) (update :returns #(mapv parse-number %)))))

(defn- identity-ambiguous-warnings
  [universe]
  (into []
        (keep (fn [instrument]
                (let [local-id (instruments/normalize-instrument-id instrument)]
                  (when (and local-id
                             (not (non-blank-text
                                   (:optimizer-history/instrument-id instrument))))
                    {:code :identity-ambiguous
                     :instrument-id local-id
                     :market-type (instruments/market-type instrument)}))))
        (or universe [])))

(defn normalize-history-body
  [api-body]
  (let [body (normalize-api-map (apply dissoc api-body history-keyed-fields))
        raw-series (keyed-map-field api-body
                                    "series_by_instrument"
                                    "series-by-instrument")
        raw-aligned-returns (keyed-map-field api-body
                                             "aligned_returns_by_instrument"
                                             "aligned-returns-by-instrument")
        series-by-instrument (into {}
                                   (map (fn [[local-id series]]
                                          (let [local-id* (keyed-map-id local-id)]
                                            [local-id*
                                             (normalize-series local-id* series)])))
                                   (or raw-series {}))
        aligned-returns-by-instrument (into {}
                                            (map (fn [[local-id entry]]
                                                   [(keyed-map-id local-id)
                                                    (aligned-entry entry)]))
                                            (or raw-aligned-returns {}))]
    {:contract-version (:contract-version body)
     :request-id (:request-id body)
     :dataset-version (:dataset-version body)
     :error (:error body)
     :message (:message body)
     :status (or (keyword-like (:status body)) :ok)
     :common-calendar (mapv parse-ms (or (:common-calendar body) []))
     :return-calendar (mapv parse-ms (or (:return-calendar body) []))
     :aligned-returns-by-instrument aligned-returns-by-instrument
     :series-by-instrument series-by-instrument
     :warnings (normalize-warnings (:warnings body))}))

(defn normalize-history-bundle
  [request api-body]
  (update (normalize-history-body api-body)
          :warnings
          #(vec (concat (or % [])
                        (identity-ambiguous-warnings (:universe request))))))

(defn- row-by-time
  [rows]
  (into {}
        (map (juxt :time-ms identity))
        rows))

(defn- common-calendar
  [histories]
  (let [sets (map #(set (keep :time-ms %)) histories)]
    (if (seq sets)
      (->> (apply set/intersection sets)
           sort
           vec)
      [])))

(defn- return-intervals
  [calendar]
  (mapv (fn [[start-ms end-ms]]
          (let [dt-ms (- end-ms start-ms)
                dt-days (/ dt-ms metrics-history/day-ms)]
            {:start-ms start-ms
             :end-ms end-ms
             :dt-days dt-days
             :dt-years (/ dt-days 365.2425)}))
        (partition 2 1 calendar)))

(defn- return-intervals-for-calendar
  [calendar return-calendar]
  (let [previous-by-end (into {}
                              (map (fn [[start-ms end-ms]]
                                     [end-ms start-ms]))
                              (partition 2 1 calendar))]
    (mapv (fn [end-ms]
            (let [start-ms (get previous-by-end end-ms)
                  dt-ms (when (and (number? start-ms)
                                   (number? end-ms))
                          (- end-ms start-ms))
                  dt-days (when (number? dt-ms)
                            (/ dt-ms metrics-history/day-ms))]
              {:start-ms start-ms
               :end-ms end-ms
               :dt-days dt-days
               :dt-years (when (number? dt-days)
                           (/ dt-days 365.2425))}))
          return-calendar)))

(defn- freshness
  [calendar as-of-ms stale-after-ms]
  (let [latest-common-ms (last calendar)
        oldest-common-ms (first calendar)
        age-ms (when (and (number? as-of-ms)
                          (number? latest-common-ms))
                 (- as-of-ms latest-common-ms))
        stale? (if (number? latest-common-ms)
                 (and (number? stale-after-ms)
                      (number? age-ms)
                      (> age-ms stale-after-ms))
                 true)]
    {:as-of-ms as-of-ms
     :latest-common-ms latest-common-ms
     :oldest-common-ms oldest-common-ms
     :age-ms age-ms
     :stale? (boolean stale?)}))

(defn- usable-series?
  [series]
  (and (map? series)
       (not (contains? #{:missing :rejected}
                       (:lineage-kind series)))
       (seq (:points series))))

(defn- missing-series-warning
  [instrument series]
  (let [local-id (instruments/normalize-instrument-id instrument)
        series-warning (first (:warnings series))]
    (or series-warning
        {:code (if (= :rejected (:lineage-kind series))
                 :validation-failed
                 :missing-candle-history)
         :instrument-id local-id
         :market-type (instruments/market-type instrument)})))

(defn- funding-summary
  [instrument series]
  (let [funding (:funding series)
        status (:status funding)
        carry (or (:annualized-carry funding) 0)
        perp? (instruments/perp-instrument? instrument)]
    (case status
      :available
      {:source :history-api-v2
       :annualized-carry carry
       :status :available}

      :not-applicable
      {:source :not-applicable
       :annualized-carry 0
       :status :not-applicable}

      (:missing :rejected)
      (if perp?
        {:source :missing-market-funding-history
         :annualized-carry 0
         :status status}
        {:source :not-applicable
         :annualized-carry 0
         :status :not-applicable
         :diagnostic-status status})

      (if perp?
        {:source :missing-market-funding-history
         :annualized-carry 0
         :status :missing}
        {:source :not-applicable
         :annualized-carry 0
         :status :not-applicable}))))

(defn- funding-warning
  [instrument series]
  (when (and (instruments/perp-instrument? instrument)
             (= :missing (get-in series [:funding :status])))
    {:code :funding-history-missing
     :instrument-id (instruments/normalize-instrument-id instrument)}))

(defn- prices-for-calendar
  [points calendar]
  (let [by-time (row-by-time points)]
    (mapv #(get by-time %) calendar)))

(defn- point-return-map
  [series]
  (into {}
        (keep (fn [{:keys [time-ms return]}]
                (when (and (number? time-ms)
                           (finite-number? return))
                  [time-ms return])))
        (:points series)))

(defn- point-level-return-calendar
  [series-by-local-id calendar]
  (let [return-maps (map point-return-map (vals series-by-local-id))]
    (->> calendar
         (filter (fn [time-ms]
                   (every? #(finite-number? (get % time-ms)) return-maps)))
         vec)))

(defn- returns-from-point-level
  [series-by-local-id return-calendar]
  (into {}
        (map (fn [[local-id series]]
               (let [returns (point-return-map series)]
                 [local-id (mapv #(get returns %) return-calendar)])))
        series-by-local-id))

(defn- aligned-returns-available?
  [api-v2-history eligible-local-ids]
  (let [return-calendar (:return-calendar api-v2-history)]
    (and (seq return-calendar)
         (every? (fn [local-id]
                   (= (count return-calendar)
                      (count (get-in api-v2-history
                                     [:aligned-returns-by-instrument
                                      local-id
                                      :returns]))))
                 eligible-local-ids))))

(defn align-api-v2-history-inputs
  [{:keys [universe
           api-v2-history
           as-of-ms
           stale-after-ms
           min-observations]}]
  (let [min-observations* (or min-observations default-min-observations)
        series-by-instrument (:series-by-instrument api-v2-history)
        prepared (mapv (fn [instrument]
                         (let [local-id (instruments/normalize-instrument-id instrument)
                               backend-id (non-blank-text
                                           (:optimizer-history/instrument-id instrument))
                               series (get series-by-instrument local-id)]
                           (cond
                             (and (not backend-id)
                                  (nil? series))
                             {:instrument instrument
                              :instrument-id local-id
                              :excluded? true
                              :warning {:code :identity-ambiguous
                                        :instrument-id local-id
                                        :market-type (instruments/market-type instrument)}}

                             (not (usable-series? series))
                             {:instrument instrument
                              :instrument-id local-id
                              :series series
                              :excluded? true
                              :warning (missing-series-warning instrument series)}

                             (< (count (:points series)) min-observations*)
                             {:instrument instrument
                              :instrument-id local-id
                              :series series
                              :excluded? true
                              :warning {:code :insufficient-candle-history
                                        :instrument-id local-id
                                        :observations (count (:points series))
                                        :required min-observations*}}

                             :else
                             {:instrument instrument
                              :instrument-id local-id
                              :series series
                              :excluded? false})))
                       (or universe []))
        eligible (filterv (complement :excluded?) prepared)
        eligible-local-ids (mapv :instrument-id eligible)
        series-by-local-id (into {}
                                 (map (fn [{:keys [instrument-id series]}]
                                        [instrument-id series]))
                                 eligible)
        effective-calendar (if (seq (:common-calendar api-v2-history))
                             (vec (:common-calendar api-v2-history))
                             (common-calendar (map :points (vals series-by-local-id))))
        use-aligned? (aligned-returns-available? api-v2-history eligible-local-ids)
        effective-return-calendar (if use-aligned?
                                    (vec (:return-calendar api-v2-history))
                                    (point-level-return-calendar series-by-local-id
                                                                 effective-calendar))
        return-series-by-instrument (if use-aligned?
                                      (into {}
                                            (map (fn [local-id]
                                                   [local-id
                                                    (get-in api-v2-history
                                                            [:aligned-returns-by-instrument
                                                             local-id
                                                             :returns])]))
                                            eligible-local-ids)
                                      (returns-from-point-level series-by-local-id
                                                                effective-return-calendar))
        common-gap? (or (< (count effective-calendar) min-observations*)
                        (empty? effective-return-calendar))
        history-warning (when (and (seq eligible)
                                   common-gap?)
                          {:code :insufficient-common-history
                           :observations (count effective-return-calendar)
                           :required (dec min-observations*)})
        effective-eligible (if common-gap? [] eligible)
        excluded-instruments (vec (concat (map :instrument (filter :excluded? prepared))
                                          (when common-gap?
                                            (map :instrument eligible))))
        warnings (vec (concat (:warnings api-v2-history)
                              (keep :warning prepared)
                              (mapcat (fn [{:keys [instrument series]}]
                                        (cond-> (vec (:warnings series))
                                          (funding-warning instrument series)
                                          (conj (funding-warning instrument series))))
                                      eligible)
                              (when history-warning [history-warning])))
        price-series-by-instrument (into {}
                                         (map (fn [{:keys [instrument-id series]}]
                                                [instrument-id
                                                 (prices-for-calendar (:points series)
                                                                      effective-calendar)]))
                                         effective-eligible)
        funding-by-instrument (into {}
                                    (map (fn [instrument]
                                           (let [local-id (instruments/normalize-instrument-id
                                                           instrument)
                                                 series (get series-by-instrument local-id)]
                                             [local-id
                                              (funding-summary instrument series)])))
                                    (or universe []))]
    {:calendar effective-calendar
     :return-calendar effective-return-calendar
     :eligible-instruments (mapv :instrument effective-eligible)
     :excluded-instruments excluded-instruments
     :price-series-by-instrument price-series-by-instrument
     :return-series-by-instrument (select-keys return-series-by-instrument
                                               (map :instrument-id effective-eligible))
     :return-intervals (return-intervals-for-calendar effective-calendar
                                                       effective-return-calendar)
     :expected-return-series-by-instrument {}
     :expected-return-intervals-by-instrument {}
     :funding-by-instrument funding-by-instrument
     :warnings warnings
     :freshness (freshness effective-calendar as-of-ms stale-after-ms)
     :alignment-source {:kind (if use-aligned?
                                :api-v2-aligned-returns
                                :api-v2-point-returns)
                        :status (:status api-v2-history)
                        :dataset-version (:dataset-version api-v2-history)
                        :observations (count effective-calendar)}}))
