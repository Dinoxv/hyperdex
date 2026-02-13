(ns hyperopen.runtime.validation
  (:require [goog.object :as gobj]
            [hyperopen.schema.contracts :as contracts]))

(defn validation-enabled?
  []
  (contracts/validation-enabled?))

(def ^:private store-state-watch-key
  ::state-schema-validation)

(defn- parse-fixed-arity-key
  [k]
  (when-let [[_ arity-text] (re-matches #"cljs\$lang\$arity\$(\d+)" (str k))]
    (let [arity (js/parseInt arity-text 10)]
      (when-not (js/isNaN arity)
        arity))))

(defn- fixed-arities
  [handler]
  (->> (array-seq (js/Object.keys handler))
       (keep parse-fixed-arity-key)
       set))

(defn- variadic-handler?
  [handler]
  (or (fn? (gobj/get handler "cljs$lang$arity$variadic"))
      (fn? (gobj/get handler "cljs$lang$applyTo"))))

(defn- dispatch-arity-contract
  [handler fixed-leading-args]
  (let [dispatch-arities (->> (fixed-arities handler)
                              (map #(- % fixed-leading-args))
                              (filter #(>= % 0))
                              sort
                              vec)]
    (when (seq dispatch-arities)
      {:fixed (set dispatch-arities)
       :minimum (first dispatch-arities)
       :maximum (last dispatch-arities)
       :variadic? (variadic-handler? handler)})))

(defn- valid-dispatch-arity?
  [{:keys [fixed minimum variadic?]} arg-count]
  (if variadic?
    (>= arg-count minimum)
    (contains? fixed arg-count)))

(defn- expected-arity-text
  [{:keys [fixed minimum variadic?]}]
  (if variadic?
    (str ">=" minimum)
    (pr-str (sort fixed))))

(defn- assert-dispatch-arity!
  [label id arity-contract args context]
  (let [arg-count (count args)]
    (when (and arity-contract
               (not (valid-dispatch-arity? arity-contract arg-count)))
      (throw (js/Error.
              (str label " schema validation failed for " id ". "
                   "expected-arity=" (expected-arity-text arity-contract)
                   " actual-arity=" arg-count
                   " context=" (pr-str context)
                   " args=" (pr-str args)))))))

(defn wrap-action-handler
  [action-id handler]
  (if-not (validation-enabled?)
    handler
    (let [arity-contract (dispatch-arity-contract handler 1)]
      (fn [state & args]
        (assert-dispatch-arity! "action payload"
                                action-id
                                arity-contract
                                args
                                {:phase :dispatch})
        (contracts/assert-action-args! action-id (vec args) {:phase :dispatch})
        (let [effects (apply handler state args)]
          (contracts/assert-emitted-effects!
           effects
           {:phase :action-emission
            :action-id action-id})
          effects)))))

(defn wrap-effect-handler
  [effect-id handler]
  (if-not (validation-enabled?)
    handler
    (let [arity-contract (dispatch-arity-contract handler 2)]
      (fn [ctx store & args]
        (assert-dispatch-arity! "effect request"
                                effect-id
                                arity-contract
                                args
                                {:phase :dispatch})
        (contracts/assert-effect-args! effect-id (vec args) {:phase :dispatch})
        (apply handler ctx store args)))))

(defn install-store-state-validation!
  [store]
  (when (validation-enabled?)
    (contracts/assert-app-state!
     @store
     {:phase :bootstrap
      :boundary :app-store})
    (remove-watch store store-state-watch-key)
    (add-watch store
               store-state-watch-key
               (fn [_ _ _ new-state]
                 (contracts/assert-app-state!
                  new-state
                  {:phase :transition
                   :boundary :app-store}))))
  store)
