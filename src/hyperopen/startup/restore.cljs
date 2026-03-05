(ns hyperopen.startup.restore
  (:require [clojure.string :as str]
            [hyperopen.i18n.locale :as i18n-locale]
            [hyperopen.account.context :as account-context]
            [hyperopen.platform :as platform]
            [hyperopen.wallet.agent-session :as agent-session]))

(defn restore-agent-storage-mode!
  [store]
  (let [storage-mode (agent-session/load-storage-mode-preference)]
    (swap! store assoc-in [:wallet :agent :storage-mode] storage-mode)))

(defn restore-ui-locale-preference!
  [store]
  (swap! store
         assoc-in
         [:ui :locale]
         (i18n-locale/resolve-preferred-locale)))

(defn- parse-watchlist-storage
  [raw]
  (let [raw* (some-> raw str str/trim)]
    (if-not (seq raw*)
      []
      (let [parsed (try
                     (js->clj (js/JSON.parse raw*))
                     (catch :default _
                       nil))]
        (cond
          (sequential? parsed)
          (account-context/normalize-watchlist parsed)

          (string? raw*)
          (account-context/normalize-watchlist
           (map str/trim (str/split raw* #",")))

          :else
          [])))))

(def ^:private legacy-shadow-watchlist-storage-key
  "ghost-mode-watchlist:v1")

(def ^:private legacy-shadow-last-search-storage-key
  "ghost-mode-last-search:v1")

(defn- read-renamed-storage
  [new-key legacy-key]
  (let [new-value (platform/local-storage-get new-key)]
    (if (some? new-value)
      {:value new-value
       :legacy? false}
      (let [legacy-value (platform/local-storage-get legacy-key)]
        {:value legacy-value
         :legacy? (some? legacy-value)}))))

(defn- migrate-watchlist-storage!
  [watchlist]
  (platform/local-storage-set!
   account-context/shadow-watchlist-storage-key
   (js/JSON.stringify (clj->js watchlist)))
  (platform/local-storage-remove! legacy-shadow-watchlist-storage-key))

(defn- migrate-last-search-storage!
  [search-input]
  (platform/local-storage-set!
   account-context/shadow-last-search-storage-key
   (or search-input ""))
  (platform/local-storage-remove! legacy-shadow-last-search-storage-key))

(defn restore-shadow-mode-preferences!
  [store]
  (let [{watchlist-raw :value
         legacy-watchlist? :legacy?}
        (read-renamed-storage account-context/shadow-watchlist-storage-key
                              legacy-shadow-watchlist-storage-key)
        {search-raw :value
         legacy-search? :legacy?}
        (read-renamed-storage account-context/shadow-last-search-storage-key
                              legacy-shadow-last-search-storage-key)
        watchlist (parse-watchlist-storage watchlist-raw)
        search-input (some-> search-raw
                             str
                             str/trim)]
    (when legacy-watchlist?
      (migrate-watchlist-storage! watchlist))
    (when legacy-search?
      (migrate-last-search-storage! search-input))
    (swap! store
           (fn [state]
             (-> state
                 (assoc-in [:account-context :watchlist] watchlist)
                 (assoc-in [:account-context :watchlist-loaded?] true)
                 (assoc-in [:account-context :shadow-ui :last-search] (or search-input ""))
                 (assoc-in [:account-context :shadow-ui :search] (or search-input ""))
                 (assoc-in [:account-context :shadow-ui :label] "")
                 (assoc-in [:account-context :shadow-ui :editing-watchlist-address] nil)
                 (assoc-in [:account-context :shadow-ui :search-error] nil))))))

(defn restore-active-asset!
  [store {:keys [connected?-fn dispatch! load-active-market-display-fn]}]
  (when (nil? (:active-asset @store))
    (let [stored-asset (platform/local-storage-get "active-asset")
          asset (if (seq stored-asset) stored-asset "BTC")
          cached-market (load-active-market-display-fn asset)]
      (swap! store
             (fn [state]
               (cond-> (assoc state :active-asset asset :selected-asset asset)
                 (map? cached-market) (assoc :active-market cached-market))))
      (when-not (seq stored-asset)
        (platform/local-storage-set! "active-asset" asset))
      (when (connected?-fn)
        (dispatch! store nil [[:actions/subscribe-to-asset asset]])))))
