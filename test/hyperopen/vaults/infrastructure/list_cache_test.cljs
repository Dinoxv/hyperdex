(ns hyperopen.vaults.infrastructure.list-cache-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.core-bootstrap.test-support.browser-mocks :as browser-mocks]
            [hyperopen.platform :as platform]
            [hyperopen.platform.indexed-db :as indexed-db]
            [hyperopen.test-support.async :as async-support]
            [hyperopen.vaults.infrastructure.list-cache :as list-cache]
            [hyperopen.vaults.infrastructure.preview-cache :as preview-cache]))

(deftest normalize-vault-index-cache-record-preserves-preview-row-shape-test
  (let [record (list-cache/normalize-vault-index-cache-record
                {:id "custom-cache-id"
                 :version "3"
                 :saved-at-ms "1700000000000"
                 :etag " \"etag-1\" "
                 :last-modified " Thu, 20 Mar 2026 12:00:00 GMT "
                 :rows [{:name "Alpha Vault"
                         :vault-address "0xABc"
                         :leader "0xDEF"
                         :tvl "12.5"
                         :tvl-raw "12.5"
                         :is-closed? false
                         :relationship {:type :parent
                                        :child-addresses ["0xC1" "  "]}
                         :create-time-ms "1700"
                         :apr "0.25"
                         :apr-raw "0.25"
                         :snapshot-preview-by-key {:day {:series [1 "2.5" nil]
                                                         :last-value "2.5"}
                                                   :week {:series []}}}]})]
    (is (= {:id "custom-cache-id"
            :version 3
            :saved-at-ms 1700000000000
            :etag "\"etag-1\""
            :last-modified "Thu, 20 Mar 2026 12:00:00 GMT"
            :rows [{:name "Alpha Vault"
                    :vault-address "0xabc"
                    :leader "0xdef"
                    :tvl 12.5
                    :tvl-raw "12.5"
                    :is-closed? false
                    :relationship {:type :parent
                                   :child-addresses ["0xc1"]}
                    :create-time-ms 1700
                    :apr 0.25
                    :apr-raw "0.25"
                    :snapshot-preview-by-key {:day {:series [1 2.5]
                                                    :last-value 2.5}}}]}
           record))))

(deftest normalize-vault-index-cache-record-rejects-invalid-shapes-test
  (is (nil? (list-cache/normalize-vault-index-cache-record
             {:rows [{:vault-address "0xabc"}]})))
  (is (nil? (list-cache/normalize-vault-index-cache-record
             {:saved-at-ms "1700"
              :rows "not-a-seq"})))
  (is (nil? (list-cache/normalize-vault-index-cache-record nil))))

(deftest persist-and-load-vault-index-cache-roundtrip-test
  (async done
    (browser-mocks/with-test-indexed-db
      (fn []
        (with-redefs [platform/now-ms (fn []
                                        1700000000000)]
          (-> (list-cache/persist-vault-index-cache-record!
               [{:name "Alpha Vault"
                 :vault-address "0xAbC"
                 :leader "0xDEF"
                 :tvl 12.5
                 :tvl-raw "12.5"
                 :is-closed? false
                 :relationship {:type :child
                                :parent-address "0xPARENT"}
                 :create-time-ms 1700
                 :apr 0.25
                 :apr-raw "0.25"
                 :snapshot-preview-by-key {:day {:series [1 2.5]
                                                 :last-value 2.5}}}]
               {:etag "\"etag-1\""
                :last-modified "Thu, 20 Mar 2026 12:00:00 GMT"})
              (.then (fn [persisted?]
                       (is (true? persisted?))
                       (-> (list-cache/load-vault-index-cache-record!)
                           (.then (fn [record]
                                    (is (= {:id "vault-index-cache"
                                            :version 1
                                            :saved-at-ms 1700000000000
                                            :etag "\"etag-1\""
                                            :last-modified "Thu, 20 Mar 2026 12:00:00 GMT"
                                            :rows [{:name "Alpha Vault"
                                                    :vault-address "0xabc"
                                                    :leader "0xdef"
                                                    :tvl 12.5
                                                    :tvl-raw 12.5
                                                    :is-closed? false
                                                    :relationship {:type :child
                                                                   :parent-address "0xparent"}
                                                    :create-time-ms 1700
                                                    :apr 0.25
                                                    :apr-raw 0.25
                                                    :snapshot-preview-by-key {:day {:series [1 2.5]
                                                                                    :last-value 2.5}}}]}
                                           record))
                                    (done)))
                           (.catch (async-support/unexpected-error done)))))
              (.catch (async-support/unexpected-error done))))))))

(deftest vault-index-cache-helpers-gracefully-handle-unavailable-indexed-db-test
  (async done
    (let [original-indexed-db (.-indexedDB js/globalThis)
          restore! (fn []
                     (set! (.-indexedDB js/globalThis) original-indexed-db)
                     (indexed-db/clear-open-db-cache!))
          fail! (fn [error]
                  (restore!)
                  ((async-support/unexpected-error done) error))]
      (indexed-db/clear-open-db-cache!)
      (set! (.-indexedDB js/globalThis) nil)
      (-> (list-cache/load-vault-index-cache-record!)
          (.then (fn [record]
                   (is (nil? record))
                   (-> (list-cache/persist-vault-index-cache-record!
                        [{:vault-address "0xabc"}]
                        {:etag "\"etag-1\""})
                       (.then (fn [persisted?]
                                (is (false? persisted?))
                                (restore!)
                                (done)))
                       (.catch fail!))))
          (.catch fail!)))))

(deftest vault-startup-preview-cache-roundtrip-and-bounds-on-load-test
  (let [storage (atom nil)
        state {:wallet {:address "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"}
               :vaults-ui {:search-query ""
                           :filter-leading? true
                           :filter-deposited? true
                           :filter-others? true
                           :filter-closed? false
                           :snapshot-range :month
                           :user-vaults-page-size 10
                           :user-vaults-page 1
                           :sort {:column :tvl
                                  :direction :desc}}
               :vaults {:merged-index-rows (vec
                                            (concat
                                             [{:name "Hyperliquidity Provider (HLP)"
                                               :vault-address "0x1111111111111111111111111111111111111111"
                                               :leader "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
                                               :tvl 500
                                               :apr 0.12
                                               :relationship {:type :parent}
                                               :is-closed? false
                                               :create-time-ms (- 1700000000000 (* 2 24 60 60 1000))
                                               :snapshot-by-key {:month [0.01 0.02]}}]
                                             (for [idx (range 12)]
                                               {:name (str "Vault " idx)
                                                :vault-address (str "0x00000000000000000000000000000000000000" idx)
                                                :leader (str "0x10000000000000000000000000000000000000" idx)
                                                :tvl (+ 100 idx)
                                                :apr (+ 0.01 (* idx 0.01))
                                                :relationship {:type :normal}
                                                :is-closed? false
                                                :create-time-ms (- 1700000000000 (* (+ idx 3) 24 60 60 1000))
                                                :snapshot-by-key {:month [0.01 0.02]}})))
                        :user-equity-by-address {}}}]
    (with-redefs [platform/now-ms (fn [] 1700000000000)
                  platform/local-storage-set! (fn [_key value]
                                                (reset! storage value))
                  platform/local-storage-get (fn [_key]
                                               @storage)]
      (is (true? (preview-cache/persist-vault-startup-preview-record! state)))
      (let [record (preview-cache/load-vault-startup-preview-record!)]
        (is (= "vault-startup-preview:v1" (:id record)))
        (is (= 1 (:version record)))
        (is (= 1700000000000 (:saved-at-ms record)))
        (is (= :month (:snapshot-range record)))
        (is (= "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
               (:wallet-address record)))
        (is (= 1 (count (:protocol-rows record))))
        (is (= 8 (count (:user-rows record))))
        (is (= "0x1111111111111111111111111111111111111111"
               (get-in record [:protocol-rows 0 :vault-address])))
        (is (= "Vault 11" (get-in record [:user-rows 0 :name])))))))

(deftest vault-startup-preview-cache-rejects-invalid-json-and-write-failures-test
  (let [storage (atom "not-json")
        write-calls (atom 0)]
    (with-redefs [platform/local-storage-get (fn [_key]
                                               @storage)
                  platform/local-storage-set! (fn [_key _value]
                                                (swap! write-calls inc)
                                                (throw (js/Error. "storage-unavailable")))
                  platform/local-storage-remove! (fn [_key]
                                                   nil)]
      (is (nil? (preview-cache/load-vault-startup-preview-record!)))
      (is (false? (preview-cache/persist-vault-startup-preview-record!
                   {:vaults-ui {:snapshot-range :month}
                    :vaults {:merged-index-rows [{:vault-address "0xabc"
                                                 :leader "0xdef"
                                                 :snapshot-by-key {:month [0.01]}}]}})))
      (is (= 1 @write-calls)))))

(deftest vault-startup-preview-restore-rejects-stale-records-and-drops-wallet-scoped-user-rows-test
  (let [preview-record {:id "vault-startup-preview:v1"
                        :version 1
                        :saved-at-ms 1700000000000
                        :snapshot-range :month
                        :wallet-address "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
                        :total-visible-tvl 600
                        :protocol-rows [{:name "Protocol Vault"
                                         :vault-address "0x1111111111111111111111111111111111111111"
                                         :leader "0x2222222222222222222222222222222222222222"
                                         :tvl 500
                                         :apr 12
                                         :your-deposit 0
                                         :age-days 2
                                         :snapshot-series [1 2]}]
                        :user-rows [{:name "User Vault"
                                     :vault-address "0x3333333333333333333333333333333333333333"
                                     :leader "0x4444444444444444444444444444444444444444"
                                     :tvl 100
                                     :apr 8
                                     :your-deposit 50
                                     :age-days 3
                                     :snapshot-series [1 3]}]}
        wallet-mismatch (preview-cache/restore-vault-startup-preview
                         preview-record
                         {:snapshot-range :month
                          :wallet-address nil
                          :now-ms (+ 1700000000000 (* 5 60 1000))})
        stale-preview (preview-cache/restore-vault-startup-preview
                       preview-record
                       {:snapshot-range :month
                        :wallet-address "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"
                        :now-ms (+ 1700000000000 (* 2 60 60 1000))})]
    (is (= [] (:user-rows wallet-mismatch)))
    (is (nil? (:wallet-address wallet-mismatch)))
    (is (= 500 (:total-visible-tvl wallet-mismatch)))
    (is (nil? stale-preview))))
