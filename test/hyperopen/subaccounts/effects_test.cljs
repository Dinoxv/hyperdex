(ns hyperopen.subaccounts.effects-test
  (:require [clojure.string :as str]
            [cljs.test :refer-macros [async deftest is]]
            [hyperopen.subaccounts.actions :as actions]
            [hyperopen.subaccounts.effects :as effects]))

(def owner-address
  "0x1234567890abcdef1234567890abcdef12345678")

(def subaccount-address
  "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd")

(def other-subaccount-address
  "0x9999999999999999999999999999999999999999")

(defn- management-store
  []
  (atom {:router {:path "/subAccounts"}
         :wallet {:address owner-address}
         :account-context
         {:subaccounts {:rows [{:name "Desk"
                                :master owner-address
                                :sub-account-user subaccount-address}]
                        :selected-address subaccount-address
                        :create-name "Desk"
                        :rename-name "Ops"
                        :transfer-amount "1.23"
                        :transfer-direction :deposit
                        :creating? true
                        :renaming-address subaccount-address
                        :transferring-address subaccount-address}}}))

(deftest load-subaccounts-resolves-without-requesting-when-route-is-inactive-test
  (async done
    (let [calls (atom 0)
          store (atom {:router {:path "/trade"}
                       :wallet {:address owner-address}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (swap! calls inc)
                                     (js/Promise.resolve []))})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= 0 @calls))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected inactive-route error: " err))
                    (done)))))))

(deftest load-subaccounts-resets-state-when-owner-wallet-is-missing-test
  (async done
    (let [calls (atom 0)
          store (atom {:router {:path "/subAccounts"}
                       :wallet {:address nil}
                       :account-context
                       {:subaccounts {:status :loaded
                                      :loaded-for-owner owner-address
                                      :rows [{:name "Desk"}]
                                      :selected-address subaccount-address
                                      :error "stale"
                                      :selection-loaded? true}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (swap! calls inc)
                                     (js/Promise.resolve []))})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= 0 @calls))
                   (is (= {:status :idle
                           :loaded-for-owner nil
                           :rows []
                           :error nil
                           :selected-address nil
                           :selection-loaded? false
                           :create-name ""
                           :rename-name ""
                           :transfer-amount ""
                           :transfer-direction :deposit
                           :creating? false
                           :renaming-address nil
                           :transferring-address nil}
                          (get-in @store [:account-context :subaccounts])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected missing-owner error: " err))
                    (done)))))))

(deftest load-subaccounts-applies-owned-rows-and-restores-valid-selection-test
  (async done
    (let [calls (atom [])
          store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:status :loading
                                      :loaded-for-owner owner-address
                                      :rows []
                                      :selected-address nil
                                      :error "stale"
                                      :selection-loaded? false}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [address opts]
                                     (swap! calls conj [address opts])
                                     (js/Promise.resolve
                                      [{:name "Desk"
                                        :master owner-address
                                        :sub-account-user subaccount-address}
                                       {:name "Other"
                                        :master owner-address
                                        :sub-account-user other-subaccount-address}]))
            :local-storage-get (fn [key]
                                 (when (= key (actions/selected-subaccount-storage-key owner-address))
                                   subaccount-address))})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= [[owner-address {:priority :high}]]
                          @calls))
                   (is (= :loaded
                          (get-in @store [:account-context :subaccounts :status])))
                   (is (= owner-address
                          (get-in @store [:account-context :subaccounts :loaded-for-owner])))
                   (is (= subaccount-address
                          (get-in @store [:account-context :subaccounts :selected-address])))
                   (is (true? (get-in @store [:account-context :subaccounts :selection-loaded?])))
                   (is (nil? (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected load-subaccounts success-path error: " err))
                    (done)))))))

(deftest load-subaccounts-keeps-current-valid-selection-ahead-of-stored-selection-test
  (async done
    (let [store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:selected-address other-subaccount-address}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (js/Promise.resolve
                                      [{:name "Desk"
                                        :master owner-address
                                        :sub-account-user subaccount-address}
                                       {:name "Other"
                                        :master owner-address
                                        :sub-account-user other-subaccount-address}]))
            :local-storage-get (constantly subaccount-address)})
          (.then (fn [_]
                   (is (= other-subaccount-address
                          (get-in @store [:account-context :subaccounts :selected-address])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected selection-retention error: " err))
                    (done)))))))

(deftest load-subaccounts-clears-unowned-stored-selection-test
  (async done
    (let [store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:selected-address nil}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (js/Promise.resolve
                                      [{:name "Desk"
                                        :master owner-address
                                        :sub-account-user subaccount-address}]))
            :local-storage-get (constantly other-subaccount-address)})
          (.then (fn [_]
                   (is (nil? (get-in @store [:account-context :subaccounts :selected-address])))
                   (is (true? (get-in @store [:account-context :subaccounts :selection-loaded?])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected stored-selection clear error: " err))
                    (done)))))))

(deftest load-subaccounts-records-request-error-without-rejecting-test
  (async done
    (let [store (atom {:router {:path "/subAccounts"}
                       :wallet {:address owner-address}
                       :account-context
                       {:subaccounts {:status :loading
                                      :error nil}}})]
      (-> (effects/load-subaccounts!
           {:store store
            :request-sub-accounts! (fn [_address _opts]
                                     (js/Promise.reject (js/Error. "boom")))})
          (.then (fn [result]
                   (is (nil? result))
                   (is (= :error
                          (get-in @store [:account-context :subaccounts :status])))
                   (is (= "boom"
                          (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected load-subaccounts rejection: " err))
                    (done)))))))

(deftest api-load-subaccounts-wrapper-defaults-force-refresh-to-false-test
  (let [captured-opts (atom nil)]
    (with-redefs [effects/load-subaccounts!
                  (fn [opts]
                    (reset! captured-opts opts)
                    :loaded)]
      (is (= :loaded
             (effects/api-load-subaccounts! {:store :test-store
                                             :request-sub-accounts! :request})))
      (is (= {:store :test-store
              :request-sub-accounts! :request
              :force-refresh? false}
             @captured-opts)))))

(deftest create-subaccount-success-clears-form-and-force-refreshes-rows-test
  (async done
    (let [store (management-store)
          submit-calls (atom [])
          refresh-calls (atom [])]
      (-> (effects/create-subaccount!
           {:store store
            :create-sub-account! (fn [store* owner name]
                                   (swap! submit-calls conj [store* owner name])
                                   (js/Promise.resolve {:status "ok"
                                                        :response {:type "createSubAccount"
                                                                   :data other-subaccount-address}}))
            :load-subaccounts! (fn [opts]
                                 (swap! refresh-calls conj
                                        (select-keys opts [:force-refresh?]))
                                 (js/Promise.resolve :reloaded))
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [result]
                   (is (= :reloaded result))
                   (is (= [[store owner-address "Desk"]] @submit-calls))
                   (is (= [{:force-refresh? true}] @refresh-calls))
                   (is (false? (get-in @store [:account-context :subaccounts :creating?])))
                   (is (= "" (get-in @store [:account-context :subaccounts :create-name])))
                   (is (nil? (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected create-subaccount success error: " err))
                    (done)))))))

(deftest create-subaccount-exchange-error-clears-pending-and-surfaces-message-test
  (async done
    (let [store (management-store)
          refresh-calls (atom 0)]
      (-> (effects/create-subaccount!
           {:store store
            :create-sub-account! (fn [_store _owner _name]
                                   (js/Promise.resolve {:status "err"
                                                        :response "Sub-account limit reached"}))
            :load-subaccounts! (fn [_opts]
                                 (swap! refresh-calls inc)
                                 (js/Promise.resolve :reloaded))
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [_]
                   (is (= 0 @refresh-calls))
                   (is (false? (get-in @store [:account-context :subaccounts :creating?])))
                   (is (= "Sub-account limit reached"
                          (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected create-subaccount error handling rejection: " err))
                    (done)))))))

(deftest rename-subaccount-success-normalizes-address-and-refreshes-test
  (async done
    (let [store (management-store)
          submit-calls (atom [])
          refresh-calls (atom [])]
      (-> (effects/rename-subaccount!
           {:store store
            :request {:sub-account-user (str/upper-case subaccount-address)
                      :name "Ops"}
            :modify-sub-account! (fn [store* owner address name]
                                   (swap! submit-calls conj [store* owner address name])
                                   (js/Promise.resolve {:status "ok"
                                                        :response {:type "default"}}))
            :load-subaccounts! (fn [opts]
                                 (swap! refresh-calls conj
                                        (select-keys opts [:force-refresh?]))
                                 (js/Promise.resolve :reloaded))
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [result]
                   (is (= :reloaded result))
                   (is (= [[store owner-address subaccount-address "Ops"]]
                          @submit-calls))
                   (is (= [{:force-refresh? true}] @refresh-calls))
                   (is (nil? (get-in @store [:account-context :subaccounts :renaming-address])))
                   (is (= "" (get-in @store [:account-context :subaccounts :rename-name])))
                   (is (nil? (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected rename-subaccount success error: " err))
                    (done)))))))

(deftest transfer-subaccount-success-refreshes-rows-and-active-user-data-test
  (async done
    (let [store (management-store)
          submit-calls (atom [])
          refresh-calls (atom [])
          dispatch-calls (atom [])]
      (-> (effects/transfer-subaccount!
           {:store store
            :request {:sub-account-user subaccount-address
                      :is-deposit true
                      :usd 1230000
                      :amount "1.23"}
            :transfer-sub-account! (fn [store* owner address is-deposit? usd]
                                     (swap! submit-calls conj
                                            [store* owner address is-deposit? usd])
                                     (js/Promise.resolve {:status "ok"
                                                          :response {:type "default"}}))
            :load-subaccounts! (fn [opts]
                                 (swap! refresh-calls conj
                                        (select-keys opts [:force-refresh?]))
                                 (js/Promise.resolve :reloaded))
            :dispatch! (fn [store* ctx effects*]
                         (swap! dispatch-calls conj [store* ctx effects*])
                         nil)
            :runtime-error-message (fn [err] (str err))})
          (.then (fn [result]
                   (is (= :reloaded result))
                   (is (= [[store owner-address subaccount-address true 1230000]]
                          @submit-calls))
                   (is (= [{:force-refresh? true}] @refresh-calls))
                   (is (= [[store nil [[:effects/api-load-user-data subaccount-address]]]]
                          @dispatch-calls))
                   (is (nil? (get-in @store [:account-context :subaccounts :transferring-address])))
                   (is (= "" (get-in @store [:account-context :subaccounts :transfer-amount])))
                   (is (= :deposit
                          (get-in @store [:account-context :subaccounts :transfer-direction])))
                   (is (nil? (get-in @store [:account-context :subaccounts :error])))
                   (done)))
          (.catch (fn [err]
                    (is false (str "Unexpected transfer-subaccount success error: " err))
                    (done)))))))
