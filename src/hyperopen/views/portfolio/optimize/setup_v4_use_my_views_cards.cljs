(ns hyperopen.views.portfolio.optimize.setup-v4-use-my-views-cards
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.black-litterman-preview :as black-litterman-preview]
            [hyperopen.portfolio.optimizer.application.instrument-labels :as instrument-labels]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(def ^:private max-market-reference-rows 4)
(def ^:private max-view-rows 4)
(def ^:private max-output-rows 4)

(def ^:private step-class
  ["views-trust-panel__eyebrow"])

(def ^:private title-class
  ["views-trust-panel__headline"])

(def ^:private body-class
  ["views-trust-panel__description"])

(def ^:private row-list-class
  ["views-trust-panel__body"])

(defn preview
  [readiness]
  (black-litterman-preview/build-preview readiness))

(def ^:private finite-number? opt-format/finite-number?)

(defn- pct
  [value]
  (opt-format/format-pct value {:minimum-fraction-digits 1
                                :maximum-fraction-digits 1}))

(defn- signed-view-pct
  [value]
  (opt-format/format-pct-delta value {:minimum-fraction-digits 0
                                      :maximum-fraction-digits 0
                                      :suffix "%"}))

(defn- signed-delta
  [value]
  (opt-format/format-pct-delta value {:minimum-fraction-digits 1
                                      :maximum-fraction-digits 1
                                      :suffix ""}))

(defn- short-id-label
  [instrument-id]
  (or (some-> instrument-id str (str/split #":") last)
      "Select"))

(defn- universe
  [draft readiness]
  (or (get-in readiness [:request :universe])
      (:universe draft)
      []))

(defn- labels-by-id
  [draft readiness preview]
  (let [ids (vec (concat (keep :instrument-id (:rows preview))
                         (keep :instrument-id (universe draft readiness))))]
    (merge
     (into {}
           (map (fn [row]
                  [(:instrument-id row) (or (:label row)
                                            (short-id-label (:instrument-id row)))]))
           (:rows preview))
     (instrument-labels/labels-by-instrument (universe draft readiness) ids))))

(defn- label-for
  [labels instrument-id]
  (or (get labels instrument-id)
      (short-id-label instrument-id)))

(defn- primary-id
  [view]
  (or (:instrument-id view)
      (:long-instrument-id view)))

(defn- comparator-id
  [view]
  (or (:comparator-instrument-id view)
      (:short-instrument-id view)))

(defn- active-views
  [draft readiness]
  (let [request-views (seq (get-in readiness [:request :return-model :views]))
        draft-views (seq (get-in draft [:return-model :views]))]
    (vec (or request-views draft-views []))))

(defn- confidence-value
  [view]
  (cond
    (finite-number? (:confidence view))
    (:confidence view)

    (finite-number? (:confidence-variance view))
    (- 1 (:confidence-variance view))

    :else
    nil))

(defn- confidence-label
  [view]
  (cond
    (:confidence-level view)
    (-> (:confidence-level view) name str/lower-case)

    (keyword? (:confidence view))
    (-> (:confidence view) name str/lower-case)

    (finite-number? (confidence-value view))
    (let [confidence (confidence-value view)]
      (cond
        (<= confidence 0.25) "low"
        (<= confidence 0.5) "medium"
        :else "high"))

    :else
    "medium"))

(defn- view-expression
  [labels view]
  (let [primary (label-for labels (primary-id view))
        comparator (label-for labels (comparator-id view))
        direction (if (= :underperform (:direction view)) "<" ">")]
    (case (:kind view)
      :relative (str primary " " direction " " comparator " by")
      primary)))

(defn- plural
  [count singular pluralized]
  (if (= 1 count) singular pluralized))

(defn- delta-for-row
  [row]
  (when (and (finite-number? (:prior-return row))
             (finite-number? (:posterior-return row)))
    (- (:posterior-return row) (:prior-return row))))

(defn- abs-number
  [value]
  (js/Math.abs value))

(defn- material-move?
  [row]
  (some-> row delta-for-row abs-number (>= 0.001)))

(defn- step-id
  [step]
  (str "views-trust-" step))

(defn- card-shell
  [{:keys [role step label title copy accent? dim?]} rows]
  [:section (cond-> {:class (cond-> ["views-trust-panel__box" "flex" "min-h-[15.5rem]" "flex-col"
                                     "border" "border-base-300"]
                              dim? (conj "opacity-50"))
                     :aria-labelledby (step-id step)
                     :data-role role}
              accent? (assoc :aria-current "step"))
   [:h3 {:id (step-id step)
         :class (cond-> step-class
                  accent? (conj "views-trust-panel__eyebrow--accent"))}
    [:span step]
    [:span {:class ["mx-2" "text-trading-muted/50"]} "·"]
    label]
   [:p {:class title-class} title]
   (when copy
     [:p {:class body-class} copy])
   (into [:div {:class row-list-class}]
         rows)])

(defn- empty-row
  [role copy]
  [:div {:class ["views-trust-panel__empty"]
         :data-role role}
   copy])

(defn- value-row
  [role label value value-class]
  [:div {:class ["views-trust-panel__row"]
         :data-role role}
   [:span {:class ["min-w-0" "truncate" "text-trading-muted"]} label]
   [:span {:class (into ["num" "text-right"] value-class)} value]])

(defn- market-reference-card
  [preview]
  (let [rows (->> (:rows preview)
                  (sort-by #(or (:prior-return %) js/Number.NEGATIVE_INFINITY) >)
                  (take max-market-reference-rows))
        ready? (seq rows)]
    (card-shell
     {:role "portfolio-optimizer-setup-use-my-views-card-market-reference"
      :step "1"
      :label "Market reference"
      :title "What the model assumes before your views"
      :copy "Returns implied by current market weights and the stabilized covariance. This is a calibrated baseline — not a forecast."}
     (if ready?
       (map-indexed
        (fn [idx row]
          (value-row
           (str "portfolio-optimizer-setup-use-my-views-card-market-reference-row-" idx)
           (or (:label row) (short-id-label (:instrument-id row)))
           (pct (:prior-return row))
           ["views-trust-panel__value--prior"]))
        rows)
       [(empty-row
         "portfolio-optimizer-setup-use-my-views-card-market-reference-empty"
         "Market reference rows appear once the selected universe has eligible history.")]))))

(defn- your-views-card
  [views labels]
  (let [view-count (count views)]
    (card-shell
     {:role "portfolio-optimizer-setup-use-my-views-card-your-views"
      :step "2"
      :label "Your views"
      :title "What you're changing"
      :copy (when (pos? view-count)
              (str view-count " " (plural view-count "view" "views")
                   " active. Confidence determines how much each view pulls the combined output away from the market reference."))
      :accent? true}
     (if (pos? view-count)
       (map-indexed
        (fn [idx view]
          [:div {:class ["views-trust-panel__row"]
                 :data-role (str "portfolio-optimizer-setup-use-my-views-card-your-views-row-" idx)}
           [:span {:class ["min-w-0" "truncate" "text-trading-muted"]}
            (view-expression labels view)]
           [:span {:class ["num" "text-right" "whitespace-nowrap"]}
            [:span {:class ["views-trust-panel__value--view"]} (signed-view-pct (:return view))]
            [:span {:class ["text-trading-muted/50"]} " · "]
            [:span {:class ["views-trust-panel__confidence"]} (confidence-label view)]]])
        (take max-view-rows views))
       [(empty-row
         "portfolio-optimizer-setup-use-my-views-card-your-views-empty"
         "No views added yet. Add one in the editor below to see how it changes the recommendation.")]))))

(defn- row-by-id
  [rows]
  (into {}
        (map (fn [row] [(:instrument-id row) row]))
        rows))

(defn- output-instrument-ids
  [views preview]
  (let [preview-ids (set (keep :instrument-id (:rows preview)))
        row-by-id* (row-by-id (:rows preview))
        primary-ids (->> views
                         (keep primary-id)
                         (filter preview-ids)
                         distinct)
        eligible-ids (set (concat primary-ids
                                  (->> (:rows preview)
                                       (filter material-move?)
                                       (map :instrument-id))))]
    (->> eligible-ids
         (keep row-by-id*)
         (sort-by #(abs-number (or (delta-for-row %) 0)) >)
         (map :instrument-id))))

(defn- output-callout
  [views labels rows-by-id]
  (let [low-muted
        (some
         (fn [view]
           (let [id (primary-id view)
                 row (get rows-by-id id)
                 prior (:prior-return row)
                 posterior (:posterior-return row)
                 view-return (:return view)
                 pulled (when (and (finite-number? prior)
                                   (finite-number? posterior))
                          (abs-number (- posterior prior)))
                 claim-distance (when (and (finite-number? view-return)
                                           (finite-number? prior))
                                  (abs-number (- view-return prior)))
                 ratio (when (and pulled claim-distance (pos? claim-distance))
                         (/ pulled claim-distance))]
             (when (and (= "low" (confidence-label view))
                        ratio
                        (< ratio 0.25))
               {:kind :low-muted
                :instrument-id id
                :view-return view-return
                :posterior posterior})))
         views)
        dominated
        (some
         (fn [row]
           (let [delta (delta-for-row row)]
             (when (and delta (> (abs-number delta) 0.05))
               {:kind :dominated
                :instrument-id (:instrument-id row)
                :delta delta
                :confidence (or (some (fn [view]
                                        (when (= (:instrument-id row) (primary-id view))
                                          (confidence-label view)))
                                      views)
                                "medium")})))
         (sort-by #(abs-number (or (delta-for-row %) 0)) > (vals rows-by-id)))]
    (cond
      low-muted
      (str "Low confidence on " (label-for labels (:instrument-id low-muted))
           " means your " (signed-view-pct (:view-return low-muted))
           " view only pulls posterior to " (pct (:posterior low-muted))
           ". Strong claims need strong confidence.")

      dominated
      (str (label-for labels (:instrument-id dominated))
           " moved " (signed-delta (:delta dominated))
           "pp — your " (:confidence dominated)
           " view dominated the prior here.")

      :else nil)))

(defn- output-note
  [copy]
  [:aside {:class ["views-trust-panel__callout"]
           :role "note"
           :data-role "portfolio-optimizer-setup-use-my-views-card-combined-output-callout"}
   copy])

(defn- combined-output-card
  [views labels preview]
  (let [rows-by-id (row-by-id (:rows preview))
        output-rows (->> (output-instrument-ids views preview)
                         (keep rows-by-id)
                         (take max-output-rows))
        view-count (count views)
        callout-copy (output-callout views labels rows-by-id)]
    (card-shell
     {:role "portfolio-optimizer-setup-use-my-views-card-combined-output"
      :step "3"
      :label "Combined output"
      :title "How much your views actually matter"
      :copy "Posterior return per asset, after blending the market reference with your views weighted by confidence."
      :dim? (zero? view-count)}
     (cond
       (not (seq (:rows preview)))
       [(empty-row
         "portfolio-optimizer-setup-use-my-views-card-combined-output-empty"
         "Combined output appears once the posterior preview can be computed.")]

       (zero? view-count)
       [(empty-row
         "portfolio-optimizer-setup-use-my-views-card-combined-output-empty"
         "Add a view to see the combined output.")]

       :else
       (if (seq output-rows)
         (concat
          (map-indexed
           (fn [idx row]
             (let [prior (:prior-return row)
                   posterior (:posterior-return row)
                   delta (when (and (finite-number? prior)
                                    (finite-number? posterior))
                           (- posterior prior))]
               [:div {:class ["views-trust-panel__row"]
                      :data-role (str "portfolio-optimizer-setup-use-my-views-card-combined-output-row-" idx)}
                [:span {:class ["min-w-0" "truncate" "text-trading-muted"]}
                 (or (:label row) (short-id-label (:instrument-id row)))]
                [:span {:class ["num" "text-right" "whitespace-nowrap"]}
                 [:span {:class ["text-trading-muted/70"]} (pct prior)]
                 [:span {:class ["px-1.5" "text-trading-muted/50"]} "→"]
                 [:span {:class ["views-trust-panel__value--combined"]} (pct posterior)]
                 [:span {:class ["ml-1.5" "text-trading-muted/70"]}
                  (str "(" (signed-delta delta) ")")]]]))
           output-rows)
          (when callout-copy
            [(output-note callout-copy)]))
         [(empty-row
           "portfolio-optimizer-setup-use-my-views-card-combined-output-empty"
           "Active views do not match the current model universe yet. Combined output will update after the views are corrected or removed.")])))))

(defn cards
  [draft readiness preview]
  (let [views (active-views draft readiness)
        labels (labels-by-id draft readiness preview)]
    [:div {:class ["grid" "grid-cols-1" "gap-3" "xl:grid-cols-3"]
           :data-role "portfolio-optimizer-setup-use-my-views-insight-cards"}
     (market-reference-card preview)
     (your-views-card views labels)
     (combined-output-card views labels preview)]))
