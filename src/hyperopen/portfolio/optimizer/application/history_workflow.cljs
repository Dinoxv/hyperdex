(ns hyperopen.portfolio.optimizer.application.history-workflow
  (:require [hyperopen.portfolio.optimizer.application.history-loader.api-v2 :as history-api-v2]
            [hyperopen.portfolio.optimizer.application.history-prefetch :as history-prefetch]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(def default-funding-window-ms
  (* 365 24 60 60 1000))

(defn request-signature
  [request]
  (select-keys request
               [:universe
                :interval
                :bars
                :priority
                :now-ms
                :funding-window-ms
                :funding-start-ms
                :funding-end-ms]))

(defn optimizer-runtime
  [state]
  (get-in state contracts/runtime-path))

(defn- enrich-universe-from-discovery
  [state universe]
  (let [discovery (get-in state contracts/history-discovery-path)]
    (mapv #(history-api-v2/with-discovery-metadata % discovery)
          (or universe []))))

(defn history-request
  [state opts]
  (let [opts* (or opts {})
        runtime (optimizer-runtime state)
        now-ms (or (:now-ms opts*)
                   (:as-of-ms runtime))
        request (merge {:universe (get-in state contracts/draft-universe-path)
                        :interval :1d
                        :bars 365
                        :priority :high
                        :now-ms now-ms
                        :funding-window-ms default-funding-window-ms}
                       (select-keys runtime
                                    [:stale-after-ms
                                     :funding-periods-per-year
                                     :funding-window-ms])
                       (dissoc opts* :now-ms))]
    (update request :universe #(enrich-universe-from-discovery state %))))

(defn begin-history-load-state
  [signature started-at-ms]
  {:status :loading
   :request-signature signature
   :started-at-ms started-at-ms
   :completed-at-ms nil
   :error nil
   :warnings []})

(defn success-history-load-state
  [current-state completed-at-ms bundle]
  {:status :succeeded
   :request-signature (:request-signature current-state)
   :started-at-ms (:started-at-ms current-state)
   :completed-at-ms completed-at-ms
   :error nil
   :warnings (vec (:warnings bundle))})

(defn error-message
  [err]
  (or (when (map? err)
        (:message err))
      (some-> err .-message)
      (str err)))

(defn failed-history-load-state
  [current-state completed-at-ms err]
  {:status :failed
   :request-signature (:request-signature current-state)
   :started-at-ms (:started-at-ms current-state)
   :completed-at-ms completed-at-ms
   :error {:message (error-message err)}
   :warnings []})

(defn request-opts
  [opts]
  (dissoc (or opts {}) :on-progress :source :queue? :merge?))

(defn selection-prefetch?
  [opts]
  (and (= :selection-prefetch (:source opts))
       (:queue? opts)))

(defn merge-history-bundle
  [history-data bundle completed-at-ms]
  (-> (or history-data {})
      (update :candle-history-by-coin
              merge
              (or (:candle-history-by-coin bundle) {}))
      (update :funding-history-by-coin
              merge
              (or (:funding-history-by-coin bundle) {}))
      (update :vault-details-by-address
              merge
              (or (:vault-details-by-address bundle) {}))
      (update :api-v2-history
              (fn [existing]
                (if-let [api-v2-history (:api-v2-history bundle)]
                  (let [existing* (or existing {})]
                    (-> (merge existing* api-v2-history)
                        (assoc :series-by-instrument
                               (merge (or (:series-by-instrument existing*) {})
                                      (or (:series-by-instrument api-v2-history)
                                          {})))
                        (assoc :aligned-returns-by-instrument
                               (merge (or (:aligned-returns-by-instrument
                                           existing*)
                                          {})
                                      (or (:aligned-returns-by-instrument
                                           api-v2-history)
                                          {})))
                        (update :warnings
                                #(vec (concat (or (:warnings existing) [])
                                              (or (:warnings api-v2-history)
                                                  []))))))
                  existing)))
      (update :warnings
              #(vec (concat (or % []) (or (:warnings bundle) []))))
      (assoc :loaded-at-ms completed-at-ms)))

(defn remove-queued-instrument
  [queue instrument-id*]
  (vec (remove #(= instrument-id* (history-prefetch/instrument-id %))
               (or queue []))))

(defn finish-selection-prefetch-state
  [prefetch-state instrument-id* status]
  (let [prefetch-state* (merge history-prefetch/default-state prefetch-state)]
    (cond-> (-> prefetch-state*
                (update :queue remove-queued-instrument instrument-id*)
                (assoc-in [:by-instrument-id instrument-id*] status))
      (= instrument-id* (:active-instrument-id prefetch-state*))
      (assoc :active-instrument-id nil))))

(defn current-universe-ids
  [state]
  (keep :instrument-id
        (get-in state contracts/draft-universe-path)))

(defn begin-selection-prefetch-state
  [state instrument-id* signature started-at-ms]
  (-> state
      (assoc-in contracts/history-load-state-path
                (begin-history-load-state signature started-at-ms))
      (update-in contracts/history-prefetch-path
                 (fn [prefetch-state]
                   (-> (merge history-prefetch/default-state prefetch-state)
                       (assoc :active-instrument-id instrument-id*)
                       (assoc-in [:by-instrument-id instrument-id*]
                                 (history-prefetch/loading-status started-at-ms)))))))

(defn request-history-command
  [{:keys [source instrument-id request signature]}]
  {:command/type :optimizer.workflow/request-history-bundle
   :source source
   :instrument-id instrument-id
   :request-signature signature
   :request request})

(defn begin-selection-prefetch
  [{:keys [state opts now-ms started-at-ms]}]
  (let [prefetch-state (history-prefetch/prefetch-state state)
        active-id (:active-instrument-id prefetch-state)
        instrument (history-prefetch/first-queued-instrument state)
        instrument-id* (history-prefetch/instrument-id instrument)]
    (cond
      active-id
      {:state state
       :commands []}

      (nil? instrument-id*)
      {:state state
       :commands []}

      :else
      (let [prefetch-universe (if (get-in state (conj contracts/history-data-path
                                                       :api-v2-history))
                                (vec (get-in state contracts/draft-universe-path))
                                [instrument])
            request (history-request state
                                     (assoc (request-opts opts)
                                            :universe prefetch-universe
                                            :now-ms now-ms))
            signature (request-signature request)
            started-at-ms* (or started-at-ms now-ms)
            state* (begin-selection-prefetch-state state
                                                   instrument-id*
                                                   signature
                                                   started-at-ms*)]
        {:state state*
         :commands [(request-history-command
                     {:source :selection-prefetch
                      :instrument-id instrument-id*
                      :request request
                      :signature signature})]}))))

(defn apply-history-success
  [state signature completed-at-ms bundle]
  (if (= signature
         (get-in state contracts/history-load-state-request-signature-path))
    (let [current-state (get-in state contracts/history-load-state-path)]
      (-> state
          (assoc-in contracts/history-data-path
                    (assoc bundle :loaded-at-ms completed-at-ms))
          (assoc-in contracts/history-load-state-path
                    (success-history-load-state current-state completed-at-ms bundle))))
    state))

(defn apply-history-error
  [state signature completed-at-ms err]
  (if (= signature
         (get-in state contracts/history-load-state-request-signature-path))
    (let [current-state (get-in state contracts/history-load-state-path)]
      (assoc-in state
                contracts/history-load-state-path
                (failed-history-load-state current-state completed-at-ms err)))
    state))

(defn apply-selection-prefetch-success
  [state instrument-id* signature completed-at-ms bundle]
  (let [current-prefetch-state (history-prefetch/prefetch-state state)
        current-status (get-in current-prefetch-state
                               [:by-instrument-id instrument-id*])
        selected? (history-prefetch/instrument-selected? state instrument-id*)
        current-signature? (= signature
                              (get-in state
                                      contracts/history-load-state-request-signature-path))
        status (history-prefetch/succeeded-status
                (:started-at-ms current-status)
                completed-at-ms
                (:warnings bundle))]
    (cond-> state
      (and selected? current-signature?)
      (update-in contracts/history-data-path
                 merge-history-bundle
                 bundle
                 completed-at-ms)

      current-signature?
      (assoc-in contracts/history-load-state-path
                (success-history-load-state
                 (get-in state contracts/history-load-state-path)
                 completed-at-ms
                 bundle))

      :always
      (update-in contracts/history-prefetch-path
                 finish-selection-prefetch-state
                 instrument-id*
                 status)

      :always
      (update-in contracts/history-prefetch-path
                 history-prefetch/cleanup-to-instrument-ids
                 (current-universe-ids state)))))

(defn apply-selection-prefetch-error
  [state instrument-id* signature completed-at-ms err]
  (let [current-prefetch-state (history-prefetch/prefetch-state state)
        current-status (get-in current-prefetch-state
                               [:by-instrument-id instrument-id*])
        current-load-state (get-in state contracts/history-load-state-path)
        current-signature? (= signature (:request-signature current-load-state))
        status (history-prefetch/failed-status
                (:started-at-ms current-status)
                completed-at-ms
                {:message (error-message err)})]
    (cond-> state
      current-signature?
      (assoc-in contracts/history-load-state-path
                (failed-history-load-state current-load-state completed-at-ms err))

      :always
      (update-in contracts/history-prefetch-path
                 finish-selection-prefetch-state
                 instrument-id*
                 status)

      :always
      (update-in contracts/history-prefetch-path
                 history-prefetch/cleanup-to-instrument-ids
                 (current-universe-ids state)))))

(defn complete-selection-prefetch
  [{:keys [state instrument-id request-signature completed-at-ms bundle error opts]}]
  (let [state* (if error
                 (apply-selection-prefetch-error state
                                                 instrument-id
                                                 request-signature
                                                 completed-at-ms
                                                 error)
                 (apply-selection-prefetch-success state
                                                   instrument-id
                                                   request-signature
                                                   completed-at-ms
                                                   bundle))
        next-result (begin-selection-prefetch {:state state*
                                               :opts opts
                                               :now-ms completed-at-ms})]
    next-result))

(defn begin-history-load
  [{:keys [state opts now-ms started-at-ms]}]
  (let [request (history-request state
                                 (assoc (request-opts opts)
                                        :now-ms now-ms))
        signature (request-signature request)
        started-at-ms* (or started-at-ms now-ms)]
    (if (seq (:universe request))
      {:state (assoc-in state
                        contracts/history-load-state-path
                        (begin-history-load-state signature started-at-ms*))
       :commands [(request-history-command
                   {:source :full-load
                    :request request
                    :signature signature})]}
      {:state state
       :commands []})))

(defn complete-history-load
  [{:keys [state request-signature completed-at-ms bundle error]}]
  {:state (if error
            (apply-history-error state request-signature completed-at-ms error)
            (apply-history-success state request-signature completed-at-ms bundle))
   :commands []})
