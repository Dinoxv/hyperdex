(ns hyperopen.account.ghost-mode-actions
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.platform :as platform]))

(def ^:private invalid-address-error
  "Enter a valid 0x-prefixed EVM address.")

(defn- search-value
  [state]
  (or (get-in state [:account-context :ghost-ui :search]) ""))

(defn- normalized-search-address
  [state]
  (account-context/normalize-address (search-value state)))

(defn- resolved-address
  [state address]
  (or (account-context/normalize-address address)
      (normalized-search-address state)))

(defn- watchlist
  [state]
  (account-context/normalize-watchlist
   (get-in state [:account-context :watchlist])))

(defn- persist-watchlist-effects
  [watchlist*]
  [[:effects/local-storage-set-json
    account-context/ghost-watchlist-storage-key
    watchlist*]])

(defn open-ghost-mode-modal
  [state]
  (let [active-address (account-context/ghost-address state)
        search* (or active-address
                    (search-value state)
                    "")]
    [[:effects/save-many [[[:account-context :ghost-ui :modal-open?] true]
                          [[:account-context :ghost-ui :search] search*]
                          [[:account-context :ghost-ui :search-error] nil]]]]))

(defn close-ghost-mode-modal
  [_state]
  [[:effects/save-many [[[:account-context :ghost-ui :modal-open?] false]
                        [[:account-context :ghost-ui :search-error] nil]]]])

(defn set-ghost-mode-search
  [_state value]
  (let [text (if (string? value) value (str (or value "")))]
    [[:effects/save-many [[[:account-context :ghost-ui :search] text]
                          [[:account-context :ghost-ui :search-error] nil]]]]))

(defn start-ghost-mode
  [state & [address]]
  (if-let [address* (resolved-address state address)]
    (let [started-at-ms (platform/now-ms)
          watchlist* (account-context/normalize-watchlist
                      (conj (watchlist state) address*))]
      (into [[:effects/save-many [[[:account-context :ghost-mode :active?] true]
                                  [[:account-context :ghost-mode :address] address*]
                                  [[:account-context :ghost-mode :started-at-ms] started-at-ms]
                                  [[:account-context :ghost-ui :modal-open?] false]
                                  [[:account-context :ghost-ui :search] address*]
                                  [[:account-context :ghost-ui :last-search] address*]
                                  [[:account-context :ghost-ui :search-error] nil]
                                  [[:account-context :watchlist] watchlist*]]]
             [:effects/local-storage-set
              account-context/ghost-last-search-storage-key
              address*]]
            (persist-watchlist-effects watchlist*)))
    [[:effects/save [:account-context :ghost-ui :search-error] invalid-address-error]]))

(defn stop-ghost-mode
  [_state]
  [[:effects/save-many [[[:account-context :ghost-mode :active?] false]
                        [[:account-context :ghost-mode :address] nil]
                        [[:account-context :ghost-mode :started-at-ms] nil]
                        [[:account-context :ghost-ui :modal-open?] false]
                        [[:account-context :ghost-ui :search-error] nil]]]])

(defn add-ghost-mode-watchlist-address
  [state & [address]]
  (if-let [address* (resolved-address state address)]
    (let [watchlist* (account-context/normalize-watchlist
                      (conj (watchlist state) address*))]
      (into [[:effects/save-many [[[:account-context :watchlist] watchlist*]
                                  [[:account-context :ghost-ui :search] address*]
                                  [[:account-context :ghost-ui :last-search] address*]
                                  [[:account-context :ghost-ui :search-error] nil]]]
             [:effects/local-storage-set
              account-context/ghost-last-search-storage-key
              address*]]
            (persist-watchlist-effects watchlist*)))
    [[:effects/save [:account-context :ghost-ui :search-error] invalid-address-error]]))

(defn remove-ghost-mode-watchlist-address
  [state address]
  (if-let [address* (account-context/normalize-address address)]
    (let [watchlist* (->> (watchlist state)
                          (remove #(= % address*))
                          account-context/normalize-watchlist)]
      (into [[:effects/save [:account-context :watchlist] watchlist*]]
            (persist-watchlist-effects watchlist*)))
    []))

(defn spectate-ghost-mode-watchlist-address
  [state address]
  (if (str/blank? (str (or address "")))
    []
    (start-ghost-mode (assoc-in state [:account-context :ghost-ui :search] address) address)))
