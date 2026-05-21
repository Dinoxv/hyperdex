(ns hyperopen.views.portfolio.optimize.scenario-objective-menu
  (:require [hyperopen.platform :as platform]
            [hyperopen.portfolio.optimizer.contracts :as optimizer-contracts]))

(def ^:private objective-menu-options
  [{:key :minimum-volatility
    :title "Minimum volatility"
    :description "Smallest feasible sigma - defensive baseline"}
   {:key :max-sharpe
    :title "Maximum Sharpe"
    :description "Best risk-adjusted return"}
   {:key :target-volatility
    :title "Target volatility · 12%"
    :description "Pin to a fixed level, max return at that sigma"}
   {:key :maximum-return
    :title "Maximum return"
    :description "Aggressive. Drives toward the right of the frontier"}
   {:key :use-my-views
    :title "Use my views"
    :description "Black-Litterman: combine market reference with beliefs"}])

(defn current-objective-menu-key
  [draft result]
  (let [objective-kind (or (get-in draft [:objective :kind])
                           (get-in result [:solver :objective-kind]))
        return-model-kind (get-in draft [:return-model :kind])]
    (cond
      (= :black-litterman return-model-kind) :use-my-views
      (= :minimum-variance objective-kind) :minimum-volatility
      (= :target-volatility objective-kind) :target-volatility
      (= :target-return objective-kind) :maximum-return
      :else :max-sharpe)))

(defn objective-label
  [objective-key]
  (or (:title (some #(when (= objective-key (:key %)) %)
                    objective-menu-options))
      "Maximum Sharpe"))

(defn objective-menu-open?
  [state]
  (true? (get-in state optimizer-contracts/ui-objective-menu-open-path)))

(defn objective-trigger
  [label open?]
  [:button {:type "button"
            :class ["optimizer-provenance-objective-trigger"
                    "group"
                    "mt-0.5"
                    "inline-flex"
                    "items-center"
                    "gap-1"
                    "border-0"
                    "bg-transparent"
                    "p-0"
                    "font-medium"
                    "text-trading-text"
                    "focus:outline-none"
                    "focus:ring-0"
                    "focus:ring-offset-0"]
            :data-role "portfolio-optimizer-objective-menu-trigger"
            :aria-haspopup "true"
            :aria-expanded (if open? "true" "false")
            :on {:click [[:actions/open-portfolio-optimizer-objective-menu]]}}
   [:span {:class ["optimizer-provenance-objective-label"]} label]
   [:span {:class ["text-[0.6rem]" "text-trading-muted"]} "›"]])

(defn- objective-menu-option
  [{:keys [key title description]} current-key pending-key]
  (let [selected? (= key pending-key)
        current? (= key current-key)
        role (str "portfolio-optimizer-objective-menu-option-" (name key))]
    [:button {:type "button"
              :class ["optimizer-objective-menu-option"
                      "flex"
                      "w-full"
                      "items-start"
                      "gap-3"
                      "border"
                      "p-3"
                      "text-left"
                      "transition-colors"
                      "focus:outline-none"
                      "focus:ring-0"
                      "focus:ring-offset-0"]
              :data-role role
              :data-selected (str selected?)
              :aria-pressed (str selected?)
              :on {:click [[:actions/select-portfolio-optimizer-objective-menu-option key]]}}
     [:span {:class ["optimizer-objective-menu-check"
                     "mt-0.5"
                     "inline-flex"
                     "h-3"
                     "w-3"
                     "shrink-0"
                     "items-center"
                     "justify-center"
                     "border"
                     "font-mono"
                     "text-[0.55rem]"
                     "leading-none"]
             :aria-hidden "true"}
      (when selected? "✓")]
     [:span {:class ["min-w-0"]}
      [:span {:class ["block" "text-[0.8125rem]" "font-semibold" "text-trading-text"]}
       title]
      [:span {:class ["mt-1" "block" "text-[0.6875rem]" "text-trading-muted"]}
       (str description (when current? ". Current."))]]]))

(defn- objective-menu-mount-focus!
  [render-arg]
  (let [node (or (:replicant/node render-arg)
                 render-arg)]
    (platform/queue-microtask!
     (fn []
       (when (and node
                  (.-isConnected node)
                  (fn? (.-focus node)))
         (.focus node))))))

(defn objective-menu
  [state draft result]
  (let [open? (objective-menu-open? state)
        current-key (current-objective-menu-key draft result)
        pending-key (or (get-in state optimizer-contracts/ui-objective-menu-selection-path)
                        current-key)
        apply-disabled? (= current-key pending-key)]
    (when open?
      [:section {:class ["optimizer-objective-menu"
                         "optimizer-objective-popover"
                         "absolute"
                         "left-0"
                         "top-full"
                         "z-50"
                         "mt-2"
                         "border"
                         "shadow-2xl"]
                 :data-role "portfolio-optimizer-objective-menu"
                 :role "region"
                 :tab-index -1
                 :replicant/on-render objective-menu-mount-focus!
                 :aria-label "Change objective"
                 :on {:keydown [[:actions/handle-portfolio-optimizer-objective-menu-keydown
                                  [:event/key]]]}}
       [:header {:class ["flex" "items-start" "justify-between" "gap-4" "border-b" "border-base-300" "px-3" "py-3"]}
        [:div
         [:p {:class ["font-mono" "text-[0.58rem]" "uppercase" "tracking-[0.18em]" "text-trading-muted/70"]}
          "Edit"]
         [:h2 {:class ["mt-1" "text-sm" "font-semibold" "text-trading-text"]}
          "Change objective"]
         [:p {:class ["mt-1.5" "text-[0.7rem]" "text-trading-muted"]}
          "Re-runs the solver with the same universe and constraints"]]
        [:button {:type "button"
                  :class ["border-0"
                          "bg-transparent"
                          "px-1"
                          "py-0"
                          "text-sm"
                          "text-trading-muted"
                          "focus:outline-none"
                          "focus:ring-0"
                          "focus:ring-offset-0"]
                  :aria-label "Close objective menu"
                  :data-role "portfolio-optimizer-objective-menu-close"
                  :on {:click [[:actions/close-portfolio-optimizer-objective-menu]]}}
         "x"]]
       (into
        [:div {:class ["space-y-2" "px-3" "py-3"]}]
        (map #(objective-menu-option % current-key pending-key)
             objective-menu-options))
       [:footer {:class ["flex" "items-center" "justify-between" "gap-3" "border-t" "border-base-300" "px-3" "py-3"]}
        [:span {:class ["font-mono" "text-[0.62rem]" "text-trading-muted"]}
         "Esc to cancel"]
        [:div {:class ["flex" "items-center" "gap-2"]}
         [:button {:type "button"
                   :class ["border" "border-base-300" "bg-base-200/40" "px-3" "py-1.5" "text-[0.7rem]" "font-semibold" "text-trading-text"]
                   :data-role "portfolio-optimizer-objective-menu-cancel"
                   :on {:click [[:actions/close-portfolio-optimizer-objective-menu]]}}
          "Cancel"]
         [:button {:type "button"
                   :class ["optimizer-primary-action"
                           "border"
                           "border-base-300"
                           "px-3"
                           "py-1.5"
                           "text-[0.7rem]"
                           "font-semibold"
                           "disabled:cursor-not-allowed"
                           "disabled:text-trading-muted"]
                   :data-role "portfolio-optimizer-objective-menu-apply"
                   :disabled apply-disabled?
                   :on (when-not apply-disabled?
                         {:click [[:actions/apply-portfolio-optimizer-objective-menu-selection-and-run]]})}
          "Apply & re-run"]]]])))
