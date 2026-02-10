(ns hyperopen.wallet.agent-session-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hyperopen.wallet.agent-session :as agent-session]))

(defn- fake-storage []
  (let [store (atom {})]
    #js {:setItem (fn [k v] (swap! store assoc (str k) (str v)))
         :getItem (fn [k] (get @store (str k)))
         :removeItem (fn [k] (swap! store dissoc (str k)))
         :clear (fn [] (reset! store {}))}))

(deftest default-agent-state-shape-test
  (let [agent (agent-session/default-agent-state)]
    (is (= :not-ready (:status agent)))
    (is (= :session (:storage-mode agent)))
    (is (nil? (:agent-address agent)))
    (is (nil? (:last-approved-at agent)))
    (is (nil? (:error agent)))
    (is (nil? (:nonce-cursor agent)))))

(deftest storage-key-normalizes-wallet-address-test
  (is (= "hyperopen:agent-session:v1:0xabc123"
         (agent-session/session-storage-key "0xAbC123"))))

(deftest build-approve-agent-action-adds-protocol-fields-test
  (let [action (agent-session/build-approve-agent-action
                "0x1234567890abcdef1234567890abcdef12345678"
                1700000001111)]
    (is (= "approveAgent" (:type action)))
    (is (= "0x1234567890abcdef1234567890abcdef12345678" (:agentAddress action)))
    (is (= 1700000001111 (:nonce action)))
    (is (= "Mainnet" (:hyperliquidChain action)))
    (is (= "0x66eee" (:signatureChainId action)))
    (is (nil? (:agentName action)))))

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
