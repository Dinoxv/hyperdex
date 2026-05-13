(ns hyperopen.views.portfolio.optimize.black-litterman-active-views
  (:require [hyperopen.views.portfolio.optimize.black-litterman-views-controls :as controls]
            [hyperopen.views.portfolio.optimize.black-litterman-views-model :as model]))

(defn- active-view-card
  [universe view]
  (let [summary (model/view-summary universe view)
        confidence (model/display-confidence
                    (or (:confidence-level view)
                        (cond
                          (<= (or (:confidence view) 0.5) 0.25) :low
                          (<= (or (:confidence view) 0.5) 0.5) :medium
                          :else :high)))
        horizon (model/display-horizon (:horizon view))]
    [:div {:class ["border" "border-base-300" "bg-base-200/50" "p-2.5" "text-left"]
           :role "button"
           :tabindex 0
           :data-role (str "portfolio-optimizer-black-litterman-active-view-" (:id view))
           :on {:click [[:actions/edit-portfolio-optimizer-black-litterman-view (:id view)]]}}
     [:div {:class ["flex" "items-start" "justify-between" "gap-2"]}
      [:div {:class ["min-w-0"]}
       [:p {:class ["truncate" "text-[0.75rem]" "font-semibold" "text-trading-text"]}
        summary]
       [:p {:class ["mt-1" "font-mono" "text-[0.625rem]" "text-trading-muted"]}
        (str "Confidence: " confidence " · Horizon: " horizon)]]
      [:button {:type "button"
                :class ["shrink-0" "border" "border-transparent" "bg-transparent" "px-1.5"
                        "text-[0.875rem]" "text-trading-muted" "hover:border-base-300"
                        "hover:text-warning"]
                :aria-label (str "Remove " summary " view")
                :data-role (str "portfolio-optimizer-black-litterman-active-view-" (:id view) "-remove")
                :on {:click [[:actions/remove-portfolio-optimizer-black-litterman-view
                              (:id view)]]}}
       "x"]]]))

(defn active-views-section
  [universe views clear-open?]
  [:div {:class ["border-t" "border-base-300" "pt-3"]}
   [:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
    [:p {:class controls/eyebrow-class}
     (str "ACTIVE VIEWS (" (count views) "/" model/max-active-views ")")]
    [:button {:type "button"
              :class ["bg-transparent" "text-[0.65625rem]" "font-semibold"
                      "text-trading-muted" "hover:text-warning"
                      "disabled:cursor-not-allowed" "disabled:text-trading-muted/40"]
              :disabled (empty? views)
              :data-role "portfolio-optimizer-black-litterman-clear-all"
              :on {:click [[:actions/request-clear-portfolio-optimizer-black-litterman-views]]}}
     "Clear all"]]
   (if (seq views)
     (into [:div {:class ["mt-2" "space-y-2"]}]
           (map (partial active-view-card universe) views))
     [:p {:class ["mt-2" "border" "border-base-300" "bg-base-200/30" "p-3"
                  "text-[0.6875rem]" "text-trading-muted"]}
      "No views yet. Add an absolute or relative belief to blend with the market reference."])
   (when clear-open?
     [:div {:class ["mt-3" "border" "border-warning/50" "bg-warning/10" "p-3"]
            :data-role "portfolio-optimizer-black-litterman-clear-confirmation"}
      [:p {:class ["text-[0.71875rem]" "font-semibold" "text-trading-text"]}
       "Clear all views?"]
      [:p {:class ["mt-1" "text-[0.65625rem]" "text-trading-muted"]}
       (str "This removes " (count views) " views from the scenario.")]
      [:div {:class ["mt-3" "flex" "gap-2"]}
       [:button {:type "button"
                 :class ["border" "border-base-300" "bg-base-100" "px-3" "py-1.5"
                         "text-[0.6875rem]" "font-semibold" "text-trading-muted"]
                 :data-role "portfolio-optimizer-black-litterman-clear-cancel"
                 :on {:click [[:actions/cancel-clear-portfolio-optimizer-black-litterman-views]]}}
        "Cancel"]
       [:button {:type "button"
                 :class ["border" "border-warning/70" "bg-warning/80" "px-3" "py-1.5"
                         "text-[0.6875rem]" "font-semibold" "text-base-100"]
                 :data-role "portfolio-optimizer-black-litterman-clear-confirm"
                 :on {:click [[:actions/confirm-clear-portfolio-optimizer-black-litterman-views]]}}
        "Clear views"]]])])
