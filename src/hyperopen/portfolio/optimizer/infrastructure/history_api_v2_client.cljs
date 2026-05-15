(ns hyperopen.portfolio.optimizer.infrastructure.history-api-v2-client
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.history-loader.api-v2 :as api-v2]
            [hyperopen.portfolio.optimizer.coercion :as coercion]))

(def default-base-url
  "https://price-history.hyperopen.xyz")

(defn- normalize-base-url
  [base-url]
  (str/replace (or (coercion/non-blank-text base-url)
                   default-base-url)
               #"/+$"
               ""))

(defn- request-id-value
  [request-id]
  (cond
    (fn? request-id) (request-id)
    (some? request-id) request-id
    :else nil))

(defn- keyword-name
  [value fallback]
  (cond
    (keyword? value) (name value)
    (string? value) value
    :else fallback))

(defn- proxy-policy-wire
  [proxy-policy]
  (str/replace (keyword-name proxy-policy "approved-proxy-allowed") #"-" "_"))

(defn- interval-wire
  [interval]
  (keyword-name interval "1d"))

(defn- response-json!
  [response]
  (if (and response (fn? (.-json response)))
    (.json response)
    (js/Promise.resolve #js {})))

(defn- parsed-response!
  [normalizer response]
  (-> (response-json! response)
      (.then (fn [payload]
               (let [body (normalizer (js->clj payload))]
                 {:response response
                  :body body})))))

(defn- error-from-response
  [response body]
  (let [status (or (some-> response .-status) 0)
        message (or (:message body)
                    (:error body)
                    (str "Optimizer history API failed with HTTP " status))
        err (js/Error. message)]
    (aset err "status" status)
    (aset err "payload" (clj->js body))
    (aset err "requestId" (:request-id body))
    (aset err "contractVersion" (:contract-version body))
    err))

(defn- validate-contract!
  [body]
  (if (= api-v2/contract-version (:contract-version body))
    body
    (let [err (js/Error. "Unexpected optimizer history API contract version.")]
      (aset err "status" :invalid-contract)
      (aset err "requestId" (:request-id body))
      (aset err "contractVersion" (:contract-version body))
      (throw err))))

(defn- request-json!
  [fetch-fn url init normalizer]
  (let [fetch-fn* (or fetch-fn js/fetch)]
    (-> (fetch-fn* url (clj->js init))
        (.then (partial parsed-response! normalizer))
        (.then (fn [{:keys [response body]}]
                 (if (and response
                          (some? (.-ok response))
                          (false? (.-ok response)))
                   (js/Promise.reject (error-from-response response body))
                   (validate-contract! body)))))))

(defn request-instruments!
  [{:keys [fetch-fn base-url request-id]}]
  (let [rid (request-id-value request-id)
        headers (cond-> {}
                  rid (assoc "x-request-id" rid))]
    (request-json! fetch-fn
                   (str (normalize-base-url base-url)
                        "/v1/optimizer/instruments")
                   {:method "GET"
                    :headers headers}
                   api-v2/normalize-api-map)))

(defn- api-instrument-row
  [instrument]
  (let [local-id (coercion/non-blank-text (:instrument-id instrument))
        backend-id (coercion/non-blank-text
                    (:optimizer-history/instrument-id instrument))]
    (when (and local-id backend-id)
      {:client_instrument_id local-id
       :instrument_id backend-id})))

(defn- history-body
  [{:keys [proxy-policy include-aligned-returns?]} request]
  {:lookback_days (or (:bars request) 365)
   :interval (interval-wire (:interval request))
   :proxy_policy (proxy-policy-wire proxy-policy)
   :include_aligned_returns (true? include-aligned-returns?)
   :instruments (mapv identity (keep api-instrument-row (:universe request)))})

(defn request-history-bundle!
  [{:keys [fetch-fn base-url request-id] :as deps} request]
  (let [rid (request-id-value request-id)]
    (request-json! fetch-fn
                   (str (normalize-base-url base-url)
                        "/v1/optimizer/history-bundle")
                   {:method "POST"
                    :headers (cond-> {"content-type" "application/json"}
                               rid (assoc "x-request-id" rid))
                    :body (js/JSON.stringify
                           (clj->js (history-body deps request)))}
                   api-v2/normalize-history-body)))
