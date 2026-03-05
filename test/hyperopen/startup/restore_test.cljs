(ns hyperopen.startup.restore-test
  (:require [cljs.test :refer-macros [deftest is]]
            [hyperopen.account.context :as account-context]
            [hyperopen.platform :as platform]
            [hyperopen.startup.restore :as startup-restore]))

(deftest restore-shadow-mode-preferences-loads-watchlist-and-search-test
  (let [store (atom {:account-context {:shadow-ui {:search-error "old"}}})]
    (with-redefs [platform/local-storage-get (fn [key]
                                               (case key
                                                 "shadow-mode-watchlist:v1"
                                                 "[{\"address\":\"0x1111111111111111111111111111111111111111\",\"label\":\"Core\"},\"bad\",\"0x2222222222222222222222222222222222222222\"]"
                                                 "shadow-mode-last-search:v1"
                                                 " 0x3333333333333333333333333333333333333333 "
                                                 nil))]
      (startup-restore/restore-shadow-mode-preferences! store)
      (is (= [{:address "0x1111111111111111111111111111111111111111"
               :label "Core"}
              {:address "0x2222222222222222222222222222222222222222"
               :label nil}]
             (get-in @store [:account-context :watchlist])))
      (is (= true (get-in @store [:account-context :watchlist-loaded?])))
      (is (= "0x3333333333333333333333333333333333333333"
             (get-in @store [:account-context :shadow-ui :search])))
      (is (= "0x3333333333333333333333333333333333333333"
             (get-in @store [:account-context :shadow-ui :last-search])))
      (is (= "" (get-in @store [:account-context :shadow-ui :label])))
      (is (nil? (get-in @store [:account-context :shadow-ui :editing-watchlist-address])))
      (is (nil? (get-in @store [:account-context :shadow-ui :search-error]))))))

(deftest restore-shadow-mode-preferences-falls-back-on-malformed-storage-test
  (let [store (atom {:account-context (account-context/default-account-context-state)})]
    (with-redefs [platform/local-storage-get (fn [key]
                                               (case key
                                                 "shadow-mode-watchlist:v1"
                                                 "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,not-valid,0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                                                 "shadow-mode-last-search:v1"
                                                 " "
                                                 nil))]
      (startup-restore/restore-shadow-mode-preferences! store)
      (is (= [{:address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
               :label nil}
              {:address "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
               :label nil}]
             (get-in @store [:account-context :watchlist])))
      (is (= "" (get-in @store [:account-context :shadow-ui :search])))
      (is (= "" (get-in @store [:account-context :shadow-ui :last-search])))
      (is (= "" (get-in @store [:account-context :shadow-ui :label])))
      (is (nil? (get-in @store [:account-context :shadow-ui :editing-watchlist-address]))))))

(deftest restore-shadow-mode-preferences-migrates-legacy-ghost-storage-test
  (let [store (atom {:account-context (account-context/default-account-context-state)})
        set-calls (atom [])
        remove-calls (atom [])]
    (with-redefs [platform/local-storage-get (fn [key]
                                               (case key
                                                 "shadow-mode-watchlist:v1" nil
                                                 "shadow-mode-last-search:v1" nil
                                                 "ghost-mode-watchlist:v1"
                                                 "[{\"address\":\"0x1111111111111111111111111111111111111111\",\"label\":\"Core\"}]"
                                                 "ghost-mode-last-search:v1"
                                                 " 0x2222222222222222222222222222222222222222 "
                                                 nil))
                  platform/local-storage-set! (fn [key value]
                                                (swap! set-calls conj [key value]))
                  platform/local-storage-remove! (fn [key]
                                                   (swap! remove-calls conj key))]
      (startup-restore/restore-shadow-mode-preferences! store)
      (is (= [{:address "0x1111111111111111111111111111111111111111"
               :label "Core"}]
             (get-in @store [:account-context :watchlist])))
      (is (= "0x2222222222222222222222222222222222222222"
             (get-in @store [:account-context :shadow-ui :search])))
      (is (= "0x2222222222222222222222222222222222222222"
             (get-in @store [:account-context :shadow-ui :last-search])))
      (is (= [["shadow-mode-watchlist:v1"
               "[{\"address\":\"0x1111111111111111111111111111111111111111\",\"label\":\"Core\"}]"]
              ["shadow-mode-last-search:v1"
               "0x2222222222222222222222222222222222222222"]]
             @set-calls))
      (is (= ["ghost-mode-watchlist:v1"
              "ghost-mode-last-search:v1"]
             @remove-calls)))))

(deftest restore-shadow-mode-preferences-prefers-shadow-storage-over-legacy-test
  (let [store (atom {:account-context (account-context/default-account-context-state)})
        set-calls (atom [])
        remove-calls (atom [])]
    (with-redefs [platform/local-storage-get (fn [key]
                                               (case key
                                                 "shadow-mode-watchlist:v1"
                                                 "[{\"address\":\"0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"label\":\"Primary\"}]"
                                                 "shadow-mode-last-search:v1"
                                                 "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                                                 "ghost-mode-watchlist:v1"
                                                 "[{\"address\":\"0x1111111111111111111111111111111111111111\",\"label\":\"Legacy\"}]"
                                                 "ghost-mode-last-search:v1"
                                                 "0x2222222222222222222222222222222222222222"
                                                 nil))
                  platform/local-storage-set! (fn [key value]
                                                (swap! set-calls conj [key value]))
                  platform/local-storage-remove! (fn [key]
                                                   (swap! remove-calls conj key))]
      (startup-restore/restore-shadow-mode-preferences! store)
      (is (= [{:address "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
               :label "Primary"}]
             (get-in @store [:account-context :watchlist])))
      (is (= "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
             (get-in @store [:account-context :shadow-ui :search])))
      (is (empty? @set-calls))
      (is (empty? @remove-calls)))))
