(ns hyperopen.account.shadow-mode-actions
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.platform :as platform]))

(def ^:private invalid-address-error
  "Enter a valid 0x-prefixed EVM address.")

(def ^:private anchor-keys
  [:left :right :top :bottom :width :height :viewport-width :viewport-height])

(defn- parse-num
  [value]
  (cond
    (number? value) value
    (string? value) (let [text (str/trim value)
                          parsed (js/parseFloat text)]
                      (when (and (seq text)
                                 (not (js/isNaN parsed)))
                        parsed))
    :else nil))

(defn- normalize-anchor
  [anchor]
  (when (map? anchor)
    (let [normalized (reduce (fn [acc key]
                               (if-let [num (parse-num (get anchor key))]
                                 (assoc acc key num)
                                 acc))
                             {}
                             anchor-keys)]
      (when (seq normalized)
        normalized))))

(defn- search-value
  [state]
  (or (get-in state [:account-context :shadow-ui :search]) ""))

(defn- label-value
  [state]
  (or (get-in state [:account-context :shadow-ui :label]) ""))

(defn- normalized-search-address
  [state]
  (account-context/normalize-address (search-value state)))

(defn- normalized-search-label
  [state]
  (account-context/normalize-watchlist-label (label-value state)))

(defn- resolved-address
  [state address]
  (or (account-context/normalize-address address)
      (normalized-search-address state)))

(defn- editing-watchlist-address
  [state]
  (account-context/normalize-address
   (get-in state [:account-context :shadow-ui :editing-watchlist-address])))

(defn- watchlist
  [state]
  (account-context/normalize-watchlist
   (get-in state [:account-context :watchlist])))

(defn- persist-watchlist-effects
  [watchlist*]
  [[:effects/local-storage-set-json
    account-context/shadow-watchlist-storage-key
    watchlist*]])

(defn open-shadow-mode-modal
  [state & [trigger-bounds]]
  (let [watchlist* (watchlist state)
        active-address (account-context/shadow-address state)
        active-entry (account-context/watchlist-entry-by-address watchlist* active-address)
        search* (or active-address
                    (search-value state)
                    "")
        label* (or (:label active-entry)
                   (label-value state)
                   "")
        anchor* (normalize-anchor trigger-bounds)]
    [[:effects/save-many [[[:account-context :shadow-ui :modal-open?] true]
                          [[:account-context :shadow-ui :anchor] anchor*]
                          [[:account-context :shadow-ui :search] search*]
                          [[:account-context :shadow-ui :label] label*]
                          [[:account-context :shadow-ui :editing-watchlist-address] nil]
                          [[:account-context :shadow-ui :search-error] nil]]]]))

(defn close-shadow-mode-modal
  [_state]
  [[:effects/save-many [[[:account-context :shadow-ui :modal-open?] false]
                        [[:account-context :shadow-ui :anchor] nil]
                        [[:account-context :shadow-ui :label] ""]
                        [[:account-context :shadow-ui :editing-watchlist-address] nil]
                        [[:account-context :shadow-ui :search-error] nil]]]])

(defn set-shadow-mode-search
  [state value]
  (let [text (if (string? value) value (str (or value "")))
        editing-address* (editing-watchlist-address state)
        next-address* (account-context/normalize-address text)
        editing-active? (and (some? editing-address*)
                             (= editing-address* next-address*))]
    [[:effects/save-many [[[:account-context :shadow-ui :search] text]
                          [[:account-context :shadow-ui :label] (if editing-active?
                                                                  (label-value state)
                                                                  "")]
                          [[:account-context :shadow-ui :editing-watchlist-address] (when editing-active?
                                                                                      editing-address*)]
                          [[:account-context :shadow-ui :search-error] nil]]]]))

(defn set-shadow-mode-label
  [_state value]
  (let [text (if (string? value) value (str (or value "")))]
    [[:effects/save [:account-context :shadow-ui :label] text]]))

(defn start-shadow-mode
  [state & [address]]
  (if-let [address* (resolved-address state address)]
    (let [started-at-ms (platform/now-ms)
          watchlist* (account-context/upsert-watchlist-entry
                      (watchlist state)
                      address*
                      (normalized-search-label state)
                      true)]
      (into [[:effects/save-many [[[:account-context :shadow-mode :active?] true]
                                  [[:account-context :shadow-mode :address] address*]
                                  [[:account-context :shadow-mode :started-at-ms] started-at-ms]
                                  [[:account-context :shadow-ui :modal-open?] false]
                                  [[:account-context :shadow-ui :anchor] nil]
                                  [[:account-context :shadow-ui :search] address*]
                                  [[:account-context :shadow-ui :last-search] address*]
                                  [[:account-context :shadow-ui :label] ""]
                                  [[:account-context :shadow-ui :editing-watchlist-address] nil]
                                  [[:account-context :shadow-ui :search-error] nil]
                                  [[:account-context :watchlist] watchlist*]]]
             [:effects/local-storage-set
              account-context/shadow-last-search-storage-key
              address*]]
            (persist-watchlist-effects watchlist*)))
    [[:effects/save [:account-context :shadow-ui :search-error] invalid-address-error]]))

(defn stop-shadow-mode
  [_state]
  [[:effects/save-many [[[:account-context :shadow-mode :active?] false]
                        [[:account-context :shadow-mode :address] nil]
                        [[:account-context :shadow-mode :started-at-ms] nil]
                        [[:account-context :shadow-ui :modal-open?] false]
                        [[:account-context :shadow-ui :anchor] nil]
                        [[:account-context :shadow-ui :label] ""]
                        [[:account-context :shadow-ui :editing-watchlist-address] nil]
                        [[:account-context :shadow-ui :search-error] nil]]]])

(defn add-shadow-mode-watchlist-address
  [state & [address]]
  (if-let [address* (resolved-address state address)]
    (let [editing-address* (editing-watchlist-address state)
          preserve-existing-label? (not= editing-address* address*)
          watchlist* (account-context/upsert-watchlist-entry
                      (watchlist state)
                      address*
                      (normalized-search-label state)
                      preserve-existing-label?)]
      (into [[:effects/save-many [[[:account-context :watchlist] watchlist*]
                                  [[:account-context :shadow-ui :search] address*]
                                  [[:account-context :shadow-ui :last-search] address*]
                                  [[:account-context :shadow-ui :label] ""]
                                  [[:account-context :shadow-ui :editing-watchlist-address] nil]
                                  [[:account-context :shadow-ui :search-error] nil]]]
             [:effects/local-storage-set
              account-context/shadow-last-search-storage-key
              address*]]
            (persist-watchlist-effects watchlist*)))
    [[:effects/save [:account-context :shadow-ui :search-error] invalid-address-error]]))

(defn remove-shadow-mode-watchlist-address
  [state address]
  (if-let [address* (account-context/normalize-address address)]
    (let [watchlist* (account-context/remove-watchlist-entry
                      (watchlist state)
                      address*)
          editing-address* (editing-watchlist-address state)
          removed-editing? (= address* editing-address*)
          save-effects (cond-> [[[:account-context :watchlist] watchlist*]]
                         removed-editing?
                         (into [[[:account-context :shadow-ui :label] ""]
                                [[:account-context :shadow-ui :editing-watchlist-address] nil]]))]
      (into [[:effects/save-many save-effects]]
            (persist-watchlist-effects watchlist*)))
    []))

(defn edit-shadow-mode-watchlist-address
  [state address]
  (if-let [{:keys [address label]} (account-context/watchlist-entry-by-address
                                    (watchlist state)
                                    address)]
    [[:effects/save-many [[[:account-context :shadow-ui :search] address]
                          [[:account-context :shadow-ui :label] (or label "")]
                          [[:account-context :shadow-ui :editing-watchlist-address] address]
                          [[:account-context :shadow-ui :search-error] nil]]]]
    []))

(defn clear-shadow-mode-watchlist-edit
  [_state]
  [[:effects/save-many [[[:account-context :shadow-ui :label] ""]
                        [[:account-context :shadow-ui :editing-watchlist-address] nil]
                        [[:account-context :shadow-ui :search-error] nil]]]])

(defn copy-shadow-mode-watchlist-address
  [_state address]
  (if-let [address* (account-context/normalize-address address)]
    [[:effects/copy-wallet-address address*]]
    []))

(defn spectate-shadow-mode-watchlist-address
  [state address]
  (if (str/blank? (str (or address "")))
    []
    (let [entry (account-context/watchlist-entry-by-address
                 (watchlist state)
                 address)]
      (start-shadow-mode (-> state
                            (assoc-in [:account-context :shadow-ui :search] address)
                            (assoc-in [:account-context :shadow-ui :label] (or (:label entry) "")))
                        address))))
