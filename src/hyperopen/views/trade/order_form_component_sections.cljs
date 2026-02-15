(ns hyperopen.views.trade.order-form-component-sections
  (:require [hyperopen.views.trade.order-form-commands :as cmd]
            [hyperopen.views.trade.order-form-component-primitives :as primitives]))

(defn entry-mode-tabs
  [{:keys [entry-mode
           type
           pro-dropdown-open?
           pro-tab-label
           pro-dropdown-options
           order-type-label]}]
  [:div {:class ["relative"]}
   (when pro-dropdown-open?
     [:div {:class ["fixed" "inset-0" "z-[180]"]
            :on {:click (cmd/close-pro-order-type-dropdown)}}])
   [:div {:class ["relative" "z-[190]" "flex" "items-center" "border-b" "border-base-300"]}
    (primitives/mode-button "Market"
                            (= entry-mode :market)
                            (cmd/select-entry-market))
    (primitives/mode-button "Limit"
                            (= entry-mode :limit)
                            (cmd/select-entry-limit))
    [:div {:class ["relative" "flex-1"]}
     [:button {:type "button"
               :class (into ["w-full"
                             "h-10"
                             "text-sm"
                             "font-medium"
                             "border-b-2"
                             "transition-colors"
                             "inline-flex"
                             "items-center"
                             "justify-center"
                             "gap-1.5"]
                            (if (= entry-mode :pro)
                              ["text-gray-100" "border-primary"]
                              ["text-gray-400" "border-transparent" "hover:text-gray-200"]))
               :on {:click (cmd/toggle-pro-order-type-dropdown)
                    :keydown (cmd/handle-pro-order-type-dropdown-keydown [:event/key])}}
      [:span pro-tab-label]
      [:svg {:class (into ["h-3.5" "w-3.5" "transition-transform"]
                          (if pro-dropdown-open?
                            ["rotate-180"]
                            ["rotate-0"]))
             :viewBox "0 0 20 20"
             :fill "currentColor"}
       [:path {:fill-rule "evenodd"
               :clip-rule "evenodd"
               :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"}]]]
     (when pro-dropdown-open?
       [:div {:class ["absolute"
                      "right-0"
                      "top-full"
                      "mt-1"
                      "w-36"
                      "overflow-hidden"
                      "rounded-lg"
                      "border"
                      "border-base-300"
                      "bg-base-100"
                      "shadow-lg"
                      "z-[210]"]}
        (for [pro-order-type pro-dropdown-options]
          ^{:key (name pro-order-type)}
          [:button {:type "button"
                    :class (into ["block"
                                  "w-full"
                                  "px-3"
                                  "py-2"
                                  "text-left"
                                  "text-sm"
                                  "transition-colors"]
                                 (if (= type pro-order-type)
                                   ["bg-base-200" "text-gray-100"]
                                   ["text-gray-300" "hover:bg-base-200" "hover:text-gray-100"]))
                    :on {:click (cmd/select-pro-order-type pro-order-type)}}
           (order-type-label pro-order-type)])])]]])

(defn tp-sl-panel [form]
  [:div {:class ["space-y-2"]}
   (primitives/row-toggle "Enable TP"
                          (get-in form [:tp :enabled?])
                          (cmd/toggle-tp-enabled))
   (when (get-in form [:tp :enabled?])
     [:div {:class ["space-y-2"]}
      (primitives/input (get-in form [:tp :trigger])
                        (cmd/set-tp-trigger-input)
                        :placeholder "TP trigger")
      (primitives/row-toggle "TP Market"
                             (get-in form [:tp :is-market])
                             (cmd/toggle-tp-market))
      (when (not (get-in form [:tp :is-market]))
        (primitives/input (get-in form [:tp :limit])
                          (cmd/set-tp-limit-input)
                          :placeholder "TP limit price"))])
   (primitives/row-toggle "Enable SL"
                          (get-in form [:sl :enabled?])
                          (cmd/toggle-sl-enabled))
   (when (get-in form [:sl :enabled?])
     [:div {:class ["space-y-2"]}
      (primitives/input (get-in form [:sl :trigger])
                        (cmd/set-sl-trigger-input)
                        :placeholder "SL trigger")
      (primitives/row-toggle "SL Market"
                             (get-in form [:sl :is-market])
                             (cmd/toggle-sl-market))
      (when (not (get-in form [:sl :is-market]))
        (primitives/input (get-in form [:sl :limit])
                          (cmd/set-sl-limit-input)
                          :placeholder "SL limit price"))])])

(defn tif-inline-control [form]
  [:div {:class ["relative" "flex" "items-center" "gap-2"]}
   [:span {:class ["text-xs" "uppercase" "tracking-wide" "text-gray-400"]} "TIF"]
   [:select {:class ["appearance-none"
                     "bg-transparent"
                     "text-sm"
                     "font-semibold"
                     "text-gray-100"
                     "outline-none"
                     "focus:outline-none"
                     "focus:ring-0"
                     "focus:ring-offset-0"
                     "focus:shadow-none"
                     "pr-4"]
             :value (name (:tif form))
             :on {:change (cmd/set-tif-input)}}
    [:option {:value "gtc"} "GTC"]
    [:option {:value "ioc"} "IOC"]
    [:option {:value "alo"} "ALO"]]
   [:svg {:class ["pointer-events-none"
                  "absolute"
                  "right-0"
                  "top-1/2"
                  "-translate-y-1/2"
                  "w-3.5"
                  "h-3.5"
                  "text-gray-400"]
          :viewBox "0 0 20 20"
          :fill "currentColor"}
    [:path {:fill-rule "evenodd"
            :clip-rule "evenodd"
            :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"}]]])

(def ^:private order-type-section-renderers
  {:trigger
   (fn [form]
     [:div
      (primitives/section-label "Trigger")
      (primitives/input (:trigger-px form)
                        (cmd/set-trigger-price-input)
                        :placeholder "Trigger price")])

   :scale
   (fn [form]
     [:div {:class ["space-y-2"]}
      (primitives/section-label "Scale")
      (primitives/input (get-in form [:scale :start])
                        (cmd/set-scale-start-input)
                        :placeholder "Start price")
      (primitives/input (get-in form [:scale :end])
                        (cmd/set-scale-end-input)
                        :placeholder "End price")
      [:div {:class ["grid" "grid-cols-2" "gap-2"]}
       (primitives/inline-labeled-scale-input "Total Orders"
                                              (get-in form [:scale :count])
                                              (cmd/set-scale-count-input))
       (primitives/inline-labeled-scale-input "Size Skew"
                                              (get-in form [:scale :skew])
                                              (cmd/set-scale-skew-input))]])

   :twap
   (fn [form]
     [:div {:class ["space-y-2"]}
      (primitives/section-label "TWAP")
      (primitives/input (get-in form [:twap :minutes])
                        (cmd/set-twap-minutes-input)
                        :placeholder "Minutes")
      (primitives/row-toggle "Randomize"
                             (get-in form [:twap :randomize])
                             (cmd/toggle-twap-randomize)
                             "trade-toggle-twap-randomize")])})

(defn render-order-type-section [section form]
  (when-let [renderer (get order-type-section-renderers section)]
    (renderer form)))
