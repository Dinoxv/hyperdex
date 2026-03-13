(ns hyperopen.websocket.diagnostics.view-model
  (:require [clojure.string :as str]
            [hyperopen.trade.layout-actions :as trade-layout-actions]
            [hyperopen.websocket.diagnostics.catalog :as catalog]
            [hyperopen.websocket.diagnostics.policy :as policy]
            [hyperopen.websocket.diagnostics-sanitize :as diagnostics-sanitize]))

(def ^:private market-flush-table-row-limit
  25)

(def footer-links
  [{:label "Docs" :href "#"}
   {:label "Support" :href "#"}
   {:label "Terms" :href "#"}
   {:label "Privacy Policy" :href "#"}])

(def ^:private reset-button-catalog
  [{:id :market_data
    :idle-label "Reset market subs"
    :tooltip "Unsubscribe and resubscribe active market-data streams only. Use this when order book or trades look stuck."
    :click-action [[:actions/ws-diagnostics-reset-market-subscriptions]]}
   {:id :orders_oms
    :idle-label "Reset OMS subs"
    :tooltip "Unsubscribe and resubscribe active Orders/OMS streams only, without forcing a full websocket reconnect."
    :click-action [[:actions/ws-diagnostics-reset-orders-subscriptions]]}
   {:id :all
    :idle-label "Reset all subs"
    :tooltip "Run both market-data and Orders/OMS subscription resets in one pass, without a full reconnect."
    :click-action [[:actions/ws-diagnostics-reset-all-subscriptions]]}])

(def ^:private market-metrics
  [{:id :pending-count
    :label "Pending"
    :tooltip "Current number of coalesced keys waiting for the next frame flush."
    :value-fn (fn [store] (or (:pending-count store) 0))}
   {:id :max-pending-depth
    :label "Max pending"
    :tooltip "Highest pending queue depth observed for this store since runtime reset."
    :value-fn (fn [store] (or (:max-pending-depth store) 0))}
   {:id :overwrite-total
    :label "Overwrites"
    :tooltip "Total queued updates that replaced an existing coalesce key before a flush."
    :value-fn (fn [store] (or (:overwrite-total store) 0))}
   {:id :p95-flush-duration-ms
    :label "P95 flush"
    :tooltip "95th percentile flush duration from the bounded recent flush sample window."
    :value-fn (fn [store] (:p95-flush-duration-ms store))
    :format :ms}
   {:id :last-flush-duration-ms
    :label "Last flush"
    :tooltip "Duration of the most recent flush for this store."
    :value-fn (fn [store] (:last-flush-duration-ms store))
    :format :ms}
   {:id :last-queue-wait-ms
    :label "Last queue wait"
    :tooltip "Time between frame scheduling and flush start for the most recent flush."
    :value-fn (fn [store] (:last-queue-wait-ms store))
    :format :ms}])

(defn format-age-ms
  [age-ms]
  (cond
    (not (number? age-ms))
    "n/a"

    (< age-ms 1000)
    "<1s"

    (< age-ms 60000)
    (str (quot age-ms 1000) "s")

    :else
    (let [minutes (quot age-ms 60000)
          seconds (quot (mod age-ms 60000) 1000)]
      (str minutes "m " seconds "s"))))

(defn format-ms
  [value]
  (if (number? value)
    (str (js/Math.round value) " ms")
    "n/a"))

(defn threshold-label
  [stale-threshold-ms]
  (if (number? stale-threshold-ms)
    (str stale-threshold-ms " ms")
    "n/a"))

(defn- display-value
  [reveal-sensitive? value]
  (if reveal-sensitive?
    value
    (diagnostics-sanitize/sanitize-value :mask value)))

(defn- display-string
  [value fallback]
  (let [text (some-> value str)]
    (if (str/blank? text)
      fallback
      text)))

(defn- transport-state-label
  [transport-state]
  (catalog/status-token-label transport-state))

(defn- format-last-close
  [health]
  (let [close-info (get-in health [:transport :last-close])
        generated-at-ms (:generated-at-ms health)]
    (if (map? close-info)
      (let [code (or (:code close-info) "n/a")
            reason (or (:reason close-info) "n/a")
            at-ms (:at-ms close-info)
            close-age-ms (when (and (number? generated-at-ms) (number? at-ms))
                           (max 0 (- generated-at-ms at-ms)))]
        (str code
             " / "
             reason
             " / "
             (if (number? close-age-ms)
               (str (format-age-ms close-age-ms) " ago")
               "n/a")))
      "n/a")))

(defn- timeline-rows
  [timeline limit now-ms reveal-sensitive?]
  (->> (vec (or timeline []))
       (take-last limit)
       (mapv (fn [entry]
               (let [age-ms (when (number? (:at-ms entry))
                              (max 0 (- now-ms (:at-ms entry))))
                     details* (display-value reveal-sensitive? (:details entry))]
                 {:key (str (:event entry) "|" (:at-ms entry))
                  :event-label (catalog/timeline-event-label (:event entry))
                  :age-label (if (number? age-ms)
                               (str (format-age-ms age-ms) " ago")
                               "n/a")
                  :details-text (when (map? details*)
                                  (pr-str details*))})))))

(defn- group-health-rows
  [health]
  (mapv (fn [group]
          (let [status (catalog/normalize-status
                        (get-in health [:groups group :worst-status]))]
            {:key (name group)
             :label (catalog/group-title group)
             :status status
             :status-label (catalog/status-label status)
             :tone (catalog/status-tone status)}))
        catalog/group-order))

(defn- stream-sort-key
  [{:keys [group topic sub-key]}]
  [(catalog/group-rank (or group :account))
   (str topic)
   (pr-str sub-key)])

(defn- stream-group-models
  [health now-ms reveal-sensitive?]
  (let [rows (->> (get health :streams {})
                  (map (fn [[sub-key stream]]
                         (assoc stream :sub-key sub-key)))
                  (sort-by stream-sort-key)
                  vec)]
    (->> rows
         (group-by (fn [{:keys [group]}]
                     (catalog/normalize-group (or group :account))))
         (sort-by (fn [[group _]]
                    (catalog/group-rank group)))
         (mapv (fn [[group streams]]
                 {:key (name group)
                  :group group
                  :title (catalog/group-title group)
                  :count (count streams)
                  :streams (mapv (fn [{:keys [sub-key
                                              topic
                                              status
                                              last-payload-at-ms
                                              stale-threshold-ms
                                              descriptor
                                              last-seq
                                              seq-gap-detected?
                                              seq-gap-count
                                              last-gap]}]
                                   (let [status* (catalog/normalize-status status)
                                         sub-key* (display-value reveal-sensitive? sub-key)
                                         age-ms (policy/stream-age-ms now-ms {:last-payload-at-ms last-payload-at-ms})
                                         descriptor* (display-value reveal-sensitive? descriptor)
                                         last-gap* (display-value reveal-sensitive? last-gap)]
                                     {:key (str topic "|" (pr-str sub-key))
                                      :topic (or topic "unknown")
                                      :status status*
                                      :status-label (catalog/status-label status*)
                                      :tone (catalog/status-tone status*)
                                      :age-text (format-age-ms age-ms)
                                      :threshold-text (threshold-label stale-threshold-ms)
                                      :sequence-text (if (number? last-seq) (str last-seq) "n/a")
                                      :gap-text (if seq-gap-detected?
                                                  (str "yes (" (or seq-gap-count 0) ")")
                                                  "no")
                                      :last-gap-text (when (map? last-gap*)
                                                       (pr-str last-gap*))
                                      :subscription-text (pr-str sub-key*)
                                      :descriptor-text (pr-str descriptor*)}))
                                 streams)})))))

(defn- market-store-models
  [market-projection reveal-sensitive?]
  (let [stores (->> (:stores market-projection)
                    (sort-by (comp str :store-id))
                    vec)
        flush-events-by-store (group-by :store-id (vec (or (:flush-events market-projection) [])))]
    (mapv (fn [store-summary]
            (let [store-id* (display-value reveal-sensitive? (:store-id store-summary))
                  store-id-text (display-string store-id* "n/a")
                  store-events (get flush-events-by-store (:store-id store-summary))
                  durations (mapv :flush-duration-ms store-events)]
              {:key (str "market-store|" (:store-id store-summary))
               :store-id-text store-id-text
               :store-id-title store-id-text
               :flush-count (or (:flush-count store-summary) 0)
               :metrics (mapv (fn [{:keys [id label tooltip value-fn format]}]
                                (let [raw-value (value-fn store-summary)
                                      value-text (case format
                                                   :ms (format-ms raw-value)
                                                   (str raw-value))]
                                  {:key (name id)
                                   :label label
                                   :tooltip tooltip
                                   :value-text value-text}))
                              market-metrics)
               :durations durations
               :p95-flush-duration-ms (:p95-flush-duration-ms store-summary)
               :sample-count (count durations)}))
          stores)))

(defn- recent-flush-rows
  [market-projection now-ms reveal-sensitive?]
  (->> (vec (or (:flush-events market-projection) []))
       (sort-by (fn [entry] (or (:seq entry) 0)))
       (take-last market-flush-table-row-limit)
       reverse
       (mapv (fn [entry]
               (let [event-at-ms (:at-ms entry)
                     age-ms (when (and (number? now-ms) (number? event-at-ms))
                              (max 0 (- now-ms event-at-ms)))
                     store-id* (display-value reveal-sensitive? (:store-id entry))
                     store-id-text (display-string store-id* "n/a")]
                 {:key (str "market-flush|" (or (:seq entry)
                                               (:at-ms entry)
                                               (:store-id entry)))
                  :age-text (format-age-ms age-ms)
                  :store-id-text store-id-text
                  :store-id-title store-id-text
                  :pending-count (or (:pending-count entry) 0)
                  :overwrite-count (or (:overwrite-count entry) 0)
                  :flush-duration-text (format-ms (:flush-duration-ms entry))
                  :queue-wait-text (format-ms (:queue-wait-ms entry))})))))

(defn- market-projection-model
  [health now-ms reveal-sensitive?]
  (let [market-projection (or (:market-projection health) {})
        stores (market-store-models market-projection reveal-sensitive?)
        flush-rows (recent-flush-rows market-projection now-ms reveal-sensitive?)]
    {:stores stores
     :flush-rows flush-rows
     :flush-count (count flush-rows)
     :empty? (empty? stores)}))

(defn- summary-rows
  [state app-version build-id]
  (let [reset-counts (merge {:market_data 0 :orders_oms 0 :all 0}
                            (get-in state [:websocket-ui :reset-counts]))]
    [{:label "App version"
      :value (or app-version "n/a")}
     {:label "Build id"
      :value (or build-id "n/a")}
     {:label "Reconnect count"
      :value (str (or (get-in state [:websocket-ui :reconnect-count]) 0))}
     {:label "Reset count (market/oms/all)"
      :value (str (get reset-counts :market_data 0)
                  "/"
                  (get reset-counts :orders_oms 0)
                  "/"
                  (get reset-counts :all 0))}
     {:label "Auto-recover count"
      :value (str (or (get-in state [:websocket-ui :auto-recover-count]) 0))}]))

(defn- connection-meter-summary
  [meter]
  {:rows [{:label "Status"
           :value (:label meter)}
          {:label "Source"
           :value (:source-label meter)}
          {:label "Bars"
           :value (str (:active-bars meter) "/" (:bar-count meter))}
          {:label "Score"
           :value (str (:score meter) "/100")}
          {:label "Penalty"
           :value (str (:penalty meter))}]
   :breakdown (mapv (fn [{:keys [id label penalty detail]}]
                      {:key (name id)
                       :label label
                       :penalty-text (str penalty)
                       :detail detail})
                    (:breakdown meter))})

(defn- transport-rows
  [health now-ms]
  [{:label "State"
    :value (transport-state-label (get-in health [:transport :state]))}
   {:label "Freshness"
    :value (catalog/status-label (get-in health [:transport :freshness]))}
   {:label "Expected traffic"
    :value (if (get-in health [:transport :expected-traffic?]) "yes" "no")}
   {:label "Last message age"
    :value (format-age-ms (policy/transport-last-recv-age-ms now-ms health))}
   {:label "Reconnect attempts"
    :value (str (or (get-in health [:transport :attempt]) 0))}
   {:label "Last close"
    :value (format-last-close health)
    :class ["ml-3" "text-right"]}])

(defn- reset-buttons
  [reset-availability]
  (mapv (fn [{:keys [id idle-label tooltip click-action]}]
          {:key (name id)
           :label (if (= "Reset" (:label reset-availability))
                    idle-label
                    (:label reset-availability))
           :tooltip tooltip
           :disabled? (:disabled? reset-availability)
           :click-action click-action})
        reset-button-catalog))

(defn- diagnostics-model
  [state health meter {:keys [app-version
                              build-id
                              diagnostics-timeline-limit
                              display-now-ms
                              effective-now-ms]}]
  (let [timeline (vec (get-in state [:websocket-ui :diagnostics-timeline] []))
        reveal-sensitive? (boolean (get-in state [:websocket-ui :reveal-sensitive?] false))
        copy-status (get-in state [:websocket-ui :copy-status])
        reconnect-availability (policy/reconnect-availability state health effective-now-ms)
        reset-availability (policy/reset-availability state health effective-now-ms)]
    {:reconnect-control reconnect-availability
     :reset-buttons (reset-buttons reset-availability)
     :reveal-sensitive? reveal-sensitive?
     :show-surface-freshness-cues?
     (boolean (get-in state [:websocket-ui :show-surface-freshness-cues?] false))
     :copy-status (when copy-status
                    {:kind (:kind copy-status)
                     :message (:message copy-status)
                     :fallback-json (:fallback-json copy-status)})
     :summary-rows (summary-rows state app-version build-id)
     :connection-meter (connection-meter-summary meter)
     :timeline (timeline-rows timeline diagnostics-timeline-limit display-now-ms reveal-sensitive?)
     :transport-rows (transport-rows health display-now-ms)
     :group-health (group-health-rows health)
     :stream-groups (stream-group-models health display-now-ms reveal-sensitive?)
     :market-projection (market-projection-model health display-now-ms reveal-sensitive?)}))

(defn- mobile-nav-model
  [state]
  (let [route (get-in state [:router :path] "/trade")
        trade-route? (str/starts-with? route "/trade")
        portfolio-route? (str/starts-with? route "/portfolio")
        mobile-surface (trade-layout-actions/normalize-trade-mobile-surface
                        (get-in state [:trade-ui :mobile-surface]))
        markets-active? (and trade-route?
                             (contains? trade-layout-actions/market-mobile-surfaces
                                        mobile-surface))
        account-active? (or portfolio-route?
                            (and trade-route? (= mobile-surface :account)))
        trade-active? (and trade-route? (= mobile-surface :ticket))]
    {:items [{:label "Markets"
              :active? markets-active?
              :click-action (if trade-route?
                              [[:actions/select-trade-mobile-surface :chart]]
                              [[:actions/navigate "/trade"]])
              :icon-kind :markets
              :data-role "mobile-bottom-nav-markets"}
             {:label "Trade"
              :active? trade-active?
              :click-action (if trade-route?
                              [[:actions/select-trade-mobile-surface :ticket]]
                              [[:actions/navigate "/trade"]])
              :icon-kind :trade
              :data-role "mobile-bottom-nav-trade"}
             {:label "Account"
              :active? account-active?
              :click-action (if trade-route?
                              [[:actions/select-trade-mobile-surface :account]]
                              [[:actions/navigate "/portfolio"]])
              :icon-kind :account
              :data-role "mobile-bottom-nav-account"}]}))

(defn footer-view-model
  ([state]
   (footer-view-model state {}))
  ([state {:keys [app-version
                  build-id
                  wall-now-ms
                  diagnostics-timeline-limit
                  network-hint]
           :or {app-version nil
                build-id nil
                wall-now-ms nil
                diagnostics-timeline-limit 25
                network-hint nil}}]
   (let [health (get-in state [:websocket :health] {})
         generated-at-ms (:generated-at-ms health)
         display-now-ms (policy/display-now-ms generated-at-ms wall-now-ms)
         effective-now-ms (policy/effective-now-ms generated-at-ms wall-now-ms)
         meter (policy/connection-meter-model health
                                              {:wall-now-ms display-now-ms
                                               :network-hint network-hint})
         diagnostics-open? (boolean (get-in state [:websocket-ui :diagnostics-open?] false))
         banner (policy/banner-model state health)
         diagnostics (when diagnostics-open?
                       (diagnostics-model state
                                          health
                                          meter
                                          {:app-version app-version
                                           :build-id build-id
                                           :diagnostics-timeline-limit diagnostics-timeline-limit
                                           :display-now-ms display-now-ms
                                           :effective-now-ms effective-now-ms}))]
     (cond-> {:diagnostics-open? diagnostics-open?
              :connection-meter meter
              :mobile-nav (mobile-nav-model state)
              :footer-links footer-links}
       banner
       (assoc :banner banner)

       diagnostics
       (assoc :diagnostics diagnostics)))))
