(ns hyperopen.views.portfolio.montecarlo.controls
  "Control strip for the Monte Carlo tab: method, simulation count, horizon, bust
  and goal thresholds, RNG seed, and a Re-run button. Every control dispatches a
  pure action; the view-model re-runs the (cached) engine when a value changes.

  The method toggle picks between `:shuffle` (the QuantStats sequence-risk
  reordering, which has no forecast horizon) and `:bootstrap` (the forward
  forecast). The Horizon field is therefore shown only in `:bootstrap` mode.

  The dispatched action ids and data-role prefix arrive via the model's
  `:chrome`, so the portfolio and vault surfaces share this strip while keeping
  independent control state."
  (:require [clojure.string :as str]))

(defn- method-label
  [m]
  (case m :shuffle "Sequence risk" :bootstrap "Forecast" (name m)))

(defn- segmented
  [{:keys [control value options fmt set-action data-role-prefix]}]
  [:div {:class ["mc-seg"]
         :data-role (str data-role-prefix "-seg-" (name control))}
   (for [opt options]
     ^{:key (str opt)}
     [:button {:type "button"
               :class (into ["mc-seg-btn"]
                            (when (= opt value) ["mc-seg-btn-on"]))
               :aria-pressed (= opt value)
               :on {:click [[set-action control opt]]}}
      (fmt opt)])])

(defn- stepper
  [{:keys [control value suffix step set-action data-role-prefix]}]
  [:div {:class ["mc-num-input"]
         :data-role (str data-role-prefix "-num-" (name control))}
   [:input {:type "text"
            :class ["mc-num-field"]
            :value (str value)
            :aria-label (name control)
            :on {:change [[set-action control [:event.target/value]]]}}]
   (when suffix
     [:span {:class ["mc-suffix"]} suffix])
   [:div {:class ["mc-stepper"]}
    [:button {:type "button"
              :aria-label (str "increase " (name control))
              :on {:click [[set-action control (+ value step)]]}}
     "▲"]
    [:button {:type "button"
              :aria-label (str "decrease " (name control))
              :on {:click [[set-action control (- value step)]]}}
     "▼"]]])

(defn- field
  [label control-node]
  [:div {:class ["mc-ctrl"]}
   [:label {:class ["mc-ctrl-label"]} label]
   control-node])

(defn controls-bar
  [{:keys [controls sims-options horizon-options method-options sample-size chrome]}]
  (let [{:keys [method sims horizon bust goal seed]} controls
        {:keys [set-control-action rerun-action data-role-prefix]} chrome
        base {:set-action set-control-action :data-role-prefix data-role-prefix}
        shuffle? (= method :shuffle)
        eff-horizon (min horizon (max 1 sample-size))]
    [:div {:class ["mc-card" "mc-controls"]
           :data-role (str data-role-prefix "-controls")}
     (field "Method"
            (segmented (merge base {:control :method
                                    :value method
                                    :options method-options
                                    :fmt method-label})))
     (field "Simulations"
            (segmented (merge base {:control :sims
                                    :value sims
                                    :options sims-options
                                    :fmt (fn [v] (.toLocaleString v))})))
     (when-not shuffle?
       (field "Horizon"
              (segmented (merge base {:control :horizon
                                      :value horizon
                                      :options horizon-options
                                      :fmt (fn [v] (str v "d"))}))))
     (field "Bust threshold (drawdown)"
            (stepper (merge base {:control :bust :value bust :suffix "%" :step 5})))
     (field "Goal (total return)"
            (stepper (merge base {:control :goal :value goal :suffix "%" :step 10})))
     (field "Seed"
            (stepper (merge base {:control :seed :value seed :step 1})))
     [:div {:class ["mc-run"]}
      [:span {:class ["mc-run-status"]
              :data-role (str data-role-prefix "-run-status")}
       (if shuffle?
         [:span [:b (.toLocaleString (max 0 sample-size))] " trading days · reshuffled"]
         [:span [:b (.toLocaleString sims)] (str " paths · " eff-horizon "d")])]
      [:button {:type "button"
                :class ["mc-run-btn"]
                :data-role (str data-role-prefix "-rerun")
                :on {:click [[rerun-action]]}}
       (str/join " " ["↻" "Re-run"])]]]))
