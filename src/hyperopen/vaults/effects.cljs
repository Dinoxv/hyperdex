(ns hyperopen.vaults.effects
  (:require [hyperopen.api.promise-effects :as promise-effects]))

(defn api-fetch-vault-index!
  [{:keys [store
           request-vault-index!
           begin-vault-index-load
           apply-vault-index-success
           apply-vault-index-error
           opts]}]
  (swap! store begin-vault-index-load)
  (-> (request-vault-index! (or opts {}))
      (.then (promise-effects/apply-success-and-return
              store
              apply-vault-index-success))
      (.catch (promise-effects/apply-error-and-reject
               store
               apply-vault-index-error))))

(defn api-fetch-vault-summaries!
  [{:keys [store
           request-vault-summaries!
           begin-vault-summaries-load
           apply-vault-summaries-success
           apply-vault-summaries-error
           opts]}]
  (swap! store begin-vault-summaries-load)
  (-> (request-vault-summaries! (or opts {}))
      (.then (promise-effects/apply-success-and-return
              store
              apply-vault-summaries-success))
      (.catch (promise-effects/apply-error-and-reject
               store
               apply-vault-summaries-error))))

(defn api-fetch-user-vault-equities!
  [{:keys [store
           address
           request-user-vault-equities!
           begin-user-vault-equities-load
           apply-user-vault-equities-success
           apply-user-vault-equities-error
           opts]}]
  (swap! store begin-user-vault-equities-load)
  (-> (request-user-vault-equities! address (or opts {}))
      (.then (promise-effects/apply-success-and-return
              store
              apply-user-vault-equities-success))
      (.catch (promise-effects/apply-error-and-reject
               store
               apply-user-vault-equities-error))))

(defn api-fetch-vault-details!
  [{:keys [store
           vault-address
           user-address
           request-vault-details!
           begin-vault-details-load
           apply-vault-details-success
           apply-vault-details-error
           opts]}]
  (swap! store begin-vault-details-load vault-address)
  (-> (request-vault-details! vault-address
                              (cond-> (or opts {})
                                user-address (assoc :user user-address)))
      (.then (promise-effects/apply-success-and-return
              store
              apply-vault-details-success
              vault-address))
      (.catch (promise-effects/apply-error-and-reject
               store
               apply-vault-details-error
               vault-address))))

(defn api-fetch-vault-webdata2!
  [{:keys [store
           vault-address
           request-vault-webdata2!
           begin-vault-webdata2-load
           apply-vault-webdata2-success
           apply-vault-webdata2-error
           opts]}]
  (swap! store begin-vault-webdata2-load vault-address)
  (-> (request-vault-webdata2! vault-address (or opts {}))
      (.then (promise-effects/apply-success-and-return
              store
              apply-vault-webdata2-success
              vault-address))
      (.catch (promise-effects/apply-error-and-reject
               store
               apply-vault-webdata2-error
               vault-address))))
