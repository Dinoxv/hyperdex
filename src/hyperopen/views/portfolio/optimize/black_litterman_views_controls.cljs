(ns hyperopen.views.portfolio.optimize.black-litterman-views-controls
  (:require [hyperopen.views.portfolio.optimize.black-litterman-views-model :as model]))

(def confidence-options
  [[:low "LOW"]
   [:medium "MEDIUM"]
   [:high "HIGH"]])

(def horizon-options
  [[:1m "1M"]
   [:3m "3M"]
   [:6m "6M"]
   [:1y "1Y"]])

(def direction-options
  [[:outperform "Outperform"]
   [:underperform "Underperform"]])

(def eyebrow-class
  ["font-mono" "text-[0.625rem]" "font-semibold" "uppercase" "tracking-[0.08em]" "text-trading-muted/70"])

(def ^:private input-class
  ["w-full" "border" "border-base-300" "bg-base-100" "px-2" "py-1.5"
   "font-mono" "text-[0.71875rem]" "text-trading-text" "outline-none"
   "focus:border-warning/70"])

(defn instrument-grid-class
  [kind]
  (cond-> ["grid" "grid-cols-1" "gap-3"]
    (= :relative kind) (into ["sm:grid-cols-2" "xl:grid-cols-1" "2xl:grid-cols-2"])))

(defn segmented-button
  [label selected? role action]
  [:button {:type "button"
            :class (cond-> ["border" "border-base-300" "bg-base-200/20" "px-2" "py-1.5"
                            "text-center" "text-[0.65625rem]" "font-semibold" "uppercase"
                            "tracking-[0.08em]" "text-trading-muted" "hover:text-warning"]
                     selected? (conj "border-warning/70" "bg-warning/10" "text-warning"))
            :aria-pressed (str selected?)
            :data-role role
            :on {:click [action]}}
   label])

(defn option-group
  [{:keys [label options selected field role-prefix]}]
  [:div
   [:p {:class eyebrow-class} label]
   (into [:div {:class ["mt-2" "grid" "grid-cols-2" "gap-1"]
                :role "listbox"
                :aria-label label
                :data-role (str role-prefix "-options")}]
         (map (fn [[value option-label]]
                [:button {:type "button"
                          :role "option"
                          :aria-selected (str (= value selected))
                          :class (cond-> ["border" "border-base-300" "bg-base-100" "px-2" "py-1.5"
                                          "text-left" "text-[0.6875rem]" "font-medium"
                                          "text-trading-muted" "hover:text-warning"]
                                   (= value selected)
                                   (conj "border-warning/70" "bg-warning/10" "text-warning"))
                          :data-role (str role-prefix "-option-" (name value))
                          :on {:click [[:actions/set-portfolio-optimizer-black-litterman-editor-field
                                        field
                                        value]]}}
                 option-label])
              options))])

(defn instrument-option-group
  [{:keys [label universe selected field role-prefix exclude-id]}]
  [:div
   [:p {:class eyebrow-class} label]
   (into [:div {:class ["mt-2" "max-h-32" "overflow-y-auto" "border" "border-base-300"]
                :role "listbox"
                :aria-label label
                :data-role (str role-prefix "-options")}]
         (map (fn [instrument]
                (let [instrument-id (:instrument-id instrument)
                      selected? (= instrument-id selected)
                      disabled? (= instrument-id exclude-id)]
                  [:button {:type "button"
                            :role "option"
                            :aria-selected (str selected?)
                            :disabled disabled?
                            :class (cond-> ["block" "w-full" "border-b" "border-base-300"
                                            "bg-base-100" "px-2" "py-1.5" "text-left"
                                            "text-[0.6875rem]" "font-medium"
                                            "last:border-b-0" "hover:text-warning"
                                            "disabled:cursor-not-allowed" "disabled:text-trading-muted/40"]
                                     selected? (conj "bg-warning/10" "text-warning"))
                            :data-role (str role-prefix "-option-" instrument-id)
                            :on {:click [[:actions/set-portfolio-optimizer-black-litterman-editor-field
                                          field
                                          instrument-id]]}}
                   [:span {:class ["font-mono"]} (model/instrument-label universe instrument-id)]
                   (when (:symbol instrument)
                     [:span {:class ["ml-2" "text-trading-muted"]} (:symbol instrument)])]))
              universe))])

(defn text-input
  [{:keys [label value field role inputmode error]}]
  [:label {:class ["block"]}
   [:span {:class eyebrow-class} label]
   [:input {:type "text"
            :inputmode (or inputmode "text")
            :class (conj input-class "mt-2")
            :data-role role
            :aria-invalid (when error "true")
            :value (str (or value ""))
            :on {:input [[:actions/set-portfolio-optimizer-black-litterman-editor-field
                          field
                          [:event.target/value]]]}}]
   (when error
     [:p {:class ["mt-1" "text-[0.65625rem]" "text-warning"]} error])])

(defn notes-input
  [draft]
  [:label {:class ["block"]}
   [:span {:class eyebrow-class} "NOTES (optional)"]
   [:textarea {:class ["mt-2" "min-h-[44px]" "w-full" "resize-y" "border" "border-base-300"
                       "bg-base-100" "px-2" "py-1.5" "text-[0.6875rem]" "outline-none"
                       "focus:border-warning/70"]
               :maxlength 280
               :data-role "portfolio-optimizer-black-litterman-editor-notes"
               :value (str (or (:notes draft) ""))
               :on {:input [[:actions/set-portfolio-optimizer-black-litterman-editor-field
                             :notes
                             [:event.target/value]]]}}]])
