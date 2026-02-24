(ns hyperopen.views.trade.order-form-component-sections
  (:require [clojure.string :as str]
            [hyperopen.views.trade.order-form-component-primitives :as primitives]
            [hyperopen.views.trade.order-form-type-extensions :as type-extensions]))

(defn entry-mode-tabs
  [{:keys [entry-mode
           type
           pro-dropdown-open?
           pro-tab-label
           pro-dropdown-options
           order-type-label]}
   {:keys [on-close-dropdown
           on-select-entry-market
           on-select-entry-limit
           on-toggle-dropdown
           on-dropdown-keydown
           on-select-pro-order-type]}]
  [:div {:class ["relative"]}
   (when pro-dropdown-open?
     [:div {:class ["fixed" "inset-0" "z-[180]"]
            :on {:click on-close-dropdown}}])
   [:div {:class ["relative" "z-[190]" "flex" "items-center" "border-b" "border-base-300"]}
    (primitives/mode-button "Market"
                            (= entry-mode :market)
                            on-select-entry-market)
    (primitives/mode-button "Limit"
                            (= entry-mode :limit)
                            on-select-entry-limit)
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
               :on {:click on-toggle-dropdown
                    :keydown on-dropdown-keydown}}
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
                    :on {:click (on-select-pro-order-type pro-order-type)}}
           (order-type-label pro-order-type)])])]]])

(defn tp-sl-panel
  [form {:keys [on-toggle-tp-enabled
                on-set-tp-trigger
                on-toggle-tp-market
                on-set-tp-limit
                on-toggle-sl-enabled
                on-set-sl-trigger
                on-toggle-sl-market
                on-set-sl-limit]}]
  [:div {:class ["space-y-2"]}
   (primitives/row-toggle "Enable TP"
                          (get-in form [:tp :enabled?])
                          on-toggle-tp-enabled)
   (when (get-in form [:tp :enabled?])
     [:div {:class ["space-y-2"]}
      (primitives/input (get-in form [:tp :trigger])
                        on-set-tp-trigger
                        :placeholder "TP trigger")
      (primitives/row-toggle "TP Market"
                             (get-in form [:tp :is-market])
                             on-toggle-tp-market)
      (when (not (get-in form [:tp :is-market]))
        (primitives/input (get-in form [:tp :limit])
                          on-set-tp-limit
                          :placeholder "TP limit price"))])
   (primitives/row-toggle "Enable SL"
                          (get-in form [:sl :enabled?])
                          on-toggle-sl-enabled)
   (when (get-in form [:sl :enabled?])
     [:div {:class ["space-y-2"]}
      (primitives/input (get-in form [:sl :trigger])
                        on-set-sl-trigger
                        :placeholder "SL trigger")
      (primitives/row-toggle "SL Market"
                             (get-in form [:sl :is-market])
                             on-toggle-sl-market)
      (when (not (get-in form [:sl :is-market]))
        (primitives/input (get-in form [:sl :limit])
                          on-set-sl-limit
                          :placeholder "SL limit price"))])])

(def ^:private tif-options
  [[:gtc "GTC"]
   [:ioc "IOC"]
   [:alo "ALO"]])

(defn- tif-option-row [selected-tif tif label on-select-tif]
  [:button {:type "button"
            :class (into ["flex"
                          "h-6"
                          "w-full"
                          "items-center"
                          "justify-start"
                          "text-left"
                          "text-xs"
                          "leading-6"
                          "transition-colors"]
                         (if (= selected-tif tif)
                           ["text-[#F6FEFD]"]
                           ["text-[#949E9C]" "hover:text-[#F6FEFD]"]))
            :role "option"
            :aria-selected (boolean (= selected-tif tif))
            :on {:click (on-select-tif tif)}}
   label])

(defn tif-inline-control
  [form {:keys [dropdown-open?
                on-toggle-dropdown
                on-close-dropdown
                on-dropdown-keydown
                on-select-tif]}]
  (let [selected-tif (keyword (name (or (:tif form) :gtc)))
        selected-label (some-> selected-tif name str/upper-case)
        open? (boolean dropdown-open?)]
    [:div {:class ["relative" "flex" "items-center" "gap-2"]
           :style (when open?
                    {:z-index 1200})}
     (when open?
       [:button {:type "button"
                 :class ["absolute" "bg-transparent" "cursor-default"]
                 :style {:left "-100vmax"
                         :top "-100vmax"
                         :width "200vmax"
                         :height "200vmax"
                         :z-index 1200}
                 :aria-label "Close TIF menu"
                 :on {:click on-close-dropdown}}])
     [:span {:class ["text-xs" "uppercase" "tracking-wide" "text-gray-400"]} "TIF"]
     [:button {:type "button"
               :class ["inline-flex"
                       "items-center"
                       "gap-1.5"
                       "bg-transparent"
                       "text-sm"
                       "font-normal"
                       "leading-6"
                       "text-[#949E9C]"
                       "transition-colors"
                       "duration-200"
                       "ease-in-out"
                       "hover:text-[#F6FEFD]"
                       "outline-none"
                       "focus:outline-none"
                       "focus:ring-0"
                       "focus:ring-offset-0"
                       "focus:shadow-none"]
               :aria-label "Time in force"
               :aria-haspopup "listbox"
               :aria-expanded open?
               :style (when open?
                        {:z-index 1201})
               :on {:click on-toggle-dropdown
                    :keydown on-dropdown-keydown}}
      [:span selected-label]
      [:svg {:class (into ["pointer-events-none"
                           "h-3.5"
                           "w-3.5"
                           "text-gray-400"
                           "transition-transform"
                           "duration-300"
                           "ease-out"]
                          (if open?
                            ["rotate-180"]
                            ["rotate-0"]))
             :viewBox "0 0 20 20"
             :fill "currentColor"}
       [:path {:fill-rule "evenodd"
               :clip-rule "evenodd"
               :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"}]]]
     [:div {:class (into ["absolute"
                          "right-0"
                          "top-full"
                          "mt-1"
                          "min-w-[46px]"
                          "rounded-lg"
                          "border"
                          "border-[#273035]"
                          "bg-[#1B2429]"
                          "px-2"
                          "py-1"
                          "origin-top-right"
                          "transition-all"
                          "duration-300"
                          "ease-out"
                          "shadow-[0_8px_16px_rgba(0,0,0,0.25)]"]
                         (if open?
                           ["visible"
                            "opacity-100"
                            "translate-y-0"
                            "pointer-events-auto"]
                           ["invisible"
                            "opacity-0"
                            "-translate-y-[5px]"
                            "pointer-events-none"]))
            :style {:z-index 1202}
            :role "listbox"
            :aria-label "TIF options"
            :aria-hidden (not open?)
            :on {:keydown on-dropdown-keydown}}
      (for [[tif label] tif-options]
        ^{:key (name tif)}
        (tif-option-row selected-tif tif label on-select-tif))]]))

(defn render-order-type-sections [order-type form callbacks]
  (type-extensions/render-order-type-sections order-type form callbacks))

(defn supported-order-type-sections []
  (type-extensions/supported-order-type-sections))
