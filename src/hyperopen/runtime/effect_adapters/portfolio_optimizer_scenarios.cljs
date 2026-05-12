(ns hyperopen.runtime.effect-adapters.portfolio-optimizer-scenarios
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.portfolio.optimizer.application.scenario-workflow :as scenario-workflow]
            [hyperopen.portfolio.optimizer.contracts :as contracts]
            [hyperopen.portfolio.routes :as portfolio-routes]))

(defn- env-fn
  [env key]
  (get env key))

(defn- load-tracking-record
  [load-tracking! scenario-id]
  (if load-tracking!
    (-> (load-tracking! scenario-id)
        (.catch (fn [_err] nil)))
    (js/Promise.resolve nil)))

(defn- current-scenario-id
  [env state opts now-ms]
  (let [route-kind (:kind (portfolio-routes/parse-portfolio-route
                           (get-in state [:router :path])))
        new-route? (= :optimize-new route-kind)]
    (or (:scenario-id opts)
        (when-not new-route?
          (get-in state contracts/active-scenario-loaded-id-path))
        (when-not new-route?
          (get-in state contracts/draft-id-path))
        ((env-fn env :next-scenario-id) now-ms))))

(defn- apply-result!
  [store result]
  (reset! store (:state result))
  result)

(defn- persist-scenario-plan!
  [{:keys [address complete-fn result-value save-scenario! save-scenario-index! store]} plan]
  (let [scenario-record (:scenario-record plan)
        scenario-index (:scenario-index plan)
        save-command (first (:commands plan))
        save-index-command (second (:commands plan))]
    (-> (save-scenario! (:scenario-id save-command) scenario-record)
        (.then (fn [_]
                 (save-scenario-index! address scenario-index)))
        (.then (fn [_]
                 (apply-result! store (complete-fn scenario-index scenario-record))
                 (result-value scenario-record))))))

(defn load-portfolio-optimizer-scenario-index-effect
  [env store _opts]
  (let [state @store
        address (account-context/effective-account-address state)
        now-ms-fn (env-fn env :now-ms)
        load-scenario-index! (env-fn env :load-scenario-index!)
        started-at-ms (now-ms-fn)
        begin-result (scenario-workflow/begin-index-load
                      {:state state
                       :address address
                       :started-at-ms started-at-ms})]
    (apply-result! store begin-result)
    (if-let [command (first (:commands begin-result))]
      (-> (load-scenario-index! (:address command))
          (.then (fn [loaded-index]
                   (let [completed-at-ms (now-ms-fn)
                         complete-result (scenario-workflow/complete-index-load
                                          {:state @store
                                           :loaded-index loaded-index
                                           :started-at-ms started-at-ms
                                           :completed-at-ms completed-at-ms})]
                     (apply-result! store complete-result)
                     (get-in @store contracts/scenario-index-path))))
          (.catch (fn [err]
                    (let [completed-at-ms (now-ms-fn)]
                      (apply-result!
                       store
                       (scenario-workflow/complete-index-load
                        {:state @store
                         :started-at-ms started-at-ms
                         :completed-at-ms completed-at-ms
                         :error err})))
                    nil)))
      (js/Promise.resolve nil))))

(defn load-portfolio-optimizer-scenario-effect
  [env store scenario-id _opts]
  (let [now-ms-fn (env-fn env :now-ms)
        load-scenario! (env-fn env :load-scenario!)
        load-tracking! (env-fn env :load-tracking!)
        started-at-ms (now-ms-fn)
        begin-result (scenario-workflow/begin-load
                      {:state @store
                       :scenario-id scenario-id
                       :started-at-ms started-at-ms})]
    (apply-result! store begin-result)
    (if-let [command (first (:commands begin-result))]
      (-> (load-scenario! (:scenario-id command))
          (.then (fn [scenario-record]
                   (let [completed-at-ms (now-ms-fn)
                         after-record (scenario-workflow/continue-load-after-record
                                       {:state @store
                                        :scenario-id scenario-id
                                        :scenario-record scenario-record
                                        :started-at-ms started-at-ms
                                        :completed-at-ms completed-at-ms})]
                     (apply-result! store after-record)
                     (if-let [_tracking-command (first (:commands after-record))]
                       (-> (load-tracking-record load-tracking! scenario-id)
                           (.then (fn [tracking-record]
                                    (let [completed-at-ms* (now-ms-fn)]
                                      (apply-result!
                                       store
                                       (scenario-workflow/complete-load-after-tracking
                                        {:state @store
                                         :scenario-id scenario-id
                                         :scenario-record scenario-record
                                         :tracking-record tracking-record
                                         :started-at-ms started-at-ms
                                         :completed-at-ms completed-at-ms*}))
                                      scenario-record))))
                       (js/Promise.resolve nil)))))
          (.catch (fn [err]
                    (let [completed-at-ms (now-ms-fn)]
                      (apply-result!
                       store
                       (scenario-workflow/fail-load
                        {:state @store
                         :scenario-id scenario-id
                         :started-at-ms started-at-ms
                         :completed-at-ms completed-at-ms
                         :error err})))
                    nil)))
      (js/Promise.resolve nil))))

(defn archive-portfolio-optimizer-scenario-effect
  [env store scenario-id _opts]
  (let [state @store
        address (account-context/effective-account-address state)
        now-ms-fn (env-fn env :now-ms)
        load-scenario! (env-fn env :load-scenario!)
        load-scenario-index! (env-fn env :load-scenario-index!)
        save-scenario! (env-fn env :save-scenario!)
        save-scenario-index! (env-fn env :save-scenario-index!)
        started-at-ms (now-ms-fn)
        begin-result (scenario-workflow/begin-archive
                      {:state state
                       :address address
                       :scenario-id scenario-id
                       :started-at-ms started-at-ms})]
    (apply-result! store begin-result)
    (if-let [command (first (:commands begin-result))]
      (-> (load-scenario! (:scenario-id command))
          (.then (fn [scenario-record]
                   (let [completed-at-ms (now-ms-fn)
                         after-record (scenario-workflow/continue-archive-after-record
                                       {:state @store
                                        :address address
                                        :scenario-id scenario-id
                                        :scenario-record scenario-record
                                        :started-at-ms started-at-ms
                                        :completed-at-ms completed-at-ms})]
                     (apply-result! store after-record)
                     (if-let [_load-index-command (first (:commands after-record))]
                       (-> (load-scenario-index! address)
                           (.then (fn [loaded-index]
                                    (let [plan (scenario-workflow/continue-archive-after-index
                                                {:state @store
                                                 :address address
                                                 :scenario-id scenario-id
                                                 :scenario-record scenario-record
                                                 :started-at-ms started-at-ms
                                                 :loaded-index loaded-index})]
                                      (persist-scenario-plan!
                                       {:address address
                                        :store store
                                        :save-scenario! save-scenario!
                                        :save-scenario-index! save-scenario-index!
                                        :result-value identity
                                        :complete-fn
                                        (fn [scenario-index archived-record]
                                          (scenario-workflow/complete-archive
                                           {:state @store
                                            :scenario-index scenario-index
                                            :scenario-record archived-record
                                            :started-at-ms started-at-ms
                                            :completed-at-ms (now-ms-fn)}))}
                                       plan)))))
                       (js/Promise.resolve nil)))))
          (.catch (fn [err]
                    (let [completed-at-ms (now-ms-fn)]
                      (apply-result!
                       store
                       (scenario-workflow/fail-archive
                        {:state @store
                         :scenario-id scenario-id
                         :started-at-ms started-at-ms
                         :completed-at-ms completed-at-ms
                         :error err})))
                    nil)))
      (js/Promise.resolve nil))))

(defn duplicate-portfolio-optimizer-scenario-effect
  [env store scenario-id _opts]
  (let [state @store
        address (account-context/effective-account-address state)
        now-ms-fn (env-fn env :now-ms)
        load-scenario! (env-fn env :load-scenario!)
        load-scenario-index! (env-fn env :load-scenario-index!)
        save-scenario! (env-fn env :save-scenario!)
        save-scenario-index! (env-fn env :save-scenario-index!)
        started-at-ms (now-ms-fn)
        duplicated-scenario-id ((env-fn env :next-scenario-id) started-at-ms)
        begin-result (scenario-workflow/begin-duplicate
                      {:state state
                       :address address
                       :scenario-id scenario-id
                       :duplicated-scenario-id duplicated-scenario-id
                       :started-at-ms started-at-ms})]
    (apply-result! store begin-result)
    (if-let [command (first (:commands begin-result))]
      (-> (load-scenario! (:scenario-id command))
          (.then (fn [scenario-record]
                   (let [completed-at-ms (now-ms-fn)
                         after-record (scenario-workflow/continue-duplicate-after-record
                                       {:state @store
                                        :address address
                                        :scenario-id scenario-id
                                        :duplicated-scenario-id duplicated-scenario-id
                                        :scenario-record scenario-record
                                        :started-at-ms started-at-ms
                                        :completed-at-ms completed-at-ms})]
                     (apply-result! store after-record)
                     (if-let [_load-index-command (first (:commands after-record))]
                       (-> (load-scenario-index! address)
                           (.then (fn [loaded-index]
                                    (let [plan (scenario-workflow/continue-duplicate-after-index
                                                {:state @store
                                                 :address address
                                                 :scenario-id scenario-id
                                                 :duplicated-scenario-id duplicated-scenario-id
                                                 :scenario-record scenario-record
                                                 :started-at-ms started-at-ms
                                                 :loaded-index loaded-index})]
                                      (persist-scenario-plan!
                                       {:address address
                                        :store store
                                        :save-scenario! save-scenario!
                                        :save-scenario-index! save-scenario-index!
                                        :result-value identity
                                        :complete-fn
                                        (fn [scenario-index duplicated-record]
                                          (scenario-workflow/complete-duplicate
                                           {:state @store
                                            :scenario-index scenario-index
                                            :scenario-record duplicated-record
                                            :source-scenario-id scenario-id
                                            :started-at-ms started-at-ms
                                            :completed-at-ms (now-ms-fn)}))}
                                       plan)))))
                       (js/Promise.resolve nil)))))
          (.catch (fn [err]
                    (let [completed-at-ms (now-ms-fn)]
                      (apply-result!
                       store
                       (scenario-workflow/fail-duplicate
                        {:state @store
                         :scenario-id scenario-id
                         :started-at-ms started-at-ms
                         :completed-at-ms completed-at-ms
                         :error err})))
                    nil)))
      (js/Promise.resolve nil))))

(defn enable-portfolio-optimizer-manual-tracking-effect
  [env store]
  (let [state @store
        address (account-context/effective-account-address state)
        scenario-id (or (get-in state contracts/active-scenario-loaded-id-path)
                        (get-in state contracts/draft-id-path))
        now-ms-fn (env-fn env :now-ms)
        load-scenario! (env-fn env :load-scenario!)
        load-scenario-index! (env-fn env :load-scenario-index!)
        save-scenario! (env-fn env :save-scenario!)
        save-scenario-index! (env-fn env :save-scenario-index!)
        started-at-ms (now-ms-fn)
        begin-result (scenario-workflow/begin-manual-tracking
                      {:state state
                       :address address
                       :scenario-id scenario-id
                       :started-at-ms started-at-ms})]
    (apply-result! store begin-result)
    (if-let [command (first (:commands begin-result))]
      (-> (load-scenario! (:scenario-id command))
          (.then (fn [scenario-record]
                   (let [after-record (scenario-workflow/continue-manual-tracking-after-record
                                       {:state @store
                                        :address address
                                        :scenario-id scenario-id
                                        :scenario-record scenario-record
                                        :updated-at-ms (now-ms-fn)})]
                     (apply-result! store after-record)
                     (if-let [_load-index-command (first (:commands after-record))]
                       (-> (load-scenario-index! address)
                           (.then (fn [loaded-index]
                                    (let [plan (scenario-workflow/continue-manual-tracking-after-index
                                                {:state @store
                                                 :address address
                                                 :scenario-id scenario-id
                                                 :scenario-record scenario-record
                                                 :loaded-index loaded-index
                                                 :updated-at-ms (now-ms-fn)})]
                                      (persist-scenario-plan!
                                       {:address address
                                        :store store
                                        :save-scenario! save-scenario!
                                        :save-scenario-index! save-scenario-index!
                                        :result-value identity
                                        :complete-fn
                                        (fn [scenario-index updated-record]
                                          (scenario-workflow/complete-manual-tracking
                                           {:state @store
                                            :scenario-index scenario-index
                                            :scenario-record updated-record}))}
                                       plan)))))
                       (js/Promise.resolve scenario-record)))))
          (.catch (fn [err]
                    (apply-result!
                     store
                     (scenario-workflow/fail-manual-tracking
                      {:state @store
                       :error err}))
                    nil)))
      (js/Promise.resolve nil))))

(defn save-portfolio-optimizer-scenario-effect
  [env store opts]
  (let [opts* (or opts {})
        state @store
        address (account-context/effective-account-address state)
        now-ms-fn (env-fn env :now-ms)
        load-scenario-index! (env-fn env :load-scenario-index!)
        save-scenario! (env-fn env :save-scenario!)
        save-scenario-index! (env-fn env :save-scenario-index!)
        started-at-ms (now-ms-fn)
        scenario-id (current-scenario-id env state opts* started-at-ms)
        begin-result (scenario-workflow/begin-save
                      {:state state
                       :address address
                       :scenario-id scenario-id
                       :started-at-ms started-at-ms})]
    (apply-result! store begin-result)
    (if-let [command (first (:commands begin-result))]
      (-> (load-scenario-index! (:address command))
          (.then (fn [loaded-index]
                   (let [save-plan (scenario-workflow/continue-save-after-index
                                    {:state @store
                                     :address address
                                     :scenario-id scenario-id
                                     :started-at-ms started-at-ms
                                     :loaded-index loaded-index})]
                     (persist-scenario-plan!
                      {:address address
                       :store store
                       :save-scenario! save-scenario!
                       :save-scenario-index! save-scenario-index!
                       :result-value identity
                       :complete-fn
                       (fn [scenario-index scenario-record]
                         (scenario-workflow/complete-save
                          {:state @store
                           :scenario-index scenario-index
                           :scenario-record scenario-record
                           :started-at-ms started-at-ms
                           :completed-at-ms (now-ms-fn)}))}
                      save-plan))))
          (.catch (fn [err]
                    (let [completed-at-ms (now-ms-fn)]
                      (apply-result!
                       store
                       (scenario-workflow/fail-save
                        {:state @store
                         :scenario-id scenario-id
                         :started-at-ms started-at-ms
                         :completed-at-ms completed-at-ms
                         :error err})))
                    nil)))
      (js/Promise.resolve nil))))
