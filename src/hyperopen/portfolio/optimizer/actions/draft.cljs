(ns hyperopen.portfolio.optimizer.actions.draft
  (:require [hyperopen.portfolio.optimizer.actions.common :as common]
            [hyperopen.portfolio.optimizer.actions.run :as run-actions]
            [hyperopen.portfolio.optimizer.contracts :as contracts]))

(def objective-models
  {:minimum-variance {:kind :minimum-variance}
   :max-sharpe {:kind :max-sharpe}
   :target-volatility {:kind :target-volatility
                       :target-volatility 0.2}
   :target-return {:kind :target-return
                   :target-return 0.15}})

(def return-models
  {:historical-mean {:kind :historical-mean}
   :ew-mean {:kind :ew-mean
             :alpha 0.015159678336035098}
   :black-litterman {:kind :black-litterman
                     :views []}})

(def risk-models
  {:diagonal-shrink {:kind :diagonal-shrink}
   :ledoit-wolf {:kind :diagonal-shrink}
   :ledoit-wolf-dense {:kind :ledoit-wolf-dense}
   :sample-covariance {:kind :sample-covariance}
   :mixed-frequency {:kind :mixed-frequency}})

(def setup-presets
  {:conservative {:objective {:kind :minimum-variance}
                  :return-model {:kind :historical-mean}}
   :risk-adjusted {:objective {:kind :max-sharpe}
                   :return-model {:kind :historical-mean}}
   :use-my-views {:objective {:kind :max-sharpe}
                  :return-model {:kind :black-litterman
                                 :views []}}})

(def objective-menu-options
  {:minimum-volatility {:objective {:kind :minimum-variance}}
   :max-sharpe {:objective {:kind :max-sharpe}}
   :target-volatility {:objective {:kind :target-volatility
                                   :target-volatility 0.12}}
   :maximum-return {:objective {:kind :target-return
                                :target-return 0.3}}
   :use-my-views {:objective {:kind :max-sharpe}
                  :return-model-kind :black-litterman}})

(def numeric-constraint-keys
  #{:max-asset-weight
    :gross-max
    :net-min
    :net-max
    :dust-usdc
    :max-turnover
    :rebalance-tolerance})

(def boolean-constraint-keys
  #{:long-only?})

(def numeric-objective-parameter-keys
  #{:target-return
    :target-volatility})

(def numeric-execution-assumption-keys
  #{:fallback-slippage-bps
    :manual-capital-usdc})

(def keyword-execution-assumption-keys
  #{:default-order-type
    :fee-mode})

(def instrument-filter-keys
  #{:allowlist
    :blocklist})

(def numeric-asset-override-keys
  #{:max-weight
    :perp-max-weight})

(def boolean-asset-override-keys
  #{:held-lock?})

(defn- set-draft-model
  [path models value]
  (if-let [model (get models (common/normalize-keyword-like value))]
    (common/save-draft-path-values [[path model]])
    []))

(defn- current-objective-menu-option
  [state]
  (let [objective-kind (get-in state (conj contracts/draft-objective-path :kind))
        return-model-kind (get-in state (conj contracts/draft-return-model-path :kind))]
    (cond
      (= :black-litterman return-model-kind) :use-my-views
      (= :minimum-variance objective-kind) :minimum-volatility
      (= :target-volatility objective-kind) :target-volatility
      (= :target-return objective-kind) :maximum-return
      :else :max-sharpe)))

(defn- objective-menu-model
  [value]
  (get objective-menu-options (common/normalize-keyword-like value)))

(defn- objective-menu-model-for-state
  [state value]
  (when-let [model (objective-menu-model value)]
    (if (= :black-litterman (:return-model-kind model))
      (-> model
          (dissoc :return-model-kind)
          (assoc :return-model
                 {:kind :black-litterman
                  :views (vec (or (get-in state contracts/draft-return-model-views-path)
                                  []))}))
      model)))

(defn open-portfolio-optimizer-objective-menu
  [state]
  [[:effects/save-many
    [[contracts/ui-objective-menu-open-path true]
     [contracts/ui-objective-menu-selection-path
      (current-objective-menu-option state)]]]])

(defn close-portfolio-optimizer-objective-menu
  [_state]
  [[:effects/save-many
    [[contracts/ui-objective-menu-open-path false]
     [contracts/ui-objective-menu-selection-path nil]]]])

(defn handle-portfolio-optimizer-objective-menu-keydown
  [state key]
  (if (= "Escape" (some-> key str))
    (close-portfolio-optimizer-objective-menu state)
    []))

(defn select-portfolio-optimizer-objective-menu-option
  [_state option-key]
  (if (objective-menu-model option-key)
    [[:effects/save
      contracts/ui-objective-menu-selection-path
      (common/normalize-keyword-like option-key)]]
    []))

(defn- state-after-save-effect
  [state effect]
  (case (first effect)
    :effects/save
    (assoc-in state (second effect) (nth effect 2))

    :effects/save-many
    (reduce (fn [state* [path value]]
              (assoc-in state* path value))
            state
            (second effect))

    state))

(defn- projected-state-after-save-effects
  [state effects]
  (reduce state-after-save-effect state effects))

(defn apply-portfolio-optimizer-objective-menu-selection-and-run
  [state]
  (let [selection (or (get-in state contracts/ui-objective-menu-selection-path)
                      (current-objective-menu-option state))]
    (if-let [{:keys [objective return-model]} (objective-menu-model-for-state state selection)]
      (let [path-values (cond-> [[contracts/draft-objective-path objective]]
                          return-model
                          (conj [contracts/draft-return-model-path return-model])

                          :always
                          (conj [contracts/ui-objective-menu-open-path false]
                                [contracts/ui-objective-menu-selection-path nil]))
            effects (common/save-draft-path-values path-values)
            state* (projected-state-after-save-effects state effects)]
        (into effects
              (run-actions/run-portfolio-optimizer-from-draft state*)))
      [])))

(defn set-portfolio-optimizer-objective-kind
  [_state kind]
  (set-draft-model contracts/draft-objective-path
                   objective-models
                   kind))

(defn set-portfolio-optimizer-return-model-kind
  [_state kind]
  (set-draft-model contracts/draft-return-model-path
                   return-models
                   kind))

(defn set-portfolio-optimizer-risk-model-kind
  [_state kind]
  (set-draft-model contracts/draft-risk-model-path
                   risk-models
                   kind))

(defn apply-portfolio-optimizer-setup-preset
  [_state preset]
  (if-let [{:keys [objective return-model]} (get setup-presets
                                                 (common/normalize-keyword-like preset))]
    (common/save-draft-path-values
     [[contracts/draft-objective-path objective]
      [contracts/draft-return-model-path return-model]])
    []))

(defn set-portfolio-optimizer-constraint
  [_state constraint-key value]
  (let [constraint-key* (common/normalize-keyword-like constraint-key)
        value* (cond
                 (contains? numeric-constraint-keys constraint-key*)
                 (common/parse-number-value value)

                 (contains? boolean-constraint-keys constraint-key*)
                 (common/parse-boolean-value value)

                 :else nil)]
    (if (some? value*)
      (common/save-draft-path-values
       [[(conj contracts/draft-constraints-path constraint-key*) value*]])
      [])))

(defn set-portfolio-optimizer-objective-parameter
  [_state parameter-key value]
  (let [parameter-key* (common/normalize-keyword-like parameter-key)
        value* (when (contains? numeric-objective-parameter-keys parameter-key*)
                 (common/parse-number-value value))]
    (if (some? value*)
      (common/save-draft-path-values
       [[(conj contracts/draft-objective-path parameter-key*) value*]])
      [])))

(defn set-portfolio-optimizer-execution-assumption
  [_state assumption-key value]
  (let [assumption-key* (common/normalize-keyword-like assumption-key)
        manual-capital-clear? (and (= :manual-capital-usdc assumption-key*)
                                   (nil? (common/non-blank-text value)))
        value* (cond
                 manual-capital-clear?
                 nil

                 (contains? numeric-execution-assumption-keys assumption-key*)
                 (common/parse-number-value value)

                 (contains? keyword-execution-assumption-keys assumption-key*)
                 (common/normalize-keyword-like value)

                 :else nil)]
    (if (or (some? value*)
            manual-capital-clear?)
      (common/save-draft-path-values
       [[(conj contracts/draft-execution-assumptions-path assumption-key*) value*]])
      [])))

(defn set-portfolio-optimizer-instrument-filter
  [state filter-key instrument-id enabled?]
  (let [filter-key* (common/normalize-keyword-like filter-key)
        instrument-id* (common/non-blank-text instrument-id)
        enabled?* (common/parse-boolean-value enabled?)]
    (if (and (contains? instrument-filter-keys filter-key*)
             instrument-id*
             (some? enabled?*))
      (common/save-draft-path-values
       [[(conj contracts/draft-constraints-path filter-key*)
         (common/set-membership
          (common/constraint-list state filter-key*)
          instrument-id*
          enabled?*)]])
      [])))

(defn set-portfolio-optimizer-asset-override
  [state override-key instrument-id value]
  (let [override-key* (common/normalize-keyword-like override-key)
        instrument-id* (common/non-blank-text instrument-id)
        numeric-value (when (contains? numeric-asset-override-keys override-key*)
                        (common/parse-number-value value))
        boolean-value (when (contains? boolean-asset-override-keys override-key*)
                        (common/parse-boolean-value value))]
    (cond
      (and instrument-id*
           (= :max-weight override-key*)
           (some? numeric-value))
      (common/save-draft-path-values
       [[(conj contracts/draft-constraints-path
               :asset-overrides
               instrument-id*
               :max-weight)
         numeric-value]])

      (and instrument-id*
           (= :perp-max-weight override-key*)
           (= :perp (common/instrument-market-type state instrument-id*))
           (some? numeric-value))
      (common/save-draft-path-values
       [[(conj contracts/draft-constraints-path
               :perp-leverage
               instrument-id*
               :max-weight)
         numeric-value]])

      (and instrument-id*
           (= :held-lock? override-key*)
           (some? boolean-value))
      (common/save-draft-path-values
       [[(conj contracts/draft-constraints-path :held-locks)
         (common/set-membership
          (common/constraint-list state :held-locks)
          instrument-id*
          boolean-value)]])

      :else [])))
