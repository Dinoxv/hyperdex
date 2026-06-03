(ns hyperopen.subaccounts.effects
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.api.trading :as trading-api]
            [hyperopen.platform :as platform]
            [hyperopen.subaccounts.actions :as actions]))

(defn- subaccount-row-address
  [row]
  (account-context/normalize-address
   (or (:sub-account-user row)
       (:subAccountUser row)
       (get row "subAccountUser")
       (get row "sub-account-user"))))

(defn- subaccount-row-master
  [row]
  (account-context/normalize-address
   (or (:master row)
       (get row "master"))))

(defn- normalize-subaccount-row
  [row]
  (when-let [address (subaccount-row-address row)]
    (cond-> (assoc row :sub-account-user address)
      (subaccount-row-master row) (assoc :master (subaccount-row-master row)))))

(defn- owned-subaccount-address?
  [owner-address rows address]
  (boolean
   (some (fn [row]
           (and (= address (subaccount-row-address row))
                (= owner-address (subaccount-row-master row))))
         rows)))

(defn- request-opts
  [owner-address force-refresh? now-ms-fn]
  (if force-refresh?
    (let [token (now-ms-fn)]
      {:priority :high
       :cache-ttl-ms 1
       :dedupe-key [:sub-accounts owner-address token]
       :cache-key [:sub-accounts owner-address token]})
    {:priority :high}))

(defn- error-message
  [err]
  (or (some-> err .-message str/trim not-empty)
      (some-> err str str/trim not-empty)
      "Failed to load subaccounts."))

(defn- reset-subaccounts!
  [store]
  (swap! store assoc-in [:account-context :subaccounts]
         {:status :idle
          :loaded-for-owner nil
          :rows []
          :error nil
          :selected-address nil
          :selection-loaded? false
          :create-name ""
          :create-popover-open? false
          :rename-name ""
          :transfer-amount ""
          :transfer-direction :deposit
          :creating? false
          :renaming-address nil
          :transferring-address nil}))

(defn- stored-selected-address
  [owner-address local-storage-get]
  (account-context/normalize-address
   (local-storage-get (actions/selected-subaccount-storage-key owner-address))))

(defn- next-selected-address
  [state owner-address rows local-storage-get]
  (let [current-selected (account-context/normalize-address
                          (get-in state [:account-context :subaccounts :selected-address]))
        stored-selected (stored-selected-address owner-address local-storage-get)]
    (cond
      (owned-subaccount-address? owner-address rows current-selected)
      current-selected

      (owned-subaccount-address? owner-address rows stored-selected)
      stored-selected

      :else
      nil)))

(defn load-subaccounts!
  [{:keys [store
           request-sub-accounts!
           local-storage-get
           now-ms-fn
           force-refresh?]
    :or {local-storage-get platform/local-storage-get
         now-ms-fn platform/now-ms}}]
  (let [owner-address (account-context/owner-address @store)]
    (if-not (seq owner-address)
      (do
        (reset-subaccounts! store)
        (js/Promise.resolve nil))
      (-> (request-sub-accounts! owner-address
                                  (request-opts owner-address force-refresh? now-ms-fn))
          (.then (fn [rows]
                   (let [rows* (->> (or rows [])
                                    (keep normalize-subaccount-row)
                                    vec)
                         selected (next-selected-address @store
                                                         owner-address
                                                         rows*
                                                         local-storage-get)]
                     (swap! store
                            (fn [state]
                              (-> state
                                  (assoc-in [:account-context :subaccounts :status] :loaded)
                                  (assoc-in [:account-context :subaccounts :loaded-for-owner] owner-address)
                                  (assoc-in [:account-context :subaccounts :rows] rows*)
                                  (assoc-in [:account-context :subaccounts :error] nil)
                                  (assoc-in [:account-context :subaccounts :selected-address] selected)
                                  (assoc-in [:account-context :subaccounts :selection-loaded?] true))))
                     nil)))
          (.catch (fn [err]
                    (swap! store
                           (fn [state]
                             (-> state
                                 (assoc-in [:account-context :subaccounts :status] :error)
                                 (assoc-in [:account-context :subaccounts :error] (error-message err))
                                 (assoc-in [:account-context :subaccounts :selection-loaded?] true))))
                    nil))))))

(defn api-load-subaccounts!
  [deps]
  (load-subaccounts! (merge {:force-refresh? false} deps)))

(def ^:private default-load-subaccounts! load-subaccounts!)

(defn- response-ok?
  [resp]
  (= "ok" (:status resp)))

(defn- response-error-message
  [resp fallback-message]
  (or (some-> (:error resp) str str/trim not-empty)
      (some-> (:response resp) str str/trim not-empty)
      fallback-message))

(defn- management-runtime-error-message
  [runtime-error-message err]
  (or (some-> (runtime-error-message err) str str/trim not-empty)
      (error-message err)))

(defn- refresh-subaccounts!
  [{load-subaccounts-fn :load-subaccounts!
    :as deps}]
  ((or load-subaccounts-fn default-load-subaccounts!)
   (assoc deps :force-refresh? true)))

(defn- owner-address
  [store]
  (account-context/owner-address @store))

(defn- set-management-error!
  [store path-values message]
  (swap! store
         (fn [state]
           (reduce (fn [state* [path value]]
                     (assoc-in state* path value))
                   (assoc-in state [:account-context :subaccounts :error] message)
                   path-values))))

(defn- clear-create-success!
  [store]
  (swap! store
         (fn [state]
           (-> state
               (assoc-in [:account-context :subaccounts :creating?] false)
               (assoc-in [:account-context :subaccounts :create-popover-open?] false)
               (assoc-in [:account-context :subaccounts :create-name] "")
               (assoc-in [:account-context :subaccounts :error] nil)))))

(defn create-subaccount!
  [{:keys [store request create-sub-account! runtime-error-message]
    :as deps
    :or {create-sub-account! trading-api/create-sub-account!
         runtime-error-message error-message}}]
  (let [owner (owner-address store)
        name* (or (:name request)
                  (get-in @store [:account-context :subaccounts :create-name]))]
    (-> (create-sub-account! store owner name*)
        (.then (fn [resp]
                 (if (response-ok? resp)
                   (do
                     (clear-create-success! store)
                     (refresh-subaccounts! deps))
                   (set-management-error!
                    store
                    [[[:account-context :subaccounts :creating?] false]]
                    (response-error-message resp "Subaccount creation failed.")))))
        (.catch (fn [err]
                  (set-management-error!
                   store
                   [[[:account-context :subaccounts :creating?] false]]
                   (management-runtime-error-message runtime-error-message err)))))))

(defn- clear-rename-success!
  [store]
  (swap! store
         (fn [state]
           (-> state
               (assoc-in [:account-context :subaccounts :renaming-address] nil)
               (assoc-in [:account-context :subaccounts :rename-name] "")
               (assoc-in [:account-context :subaccounts :error] nil)))))

(defn rename-subaccount!
  [{:keys [store request modify-sub-account! runtime-error-message]
    :as deps
    :or {modify-sub-account! trading-api/modify-sub-account!
         runtime-error-message error-message}}]
  (let [owner (owner-address store)
        address (account-context/normalize-address (:sub-account-user request))
        name* (:name request)]
    (-> (modify-sub-account! store owner address name*)
        (.then (fn [resp]
                 (if (response-ok? resp)
                   (do
                     (clear-rename-success! store)
                     (refresh-subaccounts! deps))
                   (set-management-error!
                    store
                    [[[:account-context :subaccounts :renaming-address] nil]]
                    (response-error-message resp "Subaccount rename failed.")))))
        (.catch (fn [err]
                  (set-management-error!
                   store
                   [[[:account-context :subaccounts :renaming-address] nil]]
                   (management-runtime-error-message runtime-error-message err)))))))

(defn- clear-transfer-success!
  [store]
  (swap! store
         (fn [state]
           (-> state
               (assoc-in [:account-context :subaccounts :transferring-address] nil)
               (assoc-in [:account-context :subaccounts :transfer-amount] "")
               (assoc-in [:account-context :subaccounts :transfer-direction] :deposit)
               (assoc-in [:account-context :subaccounts :error] nil)))))

(defn- dispatch-active-user-refresh!
  [store dispatch!]
  (when-let [address (account-context/effective-account-address @store)]
    (dispatch! store nil [[:effects/api-load-user-data address]])))

(defn transfer-subaccount!
  [{:keys [store request transfer-sub-account! dispatch! runtime-error-message]
    :as deps
    :or {transfer-sub-account! trading-api/transfer-sub-account!
         dispatch! (fn [_store _ctx _effects] nil)
         runtime-error-message error-message}}]
  (let [owner (owner-address store)
        address (account-context/normalize-address (:sub-account-user request))
        is-deposit? (boolean (:is-deposit request))
        usd (:usd request)]
    (-> (transfer-sub-account! store owner address is-deposit? usd)
        (.then (fn [resp]
                 (if (response-ok? resp)
                   (do
                     (clear-transfer-success! store)
                     (dispatch-active-user-refresh! store dispatch!)
                     (refresh-subaccounts! deps))
                   (set-management-error!
                    store
                    [[[:account-context :subaccounts :transferring-address] nil]]
                    (response-error-message resp "Subaccount transfer failed.")))))
        (.catch (fn [err]
                  (set-management-error!
                   store
                   [[[:account-context :subaccounts :transferring-address] nil]]
                   (management-runtime-error-message runtime-error-message err)))))))
