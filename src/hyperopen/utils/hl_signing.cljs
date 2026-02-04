(ns hyperopen.utils.hl-signing
  (:require [clojure.string :as str]
            [clojure.walk :as walk]
            ["../vendor/msgpack" :as msgpack]
            ["../vendor/keccak" :as keccak]))

(defn- strip-0x [s]
  (if (and s (str/starts-with? s "0x")) (subs s 2) s))

(defn hex->bytes [hex]
  (let [h (strip-0x (or hex ""))
        padded (if (odd? (count h)) (str "0" h) h)
        len (/ (count padded) 2)
        out (js/Uint8Array. len)]
    (dotimes [i len]
      (let [byte-str (subs padded (* i 2) (+ (* i 2) 2))]
        (aset out i (js/parseInt byte-str 16))))
    out))

(defn bytes->hex [bytes]
  (let [parts (array)]
    (dotimes [i (.-length bytes)]
      (let [b (aget bytes i)
            hex (.toString b 16)]
        (.push parts (if (= 1 (count hex)) "0" ""))
        (.push parts hex)))
    (apply str parts)))

(defn- bigint-u64-bytes [n]
  (let [hex (-> n (js/BigInt.) (.toString 16))
        pad-count (max 0 (- 16 (count hex)))
        padded (str/join "" (repeat pad-count "0"))]
    (hex->bytes (str padded hex))))

(defn- clj->js-clean [x]
  (walk/postwalk
    (fn [v]
      (cond
        (keyword? v) (name v)
        (map? v) (clj->js v)
        :else v))
    x))

(defn compute-connection-id
  "Compute keccak256(msgpack(action) || vault || nonce-u64).
   Returns 0x-prefixed hex string."
  [action nonce & {:keys [vault-address] :or {vault-address nil}}]
  (let [action-js (clj->js-clean action)
        action-bytes (.encode msgpack action-js)
        vault-bytes (if (and vault-address (not (str/blank? vault-address)))
                      (hex->bytes vault-address)
                      (js/Uint8Array. 20))
        nonce-bytes (bigint-u64-bytes nonce)
        total-len (+ (.-length action-bytes) (.-length vault-bytes) (.-length nonce-bytes))
        combined (js/Uint8Array. total-len)]
    (.set combined action-bytes 0)
    (.set combined vault-bytes (.-length action-bytes))
    (.set combined nonce-bytes (+ (.-length action-bytes) (.-length vault-bytes)))
    (str "0x" (.keccak256 keccak combined))))

(defn build-typed-data [connection-id]
  {:types {:Agent [{:name "source" :type "string"}
                   {:name "connectionId" :type "bytes32"}]}
   :domain {:name "Exchange"
            :version "1"
            :chainId 1337
            :verifyingContract "0x0000000000000000000000000000000000000000"}
   :primaryType "Agent"
   :message {:source "a"
             :connectionId connection-id}})

(defn split-signature [sig]
  (let [hex (strip-0x sig)
        r (subs hex 0 64)
        s (subs hex 64 128)
        v (subs hex 128 130)]
    {:r (str "0x" r)
     :s (str "0x" s)
     :v (js/parseInt v 16)}))

(defn sign-l1-action!
  "Uses window.ethereum to sign typed data. Returns a promise resolving
   to {:connectionId :r :s :v :sig}."
  [address action nonce & {:keys [vault-address]}]
  (let [connection-id (compute-connection-id action nonce :vault-address vault-address)
        typed-data (build-typed-data connection-id)
        payload (clj->js typed-data)
        msg (js/JSON.stringify payload)]
    (-> (.request (.-ethereum js/window)
                  (clj->js {:method "eth_signTypedData_v4"
                            :params [address msg]}))
        (.then (fn [sig]
                 (let [parts (split-signature sig)]
                   (clj->js (merge {:connectionId connection-id
                                    :sig sig}
                                   parts))))))))
