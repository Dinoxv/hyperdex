(ns hyperopen.portfolio.montecarlo.actions
  "Control normalization, defaults, and Nexus action handlers for the portfolio
  Monte Carlo tab.

  The controls (number of simulations, forecast horizon, bust drawdown
  threshold, return goal, and RNG seed) live in app state under
  `[:portfolio-ui :monte-carlo]`. Every handler is a pure function of state +
  arguments that returns Nexus effect descriptors (here, `:effects/save` data),
  so it can be unit-tested without a running app. Side effects are performed by
  the runtime effect interpreters, not here."
  (:require [clojure.string :as str]))

(def state-path
  "App-state path under which Monte Carlo control values live."
  [:portfolio-ui :monte-carlo])

(def sims-options
  "Selectable simulation counts (segmented control)."
  [250 1000 2500])

(def horizon-options
  "Selectable forecast horizons in days (segmented control)."
  [30 90 180 365])

(def default-controls
  "Default Monte Carlo controls. `:bust` and `:goal` are stored as whole
  percents (matching the UI); the view-model divides them by 100 before calling
  the engine. `:run-nonce` only exists to force a fresh chart animation when the
  user clicks Re-run with otherwise-identical (deterministic) inputs."
  {:sims 1000
   :horizon 90
   :bust -30
   :goal 50
   :seed 42
   :run-nonce 0})

(def control-keys
  "Controls a user can set via `set-portfolio-monte-carlo-control`."
  #{:sims :horizon :bust :goal :seed})

(defn- parse-number
  [value]
  (cond
    (number? value) (when (js/isFinite value) value)
    (string? value) (let [n (js/parseFloat (str/replace value #"[^0-9.\-]" ""))]
                      (when (js/isFinite n) n))
    :else nil))

(defn- clamp-int
  [value lo hi fallback]
  (if-let [n (parse-number value)]
    (min hi (max lo (js/Math.round n)))
    fallback))

(defn- normalize-keyword-like
  [value]
  (cond
    (keyword? value) value
    (string? value) (let [trimmed (str/trim value)]
                      (when (seq trimmed) (keyword trimmed)))
    :else nil))

(defn normalize-sims
  [value]
  (let [n (some-> (parse-number value) js/Math.round)]
    (if (some #{n} sims-options) n (:sims default-controls))))

(defn normalize-horizon
  [value]
  (let [n (some-> (parse-number value) js/Math.round)]
    (if (some #{n} horizon-options) n (:horizon default-controls))))

(defn normalize-bust
  "Bust drawdown threshold, a whole negative percent in [-95, -1]."
  [value]
  (clamp-int value -95 -1 (:bust default-controls)))

(defn normalize-goal
  "Return goal, a whole positive percent in [1, 500]."
  [value]
  (clamp-int value 1 500 (:goal default-controls)))

(defn normalize-seed
  [value]
  (clamp-int value 0 9999 (:seed default-controls)))

(defn normalize-control
  "Normalize a single control `value` for `control` key. Unknown controls return
  the value unchanged."
  [control value]
  (case control
    :sims (normalize-sims value)
    :horizon (normalize-horizon value)
    :bust (normalize-bust value)
    :goal (normalize-goal value)
    :seed (normalize-seed value)
    value))

(defn controls
  "Read the current Monte Carlo controls from `state`, filling defaults."
  [state]
  (let [stored (get-in state state-path)]
    (reduce (fn [acc k]
              (assoc acc k (normalize-control k (get stored k (get default-controls k)))))
            (select-keys default-controls [:run-nonce])
            control-keys)))

;; ---------------------------------------------------------------------------
;; Nexus action handlers (return effect descriptors only)
;; ---------------------------------------------------------------------------

(defn set-portfolio-monte-carlo-control
  "Set one Monte Carlo control (`:sims`, `:horizon`, `:bust`, `:goal`, `:seed`)."
  [_state control value]
  (let [control* (normalize-keyword-like control)]
    (when (contains? control-keys control*)
      [[:effects/save (conj state-path control*) (normalize-control control* value)]])))

(defn rerun-portfolio-monte-carlo
  "Bump the run nonce so the chart replays its reveal animation. The simulation
  is deterministic, so the numbers are unchanged unless a control differs."
  [state]
  (let [nonce (get-in state (conj state-path :run-nonce) 0)]
    [[:effects/save (conj state-path :run-nonce) (inc (or (parse-number nonce) 0))]]))
