(ns hyperopen.views.portfolio.montecarlo.controls
  "Control strip for the Monte Carlo tab: simulation count, horizon, bust and
  goal thresholds, RNG seed, and a Re-run button. Every control dispatches a
  pure action; the view-model re-runs the (cached) engine when a value changes."
  (:require [clojure.string :as str]))

(defn- segmented
  [{:keys [control value options fmt]}]
  [:div {:class ["mc-seg"]
         :data-role (str "portfolio-monte-carlo-seg-" (name control))}
   (for [opt options]
     ^{:key (str opt)}
     [:button {:type "button"
               :class (into ["mc-seg-btn"]
                            (when (= opt value) ["mc-seg-btn-on"]))
               :aria-pressed (= opt value)
               :on {:click [[:actions/set-portfolio-monte-carlo-control control opt]]}}
      (fmt opt)])])

(defn- stepper
  [{:keys [control value suffix step]}]
  [:div {:class ["mc-num-input"]
         :data-role (str "portfolio-monte-carlo-num-" (name control))}
   [:input {:type "text"
            :class ["mc-num-field"]
            :value (str value)
            :aria-label (name control)
            :on {:change [[:actions/set-portfolio-monte-carlo-control control [:event.target/value]]]}}]
   (when suffix
     [:span {:class ["mc-suffix"]} suffix])
   [:div {:class ["mc-stepper"]}
    [:button {:type "button"
              :aria-label (str "increase " (name control))
              :on {:click [[:actions/set-portfolio-monte-carlo-control control (+ value step)]]}}
     "▲"]
    [:button {:type "button"
              :aria-label (str "decrease " (name control))
              :on {:click [[:actions/set-portfolio-monte-carlo-control control (- value step)]]}}
     "▼"]]])

(defn- field
  [label control-node]
  [:div {:class ["mc-ctrl"]}
   [:label {:class ["mc-ctrl-label"]} label]
   control-node])

(defn controls-bar
  [{:keys [controls sims-options horizon-options]}]
  (let [{:keys [sims horizon bust goal seed]} controls]
    [:div {:class ["mc-card" "mc-controls"]
           :data-role "portfolio-monte-carlo-controls"}
     (field "Simulations"
            (segmented {:control :sims
                        :value sims
                        :options sims-options
                        :fmt (fn [v] (.toLocaleString v))}))
     (field "Horizon"
            (segmented {:control :horizon
                        :value horizon
                        :options horizon-options
                        :fmt (fn [v] (str v "d"))}))
     (field "Bust threshold (drawdown)"
            (stepper {:control :bust :value bust :suffix "%" :step 5}))
     (field "Goal (total return)"
            (stepper {:control :goal :value goal :suffix "%" :step 10}))
     (field "Seed"
            (stepper {:control :seed :value seed :step 1}))
     [:div {:class ["mc-run"]}
      [:span {:class ["mc-run-status"]}
       [:b (.toLocaleString sims)] (str " paths · " horizon "d")]
      [:button {:type "button"
                :class ["mc-run-btn"]
                :data-role "portfolio-monte-carlo-rerun"
                :on {:click [[:actions/rerun-portfolio-monte-carlo]]}}
       (str/join " " ["↻" "Re-run"])]]]))
