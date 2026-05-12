(ns hyperopen.runtime.effect-adapters.portfolio-optimizer.history
  (:require [hyperopen.portfolio.optimizer.application.history-workflow :as workflow]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(defn- request-candle-snapshot!
  [{:keys [request-candle-snapshot!]} coin opts]
  (request-candle-snapshot! coin opts))

(defn- request-history-bundle!
  [env on-progress request]
  ((:request-history-bundle! env)
   {:request-candle-snapshot! (partial request-candle-snapshot! env)
    :request-market-funding-history! (:request-market-funding-history! env)
    :request-vault-details! (:request-vault-details! env)
    :on-progress on-progress}
   request))

(defn- now-for-request
  [env state opts]
  (or (:now-ms opts)
      (get-in state contracts/runtime-as-of-ms-path)
      ((:now-ms env))))

(defn- apply-result!
  [store result]
  (reset! store (:state result))
  result)

(declare interpret-selection-prefetch-result!)

(defn- interpret-selection-prefetch-command!
  [env store opts command]
  (let [now-ms-fn (:now-ms env)
        on-progress (:on-progress opts)]
    (-> (request-history-bundle! env on-progress (:request command))
        (.then (fn [bundle]
                 (let [completed-at-ms (now-ms-fn)]
                   (interpret-selection-prefetch-result!
                    env
                    store
                    opts
                    (workflow/complete-selection-prefetch
                     {:state @store
                      :instrument-id (:instrument-id command)
                      :request-signature (:request-signature command)
                      :completed-at-ms completed-at-ms
                      :bundle bundle
                      :opts opts})))))
        (.catch (fn [err]
                  (let [completed-at-ms (now-ms-fn)]
                    (interpret-selection-prefetch-result!
                     env
                     store
                     opts
                     (workflow/complete-selection-prefetch
                      {:state @store
                       :instrument-id (:instrument-id command)
                       :request-signature (:request-signature command)
                       :completed-at-ms completed-at-ms
                       :error err
                       :opts opts}))))))))

(defn- interpret-selection-prefetch-result!
  [env store opts result]
  (let [result* (apply-result! store result)]
    (if-let [command (first (:commands result*))]
      (interpret-selection-prefetch-command! env store opts command)
      (js/Promise.resolve nil))))

(defn- drain-selection-prefetch!
  [env store opts]
  (interpret-selection-prefetch-result!
   env
   store
   opts
   (workflow/begin-selection-prefetch
    {:state @store
     :opts opts
     :now-ms (now-for-request env @store opts)
     :started-at-ms ((:now-ms env))})))

(defn- interpret-full-load-result!
  [env store opts result]
  (let [result* (apply-result! store result)]
    (if-let [command (first (:commands result*))]
      (let [on-progress (:on-progress opts)
            now-ms-fn (:now-ms env)]
        (-> (request-history-bundle! env on-progress (:request command))
            (.then (fn [bundle]
                     (let [completed-at-ms (now-ms-fn)]
                       (apply-result!
                        store
                        (workflow/complete-history-load
                         {:state @store
                          :request-signature (:request-signature command)
                          :completed-at-ms completed-at-ms
                          :bundle bundle}))
                       bundle)))
            (.catch (fn [err]
                      (let [completed-at-ms (now-ms-fn)]
                        (apply-result!
                         store
                         (workflow/complete-history-load
                          {:state @store
                           :request-signature (:request-signature command)
                           :completed-at-ms completed-at-ms
                           :error err})))
                      nil))))
      (js/Promise.resolve nil))))

(defn load-portfolio-optimizer-history-effect
  ([_env store]
   (load-portfolio-optimizer-history-effect _env nil store nil))
  ([env _ store opts]
   (let [opts* (or opts {})
         now-ms (now-for-request env @store opts*)
         started-at-ms ((:now-ms env))]
     (if (workflow/selection-prefetch? opts*)
       (drain-selection-prefetch! env store (assoc opts*
                                                   :now-ms now-ms
                                                   :started-at-ms started-at-ms))
       (interpret-full-load-result!
        env
        store
        (assoc opts* :now-ms now-ms)
        (workflow/begin-history-load
         {:state @store
          :opts (assoc opts* :now-ms now-ms)
          :now-ms now-ms
          :started-at-ms started-at-ms}))))))
