(ns hyperopen.schema.contracts
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]))

(defn validation-enabled?
  []
  ^boolean goog.DEBUG)

(defn- non-empty-string?
  [value]
  (and (string? value)
       (seq (str/trim value))))

(defn- keyword-path?
  [path]
  (and (vector? path)
       (every? keyword? path)))

(s/def ::any-args vector?)
(s/def ::non-empty-string non-empty-string?)
(s/def ::state-path keyword-path?)
(s/def ::path-value (s/tuple ::state-path any?))
(s/def ::path-values (s/and vector?
                            (s/coll-of ::path-value :kind vector?)))

(s/def ::market-key ::non-empty-string)
(s/def ::icon-status #{:loaded :missing})
(s/def ::asset-icon-status (s/keys :req-un [::market-key ::icon-status]))

(s/def ::action map?)
(s/def ::nonce (s/and integer?
                      #(>= % 0)))
(s/def ::r ::non-empty-string)
(s/def ::s ::non-empty-string)
(s/def ::v integer?)
(s/def ::signature (s/keys :req-un [::r ::s ::v]))
(s/def ::signed-exchange-payload
  (s/keys :req-un [::action ::nonce ::signature]))

(s/def ::exchange-response map?)

(s/def ::channel ::non-empty-string)
(s/def ::provider-message
  (s/keys :req-un [::channel]))

(s/def ::save-args (s/tuple ::state-path any?))
(s/def ::save-many-args (s/tuple ::path-values))
(s/def ::storage-args (s/tuple ::non-empty-string any?))
(s/def ::queue-asset-icon-status-args (s/tuple ::asset-icon-status))
(s/def ::path-args (s/tuple ::non-empty-string))
(s/def ::coin-args (s/tuple ::non-empty-string))
(s/def ::address-args (s/tuple ::non-empty-string))
(s/def ::optional-address-args (s/tuple (s/nilable ::non-empty-string)))
(s/def ::set-agent-storage-mode-args (s/tuple keyword?))
(s/def ::enable-agent-trading-args (s/tuple map?))
(s/def ::api-submit-request (s/keys :req-un [::action]))
(s/def ::api-submit-order-args (s/tuple ::api-submit-request))
(s/def ::api-cancel-order-args (s/tuple ::api-submit-request))

(defn- fetch-asset-selector-markets-args?
  [args]
  (or (empty? args)
      (and (= 1 (count args))
           (map? (first args)))))

(s/def ::fetch-asset-selector-markets-args fetch-asset-selector-markets-args?)

(s/def ::group #{:market_data :orders_oms :all})
(s/def ::source keyword?)
(s/def ::ws-reset-request
  (s/keys :opt-un [::group ::source]))
(s/def ::ws-reset-subscriptions-args (s/tuple ::ws-reset-request))
(s/def ::no-args empty?)

(s/def ::action-id (s/and keyword?
                          #(= "actions" (namespace %))))
(s/def ::effect-id (s/and keyword?
                          #(= "effects" (namespace %))))

(def ^:private action-args-spec-by-id
  {:actions/select-asset (s/tuple (s/or :coin ::non-empty-string
                                        :market map?))
   :actions/update-order-form (s/tuple ::state-path any?)
   :actions/navigate (s/or :path (s/tuple ::non-empty-string)
                           :path-and-opts (s/tuple ::non-empty-string map?))
   :actions/set-agent-storage-mode (s/tuple keyword?)
   :actions/set-show-surface-freshness-cues (s/tuple boolean?)})

(def ^:private effect-args-spec-by-id
  {:effects/save ::save-args
   :effects/save-many ::save-many-args
   :effects/local-storage-set ::storage-args
   :effects/local-storage-set-json ::storage-args
   :effects/queue-asset-icon-status ::queue-asset-icon-status-args
   :effects/push-state ::path-args
   :effects/replace-state ::path-args
   :effects/init-websocket ::no-args
   :effects/subscribe-active-asset ::coin-args
   :effects/subscribe-orderbook ::coin-args
   :effects/subscribe-trades ::coin-args
   :effects/subscribe-webdata2 ::address-args
   :effects/unsubscribe-active-asset ::coin-args
   :effects/unsubscribe-orderbook ::coin-args
   :effects/unsubscribe-trades ::coin-args
   :effects/unsubscribe-webdata2 ::address-args
   :effects/connect-wallet ::no-args
   :effects/disconnect-wallet ::no-args
   :effects/enable-agent-trading ::enable-agent-trading-args
   :effects/set-agent-storage-mode ::set-agent-storage-mode-args
   :effects/copy-wallet-address ::optional-address-args
   :effects/reconnect-websocket ::no-args
   :effects/refresh-websocket-health ::no-args
   :effects/confirm-ws-diagnostics-reveal ::no-args
   :effects/copy-websocket-diagnostics ::no-args
   :effects/ws-reset-subscriptions ::ws-reset-subscriptions-args
   :effects/fetch-asset-selector-markets ::fetch-asset-selector-markets-args
   :effects/api-submit-order ::api-submit-order-args
   :effects/api-cancel-order ::api-cancel-order-args
   :effects/api-load-user-data ::address-args})

(s/def ::coin ::non-empty-string)
(s/def ::symbol ::non-empty-string)
(s/def ::active-asset (s/nilable ::non-empty-string))
(s/def ::active-market
  (s/nilable
   (s/keys :req-un [::coin ::symbol])))

(s/def ::asset-selector-state
  (s/and map?
         #(vector? (:markets %))
         #(map? (:market-by-key %))
         #(set? (:favorites %))
         #(set? (:loaded-icons %))
         #(set? (:missing-icons %))))

(s/def ::wallet-state
  (s/and map?
         #(map? (:agent %))))

(s/def ::websocket-state map?)
(s/def ::websocket-ui-state map?)
(s/def ::router-state
  (s/and map?
         #(string? (:path %))))

(s/def ::app-state
  (s/and map?
         #(contains? % :active-asset)
         #(contains? % :active-market)
         #(contains? % :asset-selector)
         #(contains? % :wallet)
         #(contains? % :websocket)
         #(contains? % :websocket-ui)
         #(contains? % :router)
         #(contains? % :order-form)
         #(s/valid? ::active-asset (:active-asset %))
         #(s/valid? ::active-market (:active-market %))
         #(s/valid? ::asset-selector-state (:asset-selector %))
         #(s/valid? ::wallet-state (:wallet %))
         #(s/valid? ::websocket-state (:websocket %))
         #(s/valid? ::websocket-ui-state (:websocket-ui %))
         #(s/valid? ::router-state (:router %))
         #(map? (:order-form %))))

(defn- assertion-error
  [label spec value context]
  (js/Error.
   (str label " schema validation failed. "
        "context=" (pr-str context)
        " value=" (pr-str value)
        " explain=" (pr-str (s/explain-data spec value)))))

(defn- assert-spec!
  [label spec value context]
  (when-not (s/valid? spec value)
    (throw (assertion-error label spec value context)))
  value)

(defn assert-action-args!
  [action-id args context]
  (assert-spec! "action payload"
                ::action-id
                action-id
                context)
  (assert-spec! "action payload"
                (get action-args-spec-by-id action-id ::any-args)
                args
                (assoc context :action-id action-id)))

(defn assert-effect-args!
  [effect-id args context]
  (assert-spec! "effect request"
                ::effect-id
                effect-id
                context)
  (assert-spec! "effect request"
                (get effect-args-spec-by-id effect-id ::any-args)
                args
                (assoc context :effect-id effect-id)))

(defn assert-effect-call!
  [effect context]
  (when-not (and (vector? effect)
                 (seq effect))
    (throw (js/Error.
            (str "effect request schema validation failed. "
                 "context=" (pr-str context)
                 " value=" (pr-str effect)))))
  (let [effect-id (first effect)
        args (subvec effect 1)]
    (assert-effect-args! effect-id args context)
    effect))

(defn assert-emitted-effects!
  [effects context]
  (when-not (sequential? effects)
    (throw (js/Error.
            (str "effect request schema validation failed. "
                 "context=" (pr-str context)
                 " value=" (pr-str effects)))))
  (doseq [[idx effect] (map-indexed vector effects)]
    (assert-effect-call! effect (assoc context :effect-index idx)))
  effects)

(defn assert-app-state!
  [state context]
  (assert-spec! "app state" ::app-state state context))

(defn assert-provider-message!
  [provider-message context]
  (assert-spec! "provider payload" ::provider-message provider-message context))

(defn assert-signed-exchange-payload!
  [payload context]
  (assert-spec! "exchange payload" ::signed-exchange-payload payload context))

(defn assert-exchange-response!
  [payload context]
  (assert-spec! "exchange payload" ::exchange-response payload context))
