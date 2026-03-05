(ns hyperopen.websocket.endpoints-coverage-test
  (:require [cljs.test :refer-macros [async deftest is]]
            [hyperopen.api.endpoints.account :as account-endpoints]
            [hyperopen.api.endpoints.market :as market-endpoints]
            [hyperopen.api.endpoints.orders :as orders-endpoints]
            [hyperopen.api.endpoints.vaults :as vaults-endpoints]
            [hyperopen.test-support.api-stubs :as api-stubs]
            [hyperopen.test-support.async :as async-support]))

(defn- ok-response
  [payload]
  #js {:ok true
       :status 200
       :json (fn []
               (js/Promise.resolve (clj->js payload)))})

(deftest ws-account-endpoints-coverage-smoke-test
  (async done
    (let [calls (atom [])
          post-info! (api-stubs/post-info-stub
                      calls
                      (fn [body _opts]
                        (case (get body "type")
                          "userFunding" (if (= 0 (get body "startTime"))
                                          {:data {:fundings [{:time 1000}]}}
                                          {:data {:fundings []}})
                          "spotClearinghouseState" {:ok true}
                          "userAbstraction" "unifiedAccount"
                          "clearinghouseState" {:ok true}
                          "portfolio" {:data {"day" {:equity "1"}
                                              "perpAllTime" {:equity "2"}}}
                          "userFees" {:makerFee "0.01"}
                          "userNonFundingLedgerUpdates" {:data {:nonFundingLedgerUpdates [{:time 123}]}}
                          nil)))
          normalize-rows-fn (fn [rows]
                              (mapv (fn [row]
                                      {:time-ms (:time row)})
                                    rows))
          summary-input [["day" {:v 1}]
                         ["week" {:v 2}]
                         ["month" {:v 3}]
                         ["3m" {:v 4}]
                         ["6m" {:v 5}]
                         ["1y" {:v 6}]
                         ["2y" {:v 7}]
                         ["alltime" {:v 8}]
                         ["perpday" {:v 9}]
                         ["perpweek" {:v 10}]
                         ["perpmonth" {:v 11}]
                         ["perp3m" {:v 12}]
                         ["perp6m" {:v 13}]
                         ["perp1y" {:v 14}]
                         ["perp2y" {:v 15}]
                         ["perpalltime" {:v 16}]
                         ["customRange" {:v 17}]]
          summary (account-endpoints/normalize-portfolio-summary summary-input)]
      (-> (js/Promise.all
           #js [(account-endpoints/request-user-funding-history! post-info!
                                                                 normalize-rows-fn
                                                                 identity
                                                                 "0xAbC"
                                                                 0
                                                                 5000
                                                                 {})
                (account-endpoints/request-user-funding-history! post-info!
                                                                 normalize-rows-fn
                                                                 identity
                                                                 nil
                                                                 0
                                                                 5000
                                                                 {})
                (account-endpoints/request-spot-clearinghouse-state! post-info! "0xabc" {})
                (account-endpoints/request-user-abstraction! post-info! "0xAbC" {})
                (account-endpoints/request-clearinghouse-state! post-info! "0xAbC" "vault" {})
                (account-endpoints/request-portfolio! post-info! "0xAbC" {})
                (account-endpoints/request-user-fees! post-info! "0xAbC" {})
                (account-endpoints/request-user-non-funding-ledger-updates! post-info!
                                                                            "0xAbC"
                                                                            10.5
                                                                            20.5
                                                                            {})
                (account-endpoints/request-portfolio! post-info! nil {})
                (account-endpoints/request-user-fees! post-info! nil {})])
          (.then
           (fn [results]
             (let [results* (vec (array-seq results))]
               (is (= [{:time-ms 1000}] (nth results* 0)))
               (is (= [] (nth results* 1)))
               (is (= {:ok true} (nth results* 2)))
               (is (= "unifiedAccount" (nth results* 3)))
               (is (= {:ok true} (nth results* 4)))
               (is (= {:day {:equity "1"}
                       :perp-all-time {:equity "2"}}
                      (nth results* 5)))
               (is (= {:makerFee "0.01"} (nth results* 6)))
               (is (= [{:time 123}] (nth results* 7)))
               (is (= {} (nth results* 8)))
               (is (nil? (nth results* 9)))
               (is (= :unified (account-endpoints/normalize-user-abstraction-mode "portfolioMargin")))
               (is (= :classic (account-endpoints/normalize-user-abstraction-mode "dexAbstraction")))
               (is (= :classic (account-endpoints/normalize-user-abstraction-mode "unknown")))
               (is (= 17 (count summary)))
               (is (some #(= "portfolio" (get-in % [0 "type"])) @calls))
               (done))))
          (.catch (async-support/unexpected-error done))))))

(deftest ws-market-endpoints-coverage-smoke-test
  (async done
    (let [calls (atom [])
          post-info! (api-stubs/post-info-stub
                      calls
                      (fn [body _opts]
                        (case (get body "type")
                          "metaAndAssetCtxs" [{:universe []
                                               :marginTables []}
                                              []]
                          "perpDexs" [{:name "vault"}
                                      {:name "scaled"
                                       :deployerFeeScale 0.25}]
                          "candleSnapshot" []
                          "spotMeta" {:ok true}
                          "webData2" {:ok true}
                          "predictedFundings" {:rows []}
                          nil)))
          now-ms-fn (fn [] 10000)]
      (with-redefs [hyperopen.asset-selector.markets/build-perp-markets
                    (fn [_meta _asset-ctxs _token-by-index & {:keys [dex]}]
                      [{:key [:perp dex]
                        :coin (or dex "DEFAULT")}])
                    hyperopen.asset-selector.markets/build-spot-markets
                    (fn [_spot-meta _spot-asset-ctxs]
                      [{:key [:spot "HYPE"]
                        :coin "HYPE"}])
                    hyperopen.asset-selector.markets/resolve-market-by-coin
                    (fn [market-by-key _active-asset]
                      (get market-by-key [:perp "vault"]))]
        (let [market-state (market-endpoints/build-market-state (fn [] 77)
                                                                 "BTC"
                                                                 :live
                                                                 ["vault"]
                                                                 {:tokens [{:index 0
                                                                            :name "HYPE"}]}
                                                                 {}
                                                                 [[{:meta :m0}
                                                                   {:ctx :c0}]
                                                                  [{:meta :m1}
                                                                   {:ctx :c1}]])]
          (-> (js/Promise.all
               #js [(market-endpoints/request-asset-contexts! post-info! {})
                    (market-endpoints/request-meta-and-asset-ctxs! post-info! nil {})
                    (market-endpoints/request-meta-and-asset-ctxs! post-info! "vault" {})
                    (market-endpoints/request-perp-dexs! post-info! {})
                    (market-endpoints/request-candle-snapshot! post-info!
                                                               now-ms-fn
                                                               "BTC"
                                                               {:interval :1m
                                                                :bars 10
                                                                :priority :low})
                    (market-endpoints/request-candle-snapshot! post-info! now-ms-fn nil {})
                    (market-endpoints/request-spot-meta! post-info! {})
                    (market-endpoints/request-public-webdata2! post-info! {})
                    (market-endpoints/request-predicted-fundings! post-info! {})])
              (.then
               (fn [results]
                 (let [results* (vec (array-seq results))]
                   (is (map? (nth results* 0)))
                   (is (= [:meta-and-asset-ctxs "vault"]
                          (:dedupe-key (second (nth @calls 2)))))
                   (is (= ["vault" "scaled"] (:dex-names (nth results* 3))))
                   (is (nil? (nth results* 5)))
                   (is (= 8 (count @calls)))
                   (is (some #(= {"type" "spotMeta"} (first %)) @calls))
                   (is (some #(= {"type" "webData2"
                                  "user" "0x0000000000000000000000000000000000000000"}
                                 (first %))
                             @calls))
                   (is (some #(= {"type" "predictedFundings"} (first %)) @calls))
                   (is (= 3 (count (:markets market-state))))
                   (is (= [:perp "vault"] (:key (:active-market market-state))))
                   (is (= 77 (:loaded-at-ms market-state)))
                   (done))))
              (.catch (async-support/unexpected-error done))))))))

(deftest ws-orders-endpoints-coverage-smoke-test
  (async done
    (let [calls (atom [])
          post-info! (api-stubs/post-info-stub
                      calls
                      (fn [body _opts]
                        (case (get body "type")
                          "frontendOpenOrders" [{:coin "BTC"}]
                          "userFills" [{:coin "ETH"}]
                          "historicalOrders" {:orders [{:coin "SOL"
                                                        :oid 1}
                                                       {:order {:coin "BTC"
                                                                :oid 2}}
                                                       "invalid"]}
                          nil)))]
      (-> (js/Promise.all
           #js [(orders-endpoints/request-frontend-open-orders! post-info! "0xabc" nil {})
                (orders-endpoints/request-frontend-open-orders! post-info! "0xabc" "vault" {})
                (orders-endpoints/request-user-fills! post-info! "0xabc" {})
                (orders-endpoints/request-historical-orders! post-info! "0xabc" {})
                (orders-endpoints/request-historical-orders! post-info! nil {})])
          (.then
           (fn [results]
             (let [results* (vec (array-seq results))]
               (is (= {"type" "frontendOpenOrders"
                       "user" "0xabc"}
                      (first (nth @calls 0))))
               (is (= {"type" "frontendOpenOrders"
                       "user" "0xabc"
                       "dex" "vault"}
                      (first (nth @calls 1))))
               (is (= {"type" "userFills"
                       "user" "0xabc"
                       "aggregateByTime" true}
                      (first (nth @calls 2))))
               (is (= 2 (count (nth results* 3))))
               (is (= "SOL" (get-in results* [3 0 :order :coin])))
               (is (= [] (nth results* 4)))
               (done))))
          (.catch (async-support/unexpected-error done))))))

(deftest ws-vaults-endpoints-normalization-coverage-smoke-test
  (let [pnls (vaults-endpoints/normalize-vault-pnls
              [["day" ["1.5" "2.5"]]
               ["allTime" ["3.5"]]
               ["ignored" ["4.5"]]
               [:bad]])
        relationship-parent (vaults-endpoints/normalize-vault-relationship
                             {:type "parent"
                              :data {:childAddresses ["0xC1"
                                                      " "]}})
        relationship-child (vaults-endpoints/normalize-vault-relationship
                            {:type "child"
                             :data {:parentAddress "0xPARENT"}})
        summary (vaults-endpoints/normalize-vault-summary
                 {:name " Vault Alpha "
                  :vaultAddress "0xABc"
                  :leader "0xDEF"
                  :tvl "12.5"
                  :isClosed "false"
                  :relationship {:type "parent"
                                 :data {:childAddresses ["0xC1"]}}
                  :createTimeMillis "1700"})
        index-row (vaults-endpoints/normalize-vault-index-row
                   {:apr "0.25"
                    :pnls [["day" ["1.0"]]]
                    :summary {:vaultAddress "0xA1"
                              :leader "0xB2"}})
        normalized-details (vaults-endpoints/normalize-vault-details
                            {:name "Vault Detail"
                             :vaultAddress "0xVaUlT"
                             :leader "0xLEADER"
                             :description "  hello  "
                             :portfolio [["day" {:accountValue "10"}]]
                             :apr "0.7"
                             :followers [{:user "0xA"}
                                         {:user "0xB"}]
                             :followersCount "3"
                             :isClosed "false"
                             :relationship {:type "child"
                                            :data {:parentAddress "0xPARENT"}}
                             :allowDeposits "true"
                             :alwaysCloseOnWithdraw false})]
    (is (= {:day [1.5 2.5]
            :all-time [3.5]}
           pnls))
    (is (= {:type :parent
            :child-addresses ["0xc1"]}
           relationship-parent))
    (is (= {:type :child
            :parent-address "0xparent"}
           relationship-child))
    (is (= "Vault Alpha" (:name summary)))
    (is (= "0xabc" (:vault-address summary)))
    (is (= "0xdef" (:leader summary)))
    (is (= 12.5 (:tvl summary)))
    (is (= "0xa1" (:vault-address index-row)))
    (is (= {:day [1]} (:snapshot-by-key index-row)))
    (is (= 2 (:followers-count normalized-details)))
    (is (true? (:allow-deposits? normalized-details)))
    (is (false? (:always-close-on-withdraw? normalized-details)))
    (is (= ["0x1" "0x2" "0x3"]
           (mapv :vault-address
                 (vaults-endpoints/merge-vault-index-with-summaries
                  [{:summary {:vaultAddress "0x1"
                              :createTimeMillis 100}}
                   {:summary {:vaultAddress "0x2"
                              :createTimeMillis 200}}]
                  [{:summary {:vaultAddress "0x2"
                              :createTimeMillis 350}}
                   {:summary {:vaultAddress "0x3"
                              :createTimeMillis 300}}
                   {:summary {:vaultAddress "0x4"
                              :createTimeMillis 20}}]))))
    (is (= {:vault-address "0xa1"
            :equity 120.5
            :equity-raw "120.5"
            :locked-until-ms 1700}
           (vaults-endpoints/normalize-user-vault-equity
            {:vaultAddress "0xA1"
             :equity "120.5"
             :lockedUntilTimestamp "1700"})))
    (is (= [{:vault-address "0xa1"
             :equity 1
             :equity-raw "1"
             :locked-until-ms nil}]
           (vaults-endpoints/normalize-user-vault-equities
            [{:vaultAddress "0xA1"
              :equity "1"}
             {:vaultAddress " "
              :equity "2"}])))))

(deftest ws-vaults-endpoints-request-coverage-smoke-test
  (async done
    (let [fetch-calls (atom [])
          fetch-fn (fn [url init]
                     (swap! fetch-calls conj [url init])
                     (js/Promise.resolve
                      (ok-response
                       [{:apr "0.25"
                         :summary {:name "Alpha Vault"
                                   :vaultAddress "0xABc"
                                   :leader "0xDEF"
                                   :tvl "12.5"
                                   :isClosed "false"
                                   :relationship {:type "normal"}
                                   :createTimeMillis "1700"}}])))
          post-calls (atom [])
          post-info! (api-stubs/post-info-stub
                      post-calls
                      (fn [body _opts]
                        (case (get body "type")
                          "vaultSummaries" [{:summary {:name "Summary Vault"
                                                       :vaultAddress "0xA1"
                                                       :leader "0xB2"
                                                       :tvl "9.0"
                                                       :createTimeMillis 22}}]
                          "userVaultEquities" [{:vaultAddress "0xA1"
                                                :equity "120.5"
                                                :lockedUntilTimestamp "1700"}]
                          "vaultDetails" {:name "Vault Detail"
                                          :vaultAddress "0xVaUlT"
                                          :leader "0xLEADER"
                                          :portfolio [["day" {:accountValue "10"}]]
                                          :followers [{:user "0xA"}]
                                          :followersCount "2"
                                          :allowDeposits "true"}
                          "webData2" {:ok true}
                          nil)))]
      (-> (js/Promise.all
           #js [(vaults-endpoints/request-vault-index! fetch-fn "https://vaults.test/index" {:fetch-opts {:cache "no-store"}})
                (-> (vaults-endpoints/request-vault-index!
                     (fn [_url _init]
                       (js/Promise.resolve #js {:ok false
                                                :status 503}))
                     "https://vaults.test/fail"
                     {})
                    (.then (fn [_] {:status :unexpected}))
                    (.catch (fn [err]
                              {:status (aget err "status")})))
                (-> (vaults-endpoints/request-vault-index!
                     (fn [_url _init]
                       (js/Promise.resolve [{:summary {:vaultAddress "0xB1"}}]))
                     "https://vaults.test/direct"
                     {})
                    (.then count))
                (vaults-endpoints/request-vault-summaries! post-info! {})
                (vaults-endpoints/request-user-vault-equities! post-info! "0xAbC" {})
                (vaults-endpoints/request-vault-details! post-info! "0xVaUlT" {:user "0xUsEr"})
                (vaults-endpoints/request-vault-webdata2! post-info! "0xVaUlT" {})])
          (.then
           (fn [results]
             (let [results* (vec (array-seq results))]
               (is (= {:status 503} (nth results* 1)))
               (is (= 1 (nth results* 2)))
               (is (= "https://vaults.test/index" (ffirst @fetch-calls)))
               (is (= {"type" "vaultSummaries"} (ffirst @post-calls)))
               (is (= {"type" "userVaultEquities"
                       "user" "0xabc"}
                      (first (nth @post-calls 1))))
               (is (= {"type" "vaultDetails"
                       "vaultAddress" "0xvault"
                       "user" "0xuser"}
                      (first (nth @post-calls 2))))
               (is (= {"type" "webData2"
                       "user" "0xvault"}
                      (first (nth @post-calls 3))))
               (done))))
          (.catch (async-support/unexpected-error done))))))
