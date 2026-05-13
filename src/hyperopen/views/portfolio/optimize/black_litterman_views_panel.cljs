(ns hyperopen.views.portfolio.optimize.black-litterman-views-panel
  (:require [hyperopen.views.portfolio.optimize.black-litterman-active-views :as active-views]
            [hyperopen.views.portfolio.optimize.black-litterman-views-controls :as controls]
            [hyperopen.views.portfolio.optimize.black-litterman-views-model :as model]))

(defn black-litterman-views-panel
  ([draft readiness]
   (black-litterman-views-panel draft readiness {}))
  ([draft readiness editor-state]
   (when-let [{:keys [universe views kind errors editing? draft valid? pending? clear-open?]}
              (model/editor-view-model draft readiness editor-state)]
     [:section {:class ["portfolio-optimizer-bl-panel" "space-y-4"]
                :data-role "portfolio-optimizer-black-litterman-panel"}
      [:div
       [:p {:class controls/eyebrow-class} "EDIT VIEWS"]
       [:span {:class ["sr-only"]} "Black-Litterman Views"]
       [:h3 {:class ["mt-1" "text-[0.8125rem]" "font-semibold" "text-trading-text"]}
        "Tell the model what you believe"]]

      [:div {:class ["grid" "grid-cols-2" "border" "border-base-300"]}
       (controls/segmented-button "ABSOLUTE" (= :absolute kind)
                                  "portfolio-optimizer-black-litterman-editor-type-absolute"
                                  [:actions/set-portfolio-optimizer-black-litterman-editor-type
                                   :absolute])
       (controls/segmented-button "RELATIVE" (= :relative kind)
                                  "portfolio-optimizer-black-litterman-editor-type-relative"
                                  [:actions/set-portfolio-optimizer-black-litterman-editor-type
                                   :relative])]

      [:div {:class (controls/instrument-grid-class kind)
             :data-role "portfolio-optimizer-black-litterman-editor-instrument-grid"}
       (controls/instrument-option-group {:label "ASSET"
                                          :universe universe
                                          :selected (:instrument-id draft)
                                          :field :instrument-id
                                          :role-prefix "portfolio-optimizer-black-litterman-editor-asset"})
       (when (= :relative kind)
         (controls/instrument-option-group {:label "COMPARATOR"
                                            :universe universe
                                            :selected (:comparator-instrument-id draft)
                                            :field :comparator-instrument-id
                                            :exclude-id (:instrument-id draft)
                                            :role-prefix "portfolio-optimizer-black-litterman-editor-comparator"}))]

      (when (= :relative kind)
        (controls/option-group {:label "DIRECTION"
                                :options controls/direction-options
                                :selected (:direction draft)
                                :field :direction
                                :role-prefix "portfolio-optimizer-black-litterman-editor-direction"}))

      (controls/text-input {:label (if (= :relative kind)
                                     "EXPECTED RETURN / SPREAD (annualized)"
                                     "EXPECTED RETURN (annualized)")
                            :value (:return-text draft)
                            :field :return-text
                            :inputmode "decimal"
                            :role "portfolio-optimizer-black-litterman-editor-return"
                            :error (:return-text errors)})

      [:div {:class ["grid" "grid-cols-1" "gap-3" "sm:grid-cols-2" "xl:grid-cols-1" "2xl:grid-cols-2"]}
       (controls/option-group {:label "CONFIDENCE"
                               :options controls/confidence-options
                               :selected (:confidence draft)
                               :field :confidence
                               :role-prefix "portfolio-optimizer-black-litterman-editor-confidence"})
       (controls/option-group {:label "HORIZON"
                               :options controls/horizon-options
                               :selected (:horizon draft)
                               :field :horizon
                               :role-prefix "portfolio-optimizer-black-litterman-editor-horizon"})]

      (controls/notes-input draft)

      [:div {:class ["border" "border-base-300" "bg-base-200/30" "p-3"]
             :data-role "portfolio-optimizer-black-litterman-preview"}
       [:p {:class controls/eyebrow-class} "VIEW PREVIEW"]
       [:p {:class ["mt-2" "text-[0.75rem]" "font-semibold" "text-trading-text"]
            :data-role "portfolio-optimizer-black-litterman-preview-text"}
        (model/preview-text universe kind draft)]
       [:p {:class ["mt-1" "font-mono" "text-[0.625rem]" "text-trading-muted"]}
        (str "Confidence: " (model/display-confidence (:confidence draft))
             " · Horizon: " (model/display-horizon (:horizon draft)))]
       (when (and pending? valid?)
         [:p {:class ["mt-2" "font-mono" "text-[0.625rem]" "text-warning"]
              :data-role "portfolio-optimizer-black-litterman-pending-view-status"}
          (if editing?
            "Pending changes will apply on run."
            "Pending view will apply on run.")])]

      (when (:comparator-instrument-id errors)
        [:p {:class ["text-[0.65625rem]" "text-warning"]} (:comparator-instrument-id errors)])
      (when (:max errors)
        [:p {:class ["text-[0.65625rem]" "text-warning"]} (:max errors)])

      [:div {:class ["flex" "gap-2"]}
       [:button {:type "button"
                 :class ["flex-1" "border" "border-warning/70" "bg-warning/80" "px-3"
                         "py-2" "text-[0.71875rem]" "font-semibold" "text-base-100"
                         "disabled:cursor-not-allowed" "disabled:border-base-300"
                         "disabled:bg-base-200/30" "disabled:text-trading-muted"]
                 :disabled (not valid?)
                 :data-role "portfolio-optimizer-black-litterman-save-view"
                 :on {:click [[:actions/save-portfolio-optimizer-black-litterman-editor-view]]}}
        (if editing? "Save changes" "+ Add view")]
       (when editing?
         [:button {:type "button"
                   :class ["border" "border-base-300" "bg-base-100" "px-3" "py-2"
                           "text-[0.6875rem]" "font-semibold" "text-trading-muted"
                           "hover:text-trading-text"]
                   :data-role "portfolio-optimizer-black-litterman-cancel-edit"
                   :on {:click [[:actions/cancel-portfolio-optimizer-black-litterman-edit]]}}
          "Cancel"])]

      (active-views/active-views-section universe views clear-open?)

      [:p {:class ["font-mono" "text-[0.625rem]" "leading-[1.45]" "text-trading-muted"]
           :data-role "portfolio-optimizer-black-litterman-helper"}
       "Views adjust expected returns only. Risk (covariance) is unchanged."]])))
