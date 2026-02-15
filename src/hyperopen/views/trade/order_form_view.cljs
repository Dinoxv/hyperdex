(ns hyperopen.views.trade.order-form-view
  (:require [hyperopen.views.trade.order-form-commands :as cmd]
            [hyperopen.views.trade.order-form-component-primitives :as primitives]
            [hyperopen.views.trade.order-form-component-sections :as sections]
            [hyperopen.views.trade.order-form-vm :as order-form-vm]))

(defn- price-context-accessory [{:keys [label mid-available?]}]
  [:button {:type "button"
            :disabled (not mid-available?)
            :class (into ["text-xs" "font-semibold" "transition-colors"]
                         (if mid-available?
                           ["text-primary" "cursor-pointer" "hover:text-primary/80"]
                           ["text-gray-500" "cursor-default"]))
            :on (when mid-available?
                  {:click (cmd/set-order-price-to-mid)})}
   (or label "Ref")])

(defn order-form-view [state]
  (let [{:keys [form
                side
                type
                entry-mode
                pro-dropdown-open?
                pro-dropdown-options
                pro-tab-label
                order-type-sections
                pro-mode?
                tpsl-panel-open?
                show-limit-like-controls?
                limit-like?
                spot?
                hip3?
                read-only?
                display
                ui-leverage
                next-leverage
                size-percent
                display-size-percent
                notch-overlap-threshold
                size-display
                price
                quote-symbol
                scale-preview-lines
                error
                submitting?
                submit]}
        (order-form-vm/order-form-vm state)
        display-price (:display price)
        price-context (:context price)
        start-preview-line (:start scale-preview-lines)
        end-preview-line (:end scale-preview-lines)
        submit-tooltip (:tooltip submit)
        submit-disabled? (:disabled? submit)]
    [:div {:class ["bg-base-100"
                   "border"
                   "border-base-300"
                   "rounded-none"
                   "shadow-none"
                   "p-3"
                   "font-sans"
                   "min-h-[560px]"
                   "xl:min-h-[640px]"
                   "flex"
                   "flex-col"
                   "gap-3"]}
     (when spot?
       [:div {:class ["px-3" "py-2" "bg-base-200" "border" "border-base-300" "rounded-lg" "text-xs" "text-gray-300"]}
        "Spot trading is not supported yet. You can still view spot charts and order books."])
     (when hip3?
       [:div {:class ["px-3" "py-2" "bg-base-200" "border" "border-base-300" "rounded-lg" "text-xs" "text-gray-300"]}
        "HIP-3 trading is not supported yet. You can still view these markets."])

     [:div {:class (into ["flex" "flex-col" "flex-1" "gap-3"]
                         (when read-only? ["opacity-60" "pointer-events-none"]))}
     [:div {:class ["grid" "grid-cols-3" "gap-2"]}
       (primitives/chip-button "Cross" true :disabled? true)
       (primitives/chip-button (str ui-leverage "x")
                               true
                               :on-click (cmd/set-order-ui-leverage next-leverage))
       (primitives/chip-button "Classic" true :disabled? true)]

      (sections/entry-mode-tabs {:entry-mode entry-mode
                                 :type type
                                 :pro-dropdown-open? pro-dropdown-open?
                                 :pro-tab-label pro-tab-label
                                 :pro-dropdown-options pro-dropdown-options
                                 :order-type-label order-form-vm/order-type-label})

      [:div {:class ["flex" "items-center" "gap-2" "bg-base-200" "rounded-md" "p-1"]}
       (primitives/side-button "Buy / Long"
                               :buy
                               (= side :buy)
                               (cmd/set-order-side :buy))
       (primitives/side-button "Sell / Short"
                               :sell
                               (= side :sell)
                               (cmd/set-order-side :sell))]

      [:div {:class ["space-y-1.5"]}
       [:div {:class ["flex" "items-center" "justify-between"]}
        [:span {:class ["text-sm" "text-gray-400"]} "Available to Trade"]
        [:span {:class ["text-sm" "font-semibold" "text-gray-100" "num"]}
         (:available-to-trade display)]]
       [:div {:class ["flex" "items-center" "justify-between"]}
        [:span {:class ["text-sm" "text-gray-400"]} "Current position"]
        [:span {:class ["text-sm" "font-semibold" "text-gray-100" "num"]}
         (:current-position display)]]]

      (when show-limit-like-controls?
        (primitives/row-input display-price
                              (str "Price (" quote-symbol ")")
                              (cmd/set-limit-price-input)
                              (price-context-accessory price-context)
                              :input-padding-right "pr-14"
                              :on-focus (cmd/focus-order-price-input)
                              :on-blur (cmd/blur-order-price-input)))

      (primitives/row-input size-display
                            "Size"
                            (cmd/set-order-size-display-input)
                            (primitives/quote-accessory quote-symbol))

      [:div {:class ["flex" "items-center" "gap-2"]}
       [:div {:class ["relative" "flex-1"]}
        [:input {:class ["order-size-slider" "range" "range-sm" "w-full" "relative" "z-20"]
                 :type "range"
                 :min 0
                 :max 100
                 :step 1
                 :style {:--order-size-slider-progress (str size-percent "%")}
                 :value size-percent
                 :on {:input (cmd/set-order-size-percent-input)}}]
        [:div {:class ["order-size-slider-notches"
                       "pointer-events-none"
                       "absolute"
                       "inset-x-0"
                       "top-1/2"
                       "z-30"
                       "flex"
                       "items-center"
                       "justify-between"
                       "px-0.5"]}
         (for [pct [0 25 50 75 100]]
           ^{:key (str "size-slider-notch-" pct)}
           [:span {:class (into ["order-size-slider-notch"
                                 "block"
                                 "h-[7px]"
                                 "w-[7px]"
                                 "-translate-y-1/2"
                                 "rounded-full"]
                                (remove nil?
                                        [(if (>= size-percent pct)
                                           "order-size-slider-notch-active"
                                           "order-size-slider-notch-inactive")
                                         (when (<= (js/Math.abs (- size-percent pct))
                                                   notch-overlap-threshold)
                                           "opacity-0")]))}])]]
       [:div {:class ["relative" "w-[82px]"]}
        [:input {:class (into ["order-size-percent-input"
                               "h-10"
                               "w-full"
                               "bg-base-200/80"
                               "border"
                               "border-base-300"
                               "rounded-lg"
                               "text-right"
                               "text-sm"
                               "font-semibold"
                               "text-gray-100"
                               "num"
                               "appearance-none"
                               "pl-2.5"
                               "pr-6"]
                              primitives/neutral-input-focus-classes)
                 :type "text"
                 :inputmode "numeric"
                 :pattern "[0-9]*"
                 :value display-size-percent
                 :on {:input (cmd/set-order-size-percent-input)}}]
        [:span {:class ["pointer-events-none"
                        "absolute"
                        "right-2.5"
                        "top-1/2"
                        "-translate-y-1/2"
                        "text-sm"
                        "font-semibold"
                        "text-gray-300"]}
         "%"]]]

      (for [section order-type-sections]
        ^{:key (str "order-type-section-" (name section))}
        (sections/render-order-type-section section form))

      [:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
       (primitives/row-toggle "Reduce Only"
                              (:reduce-only form)
                              (cmd/toggle-reduce-only))
       (when show-limit-like-controls?
         (sections/tif-inline-control form))]

      (when (not= :scale type)
        (primitives/row-toggle "Take Profit / Stop Loss"
                               tpsl-panel-open?
                               (cmd/toggle-order-tpsl-panel)))

      (when (and (not= :scale type) tpsl-panel-open?)
        (sections/tp-sl-panel form))

      (when (and pro-mode? limit-like?)
        (primitives/row-toggle "Post Only"
                               (:post-only form)
                               (cmd/toggle-post-only)))

      [:div {:class ["flex-1"]}]

      (when (= :scale type)
        [:div {:class ["space-y-1.5"]}
         (primitives/metric-row "Start" start-preview-line)
         (primitives/metric-row "End" end-preview-line)])

      (when error
        [:div {:class ["text-xs" "text-red-400"]} error])

      [:div {:class ["relative" "group"]
             :tabindex (when (seq submit-tooltip) 0)}
       [:button {:type "button"
                 :class (into ["w-full"
                               "h-11"
                               "rounded-lg"
                               "text-sm"
                               "font-semibold"
                               "transition-colors"
                               "focus:outline-none"
                               "focus:ring-1"
                               "focus:ring-[#8a96a6]/40"
                               "focus:ring-offset-0"]
                              (if submit-disabled?
                                ["bg-[rgb(23,69,63)]"
                                 "text-[#7f9f9a]"
                                 "cursor-not-allowed"]
                                ["bg-primary"
                                 "text-primary-content"
                                 "hover:bg-primary/90"]))
                 :disabled submit-disabled?
                 :on {:click (cmd/submit-order)}}
        (if submitting? "Submitting..." "Place Order")]
       (when (seq submit-tooltip)
         [:div {:class ["order-submit-tooltip"
                        "pointer-events-none"
                        "absolute"
                        "left-0"
                        "right-0"
                        "bottom-full"
                        "mb-2"
                        "rounded-md"
                        "border"
                        "border-base-300"
                        "bg-base-200"
                        "px-2.5"
                        "py-2"
                        "text-xs"
                        "text-gray-200"
                        "shadow-lg"
                        "opacity-0"
                        "translate-y-1"
                        "transition-all"
                        "duration-150"
                        "group-hover:opacity-100"
                        "group-hover:translate-y-0"
                        "group-focus:opacity-100"
                        "group-focus:translate-y-0"]}
          submit-tooltip])]

      [:div {:class ["border-t" "border-base-300" "pt-3" "space-y-2"]}
       (when (not= :scale type)
         (primitives/metric-row "Liquidation Price"
                                (:liquidation-price display)))
       (primitives/metric-row "Order Value"
                              (:order-value display))
       (primitives/metric-row "Margin Required"
                              (:margin-required display))
       (when (= :market type)
         (primitives/metric-row "Slippage"
                                (:slippage display)
                                "text-primary"))
       (primitives/metric-row "Fees"
                              (:fees display))]]]))
