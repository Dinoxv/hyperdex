(ns hyperopen.vaults.infrastructure.preview-cache
  (:require [clojure.string :as str]
            [hyperopen.platform :as platform]
            [hyperopen.vaults.domain.ui-state :as vault-ui-state]
            [hyperopen.views.vaults.vm :as vault-vm]))

(def vault-startup-preview-storage-key
  "vault-startup-preview:v1")

(def vault-startup-preview-version
  1)

(def ^:private vault-startup-preview-max-age-ms
  (* 60 60 1000))

(def ^:private vault-startup-preview-stale-threshold-ms
  (* 15 60 1000))

(def ^:private vault-startup-preview-protocol-row-limit
  4)

(def ^:private vault-startup-preview-user-row-limit
  8)

(defn- non-blank-text
  [value]
  (let [text (some-> value str str/trim)]
    (when (seq text)
      text)))

(defn- optional-number
  [value]
  (cond
    (number? value)
    (when (js/isFinite value)
      value)

    (string? value)
    (let [trimmed (str/trim value)]
      (when (seq trimmed)
        (let [parsed (js/Number trimmed)]
          (when (js/isFinite parsed)
            parsed))))

    :else
    nil))

(defn- parse-saved-at-ms
  [value]
  (let [candidate (optional-number value)]
    (when (number? candidate)
      (max 0 candidate))))

(defn- normalize-address
  [value]
  (some-> value non-blank-text str/lower-case))

(defn- normalize-snapshot-series
  [series]
  (if (sequential? series)
    (->> series
         (keep optional-number)
         (take 32)
         vec)
    []))

(defn- normalize-preview-row
  [row]
  (when (map? row)
    (let [vault-address (normalize-address (:vault-address row))]
      (when vault-address
        {:name (or (non-blank-text (:name row))
                   vault-address)
         :vault-address vault-address
         :leader (normalize-address (:leader row))
         :apr (or (optional-number (:apr row)) 0)
         :tvl (or (optional-number (:tvl row)) 0)
         :your-deposit (or (optional-number (:your-deposit row)) 0)
         :age-days (max 0 (or (optional-number (:age-days row)) 0))
         :snapshot-series (normalize-snapshot-series (:snapshot-series row))
         :is-closed? (boolean (:is-closed? row))}))))

(defn- normalize-preview-rows
  [rows limit]
  (if (sequential? rows)
    (->> rows
         (keep normalize-preview-row)
         (take limit)
         vec)
    []))

(defn- preview-total-visible-tvl
  [protocol-rows user-rows fallback]
  (or (optional-number fallback)
      (reduce (fn [acc row]
                (+ acc (or (:tvl row) 0)))
              0
              (concat (or protocol-rows [])
                      (or user-rows [])))))

(defn normalize-vault-startup-preview-record
  [raw]
  (when (map? raw)
    (let [version (:version raw)
          saved-at-ms (parse-saved-at-ms (:saved-at-ms raw))
          protocol-rows (normalize-preview-rows (:protocol-rows raw)
                                                vault-startup-preview-protocol-row-limit)
          user-rows (normalize-preview-rows (:user-rows raw)
                                            vault-startup-preview-user-row-limit)]
      (when (and (= vault-startup-preview-version version)
                 (some? saved-at-ms)
                 (or (seq protocol-rows)
                     (seq user-rows)))
        {:id vault-startup-preview-storage-key
         :version vault-startup-preview-version
         :saved-at-ms saved-at-ms
         :snapshot-range (vault-ui-state/normalize-vault-snapshot-range
                          (:snapshot-range raw))
         :wallet-address (normalize-address (:wallet-address raw))
         :total-visible-tvl (preview-total-visible-tvl protocol-rows
                                                       user-rows
                                                       (:total-visible-tvl raw))
         :protocol-rows protocol-rows
         :user-rows user-rows}))))

(defn build-vault-startup-preview-record
  [state]
  (when-let [preview (vault-vm/build-startup-preview-record
                      state
                      {:protocol-row-limit vault-startup-preview-protocol-row-limit
                       :user-row-limit vault-startup-preview-user-row-limit
                       :now-ms (platform/now-ms)})]
    (assoc preview
           :id vault-startup-preview-storage-key
           :version vault-startup-preview-version)))

(defn load-vault-startup-preview-record!
  []
  (try
    (let [raw (platform/local-storage-get vault-startup-preview-storage-key)]
      (when (some? raw)
        (let [parsed (try
                       (js->clj (js/JSON.parse raw) :keywordize-keys true)
                       (catch :default _
                         ::invalid))
              normalized (when-not (= ::invalid parsed)
                           (normalize-vault-startup-preview-record parsed))]
          (when-not normalized
            (platform/local-storage-remove! vault-startup-preview-storage-key))
          normalized)))
    (catch :default error
      (js/console.warn "Failed to load vault startup preview cache:" error)
      nil)))

(defn restore-vault-startup-preview
  [preview-record {:keys [snapshot-range wallet-address now-ms]}]
  (let [preview* (normalize-vault-startup-preview-record preview-record)
        preview-snapshot-range (vault-ui-state/normalize-vault-snapshot-range
                                (:snapshot-range preview*))
        current-snapshot-range (vault-ui-state/normalize-vault-snapshot-range snapshot-range)
        current-wallet-address (normalize-address wallet-address)
        preview-wallet-address (normalize-address (:wallet-address preview*))
        saved-at-ms (:saved-at-ms preview*)
        age-ms (when (and (number? now-ms)
                          (number? saved-at-ms))
                 (max 0 (- now-ms saved-at-ms)))
        wallet-match? (and (seq current-wallet-address)
                           (= current-wallet-address preview-wallet-address))
        protocol-rows (vec (or (:protocol-rows preview*) []))
        user-rows (if wallet-match?
                    (vec (or (:user-rows preview*) []))
                    [])]
    (when (and preview*
               (= preview-snapshot-range current-snapshot-range)
               (number? age-ms)
               (<= age-ms vault-startup-preview-max-age-ms)
               (or (seq protocol-rows)
                   (seq user-rows)))
      {:id vault-startup-preview-storage-key
       :version vault-startup-preview-version
       :saved-at-ms saved-at-ms
       :snapshot-range current-snapshot-range
       :wallet-address (when wallet-match?
                         current-wallet-address)
       :total-visible-tvl (preview-total-visible-tvl protocol-rows
                                                     user-rows
                                                     nil)
       :protocol-rows protocol-rows
       :user-rows user-rows
       :stale? (>= age-ms vault-startup-preview-stale-threshold-ms)})))

(defn clear-vault-startup-preview!
  []
  (try
    (platform/local-storage-remove! vault-startup-preview-storage-key)
    true
    (catch :default error
      (js/console.warn "Failed to clear vault startup preview cache:" error)
      false)))

(defn persist-vault-startup-preview-record!
  [state]
  (try
    (if-let [preview-record (build-vault-startup-preview-record state)]
      (do
        (platform/local-storage-set!
         vault-startup-preview-storage-key
         (js/JSON.stringify (clj->js preview-record)))
        true)
      false)
    (catch :default error
      (js/console.warn "Failed to persist vault startup preview cache:" error)
      false)))
