(ns hyperopen.views.portfolio.optimize.setup-v4-use-my-views-cards
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.black-litterman-preview :as black-litterman-preview]
            [hyperopen.portfolio.optimizer.application.instrument-labels :as instrument-labels]
            [hyperopen.views.portfolio.optimize.format :as opt-format]))

(def ^:private max-market-reference-rows 4)
(def ^:private max-view-rows 4)
(def ^:private max-output-rows 4)

(def ^:private step-class
  ["font-mono" "text-[0.625rem]" "font-semibold" "uppercase" "tracking-[0.08em]"
   "text-trading-muted/60"])

(def ^:private title-class
  ["mt-3" "text-[0.875rem]" "font-semibold" "leading-[1.25]" "text-trading-text"])

(def ^:private body-class
  ["mt-3" "text-[0.75rem]" "font-medium" "leading-[1.55]" "text-trading-muted"])

(def ^:private row-list-class
  ["mt-4" "space-y-1.5" "font-mono" "text-[0.71875rem]" "leading-[1.35]"
   "tabular-nums"])

(defn preview
  [readiness]
  (black-litterman-preview/build-preview readiness))

(defn- finite-number?
  [value]
  (opt-format/finite-number? value))

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

(defn- card-shell
  [{:keys [role step label title copy accent?]} rows]
  [:section {:class (cond-> ["flex" "min-h-[15.5rem]" "flex-col" "border"
                             "border-base-300" "bg-[#101416]" "px-5" "py-5"]
                      accent? (conj "border-warning/60" "bg-[#12110d]"))
             :data-role role}
   [:p {:class (cond-> step-class
                 accent? (conj "text-warning"))}
    [:span step]
    [:span {:class ["mx-2" "text-trading-muted/50"]} "·"]
    label]
   [:h3 {:class title-class} title]
   [:p {:class body-class} copy]
   (into [:div {:class row-list-class}]
         rows)])

(defn- empty-row
  [role copy]
  [:div {:class ["border" "border-base-300" "bg-base-200/20" "px-3" "py-2"
                 "font-sans" "text-[0.71875rem]" "font-medium" "leading-[1.45]"
                 "text-trading-muted"]
         :data-role role}
   copy])

(defn- value-row
  [role label value value-class]
  [:div {:class ["grid" "grid-cols-[minmax(0,1fr)_auto]" "items-baseline" "gap-3"]
         :data-role role}
   [:span {:class ["min-w-0" "truncate" "text-trading-muted"]} label]
   [:span {:class (into ["text-right"] value-class)} value]])

(defn- market-reference-card
  [preview]
  (let [rows (take max-market-reference-rows (:rows preview))
        ready? (seq rows)]
    (card-shell
     {:role "portfolio-optimizer-setup-use-my-views-card-market-reference"
      :step "1"
      :label "Market reference"
      :title "What the model assumes before your views"
      :copy "Baseline expected returns from the current return inputs before your Black-Litterman views are blended. This calibrated reference is not a forecast."}
     (if ready?
       (map-indexed
        (fn [idx row]
          (value-row
           (str "portfolio-optimizer-setup-use-my-views-card-market-reference-row-" idx)
           (or (:label row) (short-id-label (:instrument-id row)))
           (pct (:prior-return row))
           ["text-[#8eb1dd]"]))
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
      :copy (if (pos? view-count)
              (str view-count " " (plural view-count "view" "views")
                   " active. Confidence determines how much each view pulls the combined output away from the market reference.")
              "No active views yet. Add an absolute or relative view to pull the combined output away from the market reference.")
      :accent? true}
     (if (pos? view-count)
       (map-indexed
        (fn [idx view]
          [:div {:class ["grid" "grid-cols-[minmax(0,1fr)_auto]" "items-baseline" "gap-3"]
                 :data-role (str "portfolio-optimizer-setup-use-my-views-card-your-views-row-" idx)}
           [:span {:class ["min-w-0" "truncate" "text-trading-muted"]}
            (view-expression labels view)]
           [:span {:class ["text-right" "whitespace-nowrap"]}
            [:span {:class ["text-trading-text"]} (signed-view-pct (:return view))]
            [:span {:class ["text-trading-muted/50"]} " · "]
            [:span {:class ["text-trading-muted"]} (confidence-label view)]]])
        (take max-view-rows views))
       [(empty-row
         "portfolio-optimizer-setup-use-my-views-card-your-views-empty"
         "No active views.")]))))

(defn- row-by-id
  [rows]
  (into {}
        (map (fn [row] [(:instrument-id row) row]))
        rows))

(defn- output-instrument-ids
  [views preview]
  (let [preview-ids (set (keep :instrument-id (:rows preview)))
        primary-ids (->> views
                         (keep primary-id)
                         (filter preview-ids)
                         distinct)
        changed-ids (->> (:rows preview)
                         (filter (fn [row]
                                   (let [delta (- (or (:posterior-return row) 0)
                                                  (or (:prior-return row) 0))]
                                     (and (finite-number? delta)
                                          (not (zero? delta))))))
                         (map :instrument-id)
                         distinct)]
    (if (seq primary-ids)
      primary-ids
      changed-ids)))

(defn- output-note
  [views labels]
  (if-let [low-view (first (filter #(= "low" (confidence-label %)) views))]
    (str "Low confidence on " (label-for labels (primary-id low-view))
         " means your " (signed-view-pct (:return low-view))
         " view only moves the combined output partway from the reference.")
    "Confidence weighting controls how far the combined output moves away from the market reference."))

(defn- combined-output-card
  [views labels preview]
  (let [rows-by-id (row-by-id (:rows preview))
        output-rows (->> (output-instrument-ids views preview)
                         (keep rows-by-id)
                         (take max-output-rows))
        view-count (count views)]
    (card-shell
     {:role "portfolio-optimizer-setup-use-my-views-card-combined-output"
      :step "3"
      :label "Combined output"
      :title "How much your views actually matter"
      :copy "Posterior return per asset, after blending the market reference with your views weighted by confidence."}
     (cond
       (not (seq (:rows preview)))
       [(empty-row
         "portfolio-optimizer-setup-use-my-views-card-combined-output-empty"
         "Combined output appears once the posterior preview can be computed.")]

       (zero? view-count)
       [(empty-row
         "portfolio-optimizer-setup-use-my-views-card-combined-output-empty"
         "No active views yet. Posterior output matches the market reference.")]

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
               [:div {:class ["grid" "grid-cols-[minmax(0,1fr)_auto]" "items-baseline" "gap-3"]
                      :data-role (str "portfolio-optimizer-setup-use-my-views-card-combined-output-row-" idx)}
                [:span {:class ["min-w-0" "truncate" "text-trading-muted"]}
                 (or (:label row) (short-id-label (:instrument-id row)))]
                [:span {:class ["text-right" "whitespace-nowrap"]}
                 [:span {:class ["text-trading-muted/70"]} (pct prior)]
                 [:span {:class ["px-1.5" "text-trading-muted/50"]} "→"]
                 [:span {:class ["font-semibold" "text-warning"]} (pct posterior)]
                 [:span {:class ["ml-1.5" "text-trading-muted/70"]}
                  (str "(" (signed-delta delta) ")")]]]))
           output-rows)
          [[:div {:class ["mt-4" "border" "border-base-300" "bg-base-200/20"
                          "px-3" "py-2" "font-sans" "text-[0.71875rem]"
                          "font-medium" "leading-[1.45]" "text-trading-muted"]
                  :data-role "portfolio-optimizer-setup-use-my-views-card-combined-output-note"}
            (output-note views labels)]])
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
