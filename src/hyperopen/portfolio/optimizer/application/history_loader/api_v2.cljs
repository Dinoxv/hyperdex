(ns hyperopen.portfolio.optimizer.application.history-loader.api-v2
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [hyperopen.portfolio.metrics.history :as metrics-history]
            [hyperopen.portfolio.optimizer.domain.history-series :as history-series]
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

(defn- warning-key
  [warning]
  [(:code warning)
   (:instrument-id warning)
   (:proxy-mapping-id warning)
   (:source-id warning)
   (:message warning)])

(defn- distinct-warnings
  [warnings]
  (vec (vals (reduce (fn [acc warning]
                       (assoc acc (warning-key warning) warning))
                     {}
                     warnings))))

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

(defn- local-id-by-backend-id
  [universe]
  (into {}
        (keep (fn [instrument]
                (let [local-id (instruments/normalize-instrument-id instrument)
                      backend-id (non-blank-text
                                  (:optimizer-history/instrument-id instrument))]
                  (when (and local-id backend-id)
                    [backend-id local-id]))))
        (or universe [])))

(defn- canonical-response-id
  [local-id-by-backend raw-id entry]
  (or (get local-id-by-backend raw-id)
      (get local-id-by-backend (:instrument-id entry))
      raw-id))

(defn- normalize-series-by-instrument
  [raw-series universe]
  (let [local-id-by-backend (local-id-by-backend-id universe)]
    (into {}
          (map (fn [[raw-id series]]
                 (let [raw-id* (keyed-map-id raw-id)
                       series* (normalize-series raw-id* series)
                       local-id (canonical-response-id local-id-by-backend
                                                       raw-id*
                                                       series*)]
                   [local-id
                    (assoc series* :local-instrument-id local-id)])))
          (or raw-series {}))))

(defn- normalize-aligned-returns-by-instrument
  [raw-aligned-returns universe]
  (let [local-id-by-backend (local-id-by-backend-id universe)]
    (into {}
          (map (fn [[raw-id entry]]
                 (let [raw-id* (keyed-map-id raw-id)
                       entry* (aligned-entry entry)]
                   [(canonical-response-id local-id-by-backend raw-id* entry*)
                    entry*])))
          (or raw-aligned-returns {}))))

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
  ([api-body]
   (normalize-history-body nil api-body))
  ([request api-body]
  (let [body (normalize-api-map (apply dissoc api-body history-keyed-fields))
        raw-series (keyed-map-field api-body
                                    "series_by_instrument"
                                    "series-by-instrument")
        raw-aligned-returns (keyed-map-field api-body
                                             "aligned_returns_by_instrument"
                                             "aligned-returns-by-instrument")
        universe (:universe request)
        series-by-instrument (normalize-series-by-instrument raw-series
                                                             universe)
        aligned-returns-by-instrument (normalize-aligned-returns-by-instrument
                                       raw-aligned-returns
                                       universe)]
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
     :warnings (normalize-warnings (:warnings body))})))

(defn normalize-history-bundle
  [request api-body]
  (update (normalize-history-body request api-body)
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

(defn- point-return-count
  [series]
  (count (point-return-map series)))

(def ^:private api-v2-hard-warning-codes
  #{:identity-ambiguous
    :instrument-kind-mismatch
    :proxy-mapping-unapproved
    :proxy-validation-failed
    :validation-failed})

(def ^:private api-v2-display-data-warning-codes
  #{:missing-candle-history
    :insufficient-candle-history})

(defn- aligned-return-entry
  [api-v2-history local-id]
  (get-in api-v2-history [:aligned-returns-by-instrument local-id]))

(defn- aligned-return-values
  [api-v2-history local-id]
  (vec (:returns (aligned-return-entry api-v2-history local-id))))

(defn- usable-aligned-returns?
  [api-v2-history local-id min-return-observations]
  (let [return-calendar (vec (:return-calendar api-v2-history))
        returns (aligned-return-values api-v2-history local-id)]
    (and (seq return-calendar)
         (= (count return-calendar) (count returns))
         (>= (count returns) min-return-observations)
         (every? finite-number? returns))))

(defn- all-selected-aligned-returns-usable?
  [api-v2-history local-ids min-return-observations]
  (and (seq local-ids)
       (every? #(usable-aligned-returns?
                 api-v2-history
                 %
                 min-return-observations)
               local-ids)))

(defn- api-v2-blocking-warning?
  [warning]
  (contains? api-v2-hard-warning-codes (:code warning)))

(defn- display-data-warning?
  [warning]
  (contains? api-v2-display-data-warning-codes (:code warning)))

(defn- warning-id-map
  [rows]
  (into {}
        (mapcat (fn [{:keys [instrument-id backend-id]}]
                  (cond-> [[instrument-id instrument-id]]
                    backend-id
                    (conj [backend-id instrument-id]))))
        rows))

(defn- canonical-warning
  [id-map warning]
  (let [warning-id (:instrument-id warning)]
    (cond-> warning
      (contains? id-map warning-id)
      (assoc :instrument-id (get id-map warning-id)))))

(defn- warning-local-id
  [id-map warning]
  (or (get id-map (:instrument-id warning))
      (:instrument-id warning)))

(defn- return-history-warning
  [instrument api-v2-history min-return-observations]
  (let [local-id (instruments/normalize-instrument-id instrument)
        returns (aligned-return-values api-v2-history local-id)
        observations (count (filter finite-number? returns))
        missing? (empty? returns)]
    (cond-> {:code (if missing?
                     :missing-return-history
                     :insufficient-return-history)
             :instrument-id local-id
             :observations observations
             :required min-return-observations
             :market-type (instruments/market-type instrument)}
      missing?
      (dissoc :observations :required))))

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
        min-return-observations (max 1 (dec min-observations*))
        series-by-instrument (:series-by-instrument api-v2-history)
        rows (mapv (fn [instrument]
                     (let [local-id (instruments/normalize-instrument-id instrument)
                           backend-id (non-blank-text
                                       (:optimizer-history/instrument-id instrument))]
                       {:instrument instrument
                        :instrument-id local-id
                        :backend-id backend-id
                        :series (get series-by-instrument local-id)}))
                   (or universe []))
        id-map (warning-id-map rows)
        api-warnings (mapv #(canonical-warning id-map %)
                           (concat (:warnings api-v2-history)
                                   (mapcat (fn [{:keys [series]}]
                                             (:warnings series))
                                           rows)))
        hard-warning-by-local-id (into {}
                                       (keep (fn [warning]
                                               (when (api-v2-blocking-warning? warning)
                                                 [(warning-local-id id-map warning)
                                                  warning])))
                                       api-warnings)
        base-candidates (filterv (fn [{:keys [instrument-id backend-id series]}]
                                   (and instrument-id
                                        (or backend-id series)
                                        (not (contains? hard-warning-by-local-id
                                                        instrument-id))
                                        (not= :rejected (:lineage-kind series))))
                                 rows)
        use-aligned? (all-selected-aligned-returns-usable?
                      api-v2-history
                      (mapv :instrument-id base-candidates)
                      min-return-observations)
        prepared (mapv (fn [{:keys [instrument instrument-id backend-id series]
                             :as row}]
                         (let [hard-warning (get hard-warning-by-local-id
                                                 instrument-id)]
                           (cond
                             (or (not instrument-id)
                                 (and (not backend-id)
                                      (nil? series)))
                             (assoc row
                                    :excluded? true
                                    :warning {:code :identity-ambiguous
                                              :instrument-id instrument-id
                                              :market-type (instruments/market-type
                                                            instrument)})

                             hard-warning
                             (assoc row
                                    :excluded? true
                                    :warning hard-warning)

                             (= :rejected (:lineage-kind series))
                             (assoc row
                                    :excluded? true
                                    :warning (missing-series-warning instrument
                                                                    series))

                             use-aligned?
                             (assoc row :excluded? false)

                             (not (usable-series? series))
                             (assoc row
                                    :excluded? true
                                    :warning (return-history-warning
                                              instrument
                                              api-v2-history
                                              min-return-observations))

                             (< (point-return-count series)
                                min-return-observations)
                             (assoc row
                                    :excluded? true
                                    :warning {:code :insufficient-return-history
                                              :instrument-id instrument-id
                                              :observations (point-return-count
                                                             series)
                                              :required min-return-observations
                                              :market-type (instruments/market-type
                                                            instrument)})

                             :else
                             (assoc row :excluded? false))))
                       rows)
        eligible (filterv (complement :excluded?) prepared)
        eligible-local-ids (mapv :instrument-id eligible)
        series-by-local-id (into {}
                                 (map (fn [{:keys [instrument-id series]}]
                                        [instrument-id series]))
                                 eligible)
        effective-calendar (if (seq (:common-calendar api-v2-history))
                             (vec (:common-calendar api-v2-history))
                             (if use-aligned?
                               (vec (:return-calendar api-v2-history))
                               (common-calendar (map :points
                                                     (vals series-by-local-id)))))
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
        common-gap? (< (count effective-return-calendar)
                       min-return-observations)
        history-warning (when (and (seq eligible)
                                   common-gap?)
                          {:code :insufficient-common-history
                           :observations (count effective-return-calendar)
                           :required min-return-observations})
        effective-eligible (if common-gap? [] eligible)
        excluded-instruments (vec (concat (map :instrument (filter :excluded? prepared))
                                          (when common-gap?
                                            (map :instrument eligible))))
        warnings (distinct-warnings
                  (concat (remove (fn [warning]
                                    (and (display-data-warning? warning)
                                         (or use-aligned?
                                             (some #(= (warning-local-id
                                                       id-map
                                                       warning)
                                                       (:instrument-id %))
                                                   prepared))))
                                  api-warnings)
                          (keep :warning prepared)
                          (keep (fn [{:keys [instrument series]}]
                                  (funding-warning instrument series))
                                eligible)
                          (when history-warning [history-warning])))
        price-series-by-instrument (into {}
                                         (keep (fn [{:keys [instrument-id series]}]
                                                 (when (seq (:points series))
                                                   [instrument-id
                                                    (prices-for-calendar (:points series)
                                                                         effective-calendar)])))
                                         effective-eligible)
        native-history (history-series/native-history-metadata-for-series effective-eligible)
        funding-by-instrument (into {}
                                    (map (fn [instrument]
                                           (let [local-id (instruments/normalize-instrument-id
                                                           instrument)
                                                 series (get series-by-instrument local-id)]
                                             [local-id (funding-summary instrument series)])))
                                    (or universe []))]
    {:calendar effective-calendar
     :return-calendar effective-return-calendar
     :eligible-instruments (mapv :instrument effective-eligible)
     :excluded-instruments excluded-instruments
     :price-series-by-instrument price-series-by-instrument
     :return-series-by-instrument (select-keys return-series-by-instrument (map :instrument-id effective-eligible))
     :return-intervals (return-intervals-for-calendar effective-calendar effective-return-calendar)
     :raw-price-series-by-instrument (:raw-price-series-by-instrument native-history)
     :cadence-by-instrument (:cadence-by-instrument native-history)
     :expected-return-series-by-instrument (:expected-return-series-by-instrument native-history)
     :expected-return-intervals-by-instrument (:expected-return-intervals-by-instrument native-history)
     :risk-estimation (:risk-estimation native-history)
     :funding-by-instrument funding-by-instrument
     :warnings warnings
     :freshness (freshness effective-calendar as-of-ms stale-after-ms)
     :alignment-source {:kind (if use-aligned? :api-v2-aligned-returns :api-v2-point-returns)
                        :status (:status api-v2-history)
                        :dataset-version (:dataset-version api-v2-history)
                        :observations (count effective-calendar)}}))
