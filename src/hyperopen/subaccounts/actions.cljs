(ns hyperopen.subaccounts.actions
  (:require [clojure.string :as str]
            [hyperopen.account.context :as account-context]
            [hyperopen.subaccounts.management :as management]))

(def canonical-route
  "/subAccounts")

(def selected-subaccount-storage-prefix
  "hyperopen:subaccounts:selected:v1")

(def ^:private subaccounts-route-kinds
  #{"/subaccounts"})

(def missing-owner-message
  "Connect your wallet before selecting a subaccount.")

(def invalid-subaccount-selection-message
  "Select a subaccount owned by the connected master wallet.")

(defn selected-subaccount-storage-key
  [owner-address]
  (str selected-subaccount-storage-prefix ":"
       (or (account-context/normalize-address owner-address) "")))

(defn- split-path-from-query-fragment
  [path]
  (let [path* (if (string? path) path (str (or path "")))]
    (or (first (str/split path* #"[?#]" 2))
        "")))

(defn- trim-trailing-slashes
  [path]
  (loop [path* path]
    (if (and (> (count path*) 1)
             (str/ends-with? path* "/"))
      (recur (subs path* 0 (dec (count path*))))
      path*)))

(defn normalize-route-path
  [path]
  (-> path
      split-path-from-query-fragment
      str/trim
      trim-trailing-slashes))

(defn parse-subaccounts-route
  [path]
  (let [path* (normalize-route-path path)
        path-lower (str/lower-case path*)]
    (if (contains? subaccounts-route-kinds path-lower)
      {:kind :page
       :path canonical-route}
      {:kind :other
       :path path*})))

(defn subaccounts-route?
  [path]
  (= :page (:kind (parse-subaccounts-route path))))

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

(defn- owned-subaccount-row
  [state owner-address subaccount-address]
  (some (fn [row]
          (when (and (= subaccount-address (subaccount-row-address row))
                     (= owner-address (subaccount-row-master row)))
            row))
        (get-in state [:account-context :subaccounts :rows])))

(def set-subaccount-form-field management/set-subaccount-form-field)
(def open-create-popover management/open-create-popover)
(def close-create-popover management/close-create-popover)
(def copy-subaccount-address management/copy-subaccount-address)
(def parse-usdc-amount->micros management/parse-usdc-amount->micros)
(def submit-create-subaccount management/submit-create-subaccount)
(def start-rename-subaccount management/start-rename-subaccount)
(def cancel-rename-subaccount management/cancel-rename-subaccount)
(def submit-rename-subaccount management/submit-rename-subaccount)
(def start-transfer-subaccount management/start-transfer-subaccount)
(def cancel-transfer-subaccount management/cancel-transfer-subaccount)
(def submit-transfer-subaccount management/submit-transfer-subaccount)

(defn- load-route-path-values
  [owner-address]
  (if (seq owner-address)
    [[[:account-context :subaccounts :status] :loading]
     [[:account-context :subaccounts :loaded-for-owner] owner-address]
     [[:account-context :subaccounts :rows] []]
     [[:account-context :subaccounts :error] nil]
     [[:account-context :subaccounts :create-name] ""]
     [[:account-context :subaccounts :create-popover-open?] false]
     [[:account-context :subaccounts :rename-name] ""]
     [[:account-context :subaccounts :transfer-amount] ""]
     [[:account-context :subaccounts :transfer-direction] :deposit]
     [[:account-context :subaccounts :selection-loaded?] false]]
    [[[:account-context :subaccounts :status] :idle]
     [[:account-context :subaccounts :loaded-for-owner] nil]
     [[:account-context :subaccounts :rows] []]
     [[:account-context :subaccounts :error] nil]
     [[:account-context :subaccounts :selected-address] nil]
     [[:account-context :subaccounts :create-name] ""]
     [[:account-context :subaccounts :create-popover-open?] false]
     [[:account-context :subaccounts :rename-name] ""]
     [[:account-context :subaccounts :transfer-amount] ""]
     [[:account-context :subaccounts :transfer-direction] :deposit]
     [[:account-context :subaccounts :selection-loaded?] false]]))

(defn load-subaccounts-route
  [state path]
  (if (subaccounts-route? path)
    (let [owner-address (account-context/owner-address state)]
      (cond-> [[:effects/save-many (load-route-path-values owner-address)]]
        (seq owner-address)
        (conj [:effects/api-load-subaccounts])))
    []))

(defn select-subaccount
  [state address]
  (let [owner-address (account-context/owner-address state)
        address* (account-context/normalize-address address)]
    (cond
      (not (seq owner-address))
      [[:effects/save [:account-context :subaccounts :error]
        missing-owner-message]]

      (not (and (seq address*)
                (owned-subaccount-row state owner-address address*)))
      [[:effects/save [:account-context :subaccounts :error]
        invalid-subaccount-selection-message]]

      :else
      [[:effects/save-many [[[:account-context :subaccounts :selected-address] address*]
                            [[:account-context :subaccounts :error] nil]]]
       [:effects/local-storage-set
        (selected-subaccount-storage-key owner-address)
        address*]
       [:effects/api-load-user-data address*]])))

(defn select-master-account
  [state]
  (let [owner-address (account-context/owner-address state)]
    (if-not (seq owner-address)
      [[:effects/save [:account-context :subaccounts :error]
        missing-owner-message]]
      [[:effects/save-many [[[:account-context :subaccounts :selected-address] nil]
                            [[:account-context :subaccounts :error] nil]]]
       [:effects/local-storage-set
        (selected-subaccount-storage-key owner-address)
        ""]
       [:effects/api-load-user-data owner-address]])))
