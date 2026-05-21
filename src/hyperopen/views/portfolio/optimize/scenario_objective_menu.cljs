(ns hyperopen.views.portfolio.optimize.scenario-objective-menu
  (:require [clojure.string :as str]
            [hyperopen.platform :as platform]
            [hyperopen.portfolio.optimizer.application.black-litterman-editor-model :as bl-model]
            [hyperopen.portfolio.optimizer.contracts :as optimizer-contracts]
            [hyperopen.views.portfolio.optimize.instrument-display :as instrument-display]))

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

(defn- absolute-view?
  [view]
  (= :absolute (:kind view)))

(defn- active-absolute-view
  [views instrument-id]
  (some (fn [view]
          (when (and (absolute-view? view)
                     (= instrument-id (:instrument-id view)))
            view))
        views))

(defn- default-view-order
  [draft state]
  (let [ui-order (vec (keep identity
                            (get-in state optimizer-contracts/ui-objective-menu-view-order-path)))
        views (vec (get-in draft [:return-model :views]))
        view-order (vec (keep (fn [view]
                                (when (absolute-view? view)
                                  (:instrument-id view)))
                              views))
        universe-order (vec (take 3 (keep :instrument-id (:universe draft))))]
    (cond
      (seq ui-order) ui-order
      (seq view-order) view-order
      :else universe-order)))

(defn- instrument-label
  [universe instrument-id]
  (or (some (fn [instrument]
              (when (= instrument-id (:instrument-id instrument))
                (or (when (instrument-display/vault-instrument? instrument)
                      (instrument-display/primary-label instrument))
                    (:coin instrument)
                    (:symbol instrument)
                    (:name instrument))))
            universe)
      (some-> instrument-id
              (str/split #":")
              last)
      instrument-id
      "Asset"))

(defn- inline-view-draft
  [draft state instrument-id]
  (let [views (vec (get-in draft [:return-model :views]))
        view (active-absolute-view views instrument-id)
        ui-draft (get-in state (conj optimizer-contracts/ui-objective-menu-view-drafts-path
                                     (keyword instrument-id)))]
    (merge
     {:return-text (if (some? (:return view))
                     (bl-model/decimal->percent-text (:return view))
                     "")
      :confidence (bl-model/normalize-confidence-level
                   (or (:confidence-level view)
                       (when view (bl-model/confidence-level-from-view view))))}
     ui-draft)))

(defn- confidence-button
  [instrument-id selected-confidence [confidence label]]
  (let [tooltip-label (name confidence)
        short-label (-> label
                        (subs 0 1)
                        str/upper-case)]
    [:button {:type "button"
              :class ["optimizer-objective-view-confidence-button"
                      "border"
                      "border-base-300"
                      "font-mono"
                      "text-[0.58rem]"
                      "font-semibold"
                      "focus:outline-none"
                      "focus:ring-0"
                      "focus:ring-offset-0"]
              :data-role (str "portfolio-optimizer-objective-menu-view-"
                              instrument-id
                              "-confidence-"
                              (name confidence))
              :data-selected (str (= confidence selected-confidence))
              :data-tooltip tooltip-label
              :title tooltip-label
              :aria-label (str "Set " tooltip-label " confidence")
              :aria-pressed (str (= confidence selected-confidence))
              :on {:click [[:actions/set-portfolio-optimizer-objective-menu-view-confidence
                            instrument-id
                            confidence]]}}
     short-label]))

(defn- inline-view-row
  [universe instrument-id view-draft]
  (let [asset-label (instrument-label universe instrument-id)
        selected-confidence (bl-model/normalize-confidence-level
                             (:confidence view-draft))]
    [:div {:class ["optimizer-objective-view-row"
                   "grid"
                   "items-center"
                   "gap-2"
                   "border"
                   "border-base-300"
                   "px-3"
                   "py-2"]
           :data-role (str "portfolio-optimizer-objective-menu-view-row-"
                           instrument-id)}
     [:div {:class ["flex" "min-w-0" "items-center" "gap-2"]}
      [:span {:class ["optimizer-objective-view-token"
                      "inline-flex"
                      "h-5"
                      "w-5"
                      "shrink-0"
                      "items-center"
                      "justify-center"
                      "rounded-full"
                      "font-mono"
                      "text-[0.625rem]"
                      "font-semibold"]
              :aria-hidden "true"}
       (subs asset-label 0 (min 1 (count asset-label)))]
      [:span {:class ["truncate" "text-[0.75rem]" "font-semibold" "text-trading-text"]}
       asset-label]]
     [:label {:class ["optimizer-objective-view-return-shell"
                      "flex"
                      "items-center"
                      "gap-2"
                      "font-mono"
                      "text-[0.625rem]"
                      "text-trading-muted"]}
      [:span "return"]
      [:span {:class ["optimizer-objective-view-return-input-shell"
                      "relative"
                      "inline-flex"
                      "items-center"]}
       [:input {:type "text"
                :inputmode "decimal"
                :class ["optimizer-objective-view-return-input"
                        "border"
                        "border-base-300"
                        "py-1"
                        "pl-2"
                        "pr-5"
                        "text-right"
                        "font-mono"
                        "text-[0.6875rem]"
                        "text-trading-text"
                        "focus:outline-none"
                        "focus:ring-0"
                        "focus:ring-offset-0"]
                :data-role (str "portfolio-optimizer-objective-menu-view-"
                                instrument-id
                                "-return")
                :value (str (or (:return-text view-draft) ""))
                :on {:input [[:actions/set-portfolio-optimizer-objective-menu-view-return
                              instrument-id
                              [:event.target/value]]]}}]
       [:span {:class ["optimizer-objective-view-return-suffix"
                       "pointer-events-none"
                       "absolute"
                       "right-2"
                       "font-mono"
                       "text-[0.625rem]"
                       "text-trading-muted"]}
        "%"]]]
     (into
      [:div {:class ["optimizer-objective-view-confidence"
                     "grid"
                     "grid-cols-3"]
             :aria-label (str asset-label " confidence")}]
      (map #(confidence-button instrument-id selected-confidence %)
           bl-model/confidence-options))
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
               :aria-label (str "Remove " asset-label " view")
               :data-role (str "portfolio-optimizer-objective-menu-view-"
                               instrument-id
                               "-remove")
               :on {:click [[:actions/remove-portfolio-optimizer-objective-menu-view
                             instrument-id]]}}
      "x"]]))

(defn- inline-views-section
  [draft state]
  (let [universe (vec (:universe draft))
        order (default-view-order draft state)]
    [:section {:class ["optimizer-objective-views-section"
                       "border-t"
                       "border-base-300"
                       "px-3"
                       "py-3"]
               :data-role "portfolio-optimizer-objective-menu-use-my-views-editor"}
     [:div {:class ["mb-3" "flex" "items-start" "justify-between" "gap-3"]}
      [:div
       [:p {:class ["font-mono"
                    "text-[0.58rem]"
                    "font-semibold"
                    "uppercase"
                    "tracking-[0.18em]"
                    "text-warning"]}
        "Your return views"]
       [:p {:class ["mt-1" "text-[0.6875rem]" "leading-[1.35]" "text-trading-muted"]}
        "Annualized. Confidence sets how strongly each view pulls the posterior."]]]
     (into
      [:div {:class ["space-y-1.5"]}]
      (map (fn [instrument-id]
             (inline-view-row universe
                              instrument-id
                              (inline-view-draft draft state instrument-id)))
           order))
     [:button {:type "button"
               :class ["mt-2"
                       "w-full"
                       "border"
                       "border-dashed"
                       "border-base-300"
                       "bg-transparent"
                       "px-3"
                       "py-2"
                       "text-left"
                       "text-[0.6875rem]"
                       "font-semibold"
                       "text-trading-muted"
                       "focus:outline-none"
                       "focus:ring-0"
                       "focus:ring-offset-0"]
               :data-role "portfolio-optimizer-objective-menu-add-view"
               :on {:click [[:actions/add-portfolio-optimizer-objective-menu-view]]}}
      "+ Add a view"]
     [:p {:class ["mt-3" "font-mono" "text-[0.625rem]" "leading-[1.45]" "text-trading-muted"]}
      "Relative views (e.g. ETH > SOL by 4%) and per-view caveats - open full editor"]]))

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
        apply-disabled? (and (= current-key pending-key)
                             (not= :use-my-views pending-key))]
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
       (when (= :use-my-views pending-key)
         (inline-views-section draft state))
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
