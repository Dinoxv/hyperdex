(ns hyperopen.wallet.agent-session-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.wallet.agent-session :as agent-session]))

(defn- fake-storage []
  (let [store (atom {})]
    #js {:setItem (fn [k v] (swap! store assoc (str k) (str v)))
         :getItem (fn [k] (get @store (str k)))
         :removeItem (fn [k] (swap! store dissoc (str k)))
         :clear (fn [] (reset! store {}))}))

(defn- with-test-local-storage [f]
  (let [original-local-storage (.-localStorage js/globalThis)
        storage (fake-storage)]
    (set! (.-localStorage js/globalThis) storage)
    (try
      (f storage)
      (finally
        (set! (.-localStorage js/globalThis) original-local-storage)))))

(deftest default-agent-state-shape-test
  (let [agent (agent-session/default-agent-state)]
    (is (= :not-ready (:status agent)))
    (is (= :local (:storage-mode agent)))
    (is (nil? (:agent-address agent)))
    (is (nil? (:last-approved-at agent)))
    (is (nil? (:error agent)))
    (is (nil? (:nonce-cursor agent)))))

(deftest storage-key-normalizes-wallet-address-test
  (is (= "hyperopen:agent-session:v1:0xabc123"
         (agent-session/session-storage-key "0xAbC123"))))

(deftest storage-mode-preference-roundtrip-and-normalization-test
  (with-test-local-storage
    (fn [storage]
      (is (= :local (agent-session/load-storage-mode-preference)))
      (is (true? (agent-session/persist-storage-mode-preference! :local)))
      (is (= "local" (.getItem storage "hyperopen:agent-storage-mode:v1")))
      (is (= :local (agent-session/load-storage-mode-preference)))
      (.setItem storage "hyperopen:agent-storage-mode:v1" "SESSION")
      (is (= :session (agent-session/load-storage-mode-preference)))
      (.setItem storage "hyperopen:agent-storage-mode:v1" "unknown")
      (is (= :local (agent-session/load-storage-mode-preference))))))

(deftest build-approve-agent-action-adds-protocol-fields-test
  (let [action (agent-session/build-approve-agent-action
                "0x1234567890abcdef1234567890abcdef12345678"
                1700000001111)]
    (is (= "approveAgent" (:type action)))
    (is (= "0x1234567890abcdef1234567890abcdef12345678" (:agentAddress action)))
    (is (= 1700000001111 (:nonce action)))
    (is (= "Mainnet" (:hyperliquidChain action)))
    (is (= "0xa4b1" (:signatureChainId action)))
    (is (nil? (:agentName action)))))

(deftest build-approve-agent-action-uses-testnet-chain-id-when-requested-test
  (let [action (agent-session/build-approve-agent-action
                "0x1234567890abcdef1234567890abcdef12345678"
                1700000001111
                :is-mainnet false)]
    (is (= "Testnet" (:hyperliquidChain action)))
    (is (= "0x66eee" (:signatureChainId action)))))

(deftest create-agent-credentials-generates-hex-keypair-test
  (let [{:keys [private-key agent-address]} (agent-session/create-agent-credentials!)]
    (is (re-matches #"0x[0-9a-f]{64}" private-key))
    (is (re-matches #"0x[0-9a-f]{40}" agent-address))))

(deftest persist-load-and-clear-agent-session-test
  (let [storage (fake-storage)
        wallet-address "0xabc123"
        session {:agent-address "0x9999999999999999999999999999999999999999"
                 :private-key "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                 :last-approved-at 1700000002222
                 :nonce-cursor 1700000002222}]
    (agent-session/persist-agent-session! storage wallet-address session)
    (is (= session
           (agent-session/load-agent-session storage wallet-address)))
    (agent-session/clear-agent-session! storage wallet-address)
    (is (nil? (agent-session/load-agent-session storage wallet-address)))))
