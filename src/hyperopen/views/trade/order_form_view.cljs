(ns hyperopen.views.trade.order-form-view
  (:require [clojure.string :as str]
            [hyperopen.state.trading :as trading]
            [hyperopen.utils.formatting :as fmt]
            [hyperopen.views.trade.order-form-vm :as order-form-vm]))

(def neutral-input-focus-classes
  ["outline-none"
   "transition-[border-color,box-shadow]"
   "duration-150"
   "hover:border-[#6f7a88]"
   "hover:ring-1"
   "hover:ring-[#6f7a88]/30"
   "hover:ring-offset-0"
   "focus:outline-none"
   "focus:ring-1"
   "focus:ring-[#8a96a6]/40"
   "focus:ring-offset-0"
   "focus:shadow-none"
   "focus:border-[#8a96a6]"])

(defn- section-label [text]
  [:div {:class ["text-xs" "text-gray-400" "mb-1"]} text])

(defn- label->stable-id [label-text]
  (let [safe-label (or label-text "toggle")
        slug (-> safe-label
                 str/lower-case
                 (str/replace #"[^a-z0-9]+" "-")
                 (str/replace #"^-+|-+$" ""))]
    (if (seq slug) slug "toggle")))

(defn- row-toggle
  ([label-text checked? on-change]
   (row-toggle label-text checked? on-change nil))
  ([label-text checked? on-change toggle-id]
  (let [checkbox-id (or toggle-id
                        (str "trade-toggle-" (label->stable-id label-text)))]
    [:div {:class ["inline-flex" "items-center" "gap-2" "text-sm" "text-gray-100"]}
     [:input {:id checkbox-id
              :class ["h-4"
                      "w-4"
                      "rounded-[3px]"
                      "border"
                      "border-base-300"
                      "bg-transparent"
                      "trade-toggle-checkbox"
                      "transition-colors"
                      "focus:outline-none"
                      "focus:ring-0"
                      "focus:ring-offset-0"
                      "focus:shadow-none"]
              :type "checkbox"
              :checked (boolean checked?)
              :on {:change on-change}}]
     [:label {:for checkbox-id
              :class ["cursor-pointer" "select-none"]}
      label-text]])))

(defn- input [value on-change & {:keys [type placeholder]}]
  [:input {:class (into ["w-full"
                         "h-10"
                         "px-3"
                         "bg-base-200"
                         "border"
                         "border-base-300"
                         "rounded-lg"
                         "text-sm"
                         "text-right"
                         "text-gray-100"
                         "num"
                         "placeholder:text-gray-500"]
                        neutral-input-focus-classes)
           :type (or type "text")
           :placeholder (or placeholder "")
           :value (or value "")
           :on {:input on-change}}])

(defn- row-input [value placeholder on-change accessory & {:keys [input-padding-right on-focus on-blur]
                                                           :or {input-padding-right "pr-20"}}]
  (let [input-events (cond-> {:input on-change}
                       on-focus (assoc :focus on-focus)
                       on-blur (assoc :blur on-blur))]
    [:div {:class ["relative" "w-full"]}
     [:span {:class ["order-row-input-label"
                     "pointer-events-none"
                     "absolute"
                     "left-3"
                     "top-1/2"
                     "-translate-y-1/2"
                     "max-w-[52%]"
                     "truncate"
                     "text-sm"
                     "text-gray-500"]}
      placeholder]
     [:input {:class (into ["w-full"
                            "h-11"
                            "pl-24"
                            "bg-base-200"
                            "border"
                            "border-base-300"
                            "rounded-lg"
                            "text-sm"
                            "text-right"
                            "text-gray-100"
                            "num"
                            "placeholder:text-transparent"
                            "appearance-none"]
                           (concat neutral-input-focus-classes
                                   (if accessory [input-padding-right] ["pr-3"])))
              :type "text"
              :aria-label placeholder
              :placeholder placeholder
              :value (or value "")
              :on input-events}]
     (when accessory
       [:div {:class ["absolute"
                      "right-3"
                      "top-1/2"
                      "-translate-y-1/2"
                      "shrink-0"]}
        accessory])]))

(defn- inline-labeled-scale-input [label value on-change]
  [:div {:class ["relative" "w-full"]}
   [:span {:class ["pointer-events-none"
                   "absolute"
                   "left-3"
                   "top-1/2"
                   "-translate-y-1/2"
                   "text-sm"
                   "text-gray-400"
                   "truncate"
                   "max-w-[55%]"]}
    label]
   [:input {:class (into ["w-full"
                          "h-10"
                          "bg-base-200"
                          "border"
                          "border-base-300"
                          "rounded-lg"
                          "text-right"
                          "text-sm"
                          "font-semibold"
                          "text-gray-100"
                          "num"
                          "appearance-none"
                          "pl-24"
                          "pr-3"]
                         neutral-input-focus-classes)
            :type "text"
            :aria-label label
            :value (or value "")
            :on {:input on-change}}]])

(defn- chip-button [label active? & {:keys [on-click disabled?]}]
  [:button {:type "button"
            :disabled (boolean disabled?)
            :class (into ["flex-1"
                          "h-10"
                          "rounded-lg"
                          "text-sm"
                          "font-semibold"
                          "transition-colors"]
                         (if active?
                           ["bg-base-200" "text-gray-100" "border" "border-base-300"]
                           ["bg-base-200/60" "text-gray-300" "border" "border-base-300/80"]))
            :on (when (and on-click (not disabled?))
                  {:click on-click})}
   label])

(defn- mode-button [label active? on-click]
  [:button {:type "button"
            :class (into ["flex-1"
                          "h-10"
                          "text-sm"
                          "font-medium"
                          "border-b-2"
                          "transition-colors"]
                         (if active?
                           ["text-gray-100" "border-primary"]
                           ["text-gray-400" "border-transparent" "hover:text-gray-200"]))
            :on {:click on-click}}
   label])

(defn- side-button [label side active? on-click]
  (let [active-classes (case side
                         :buy ["bg-[#50D2C1]" "text-[#0F1A1F]"]
                         :sell ["bg-[#ED7088]" "text-[#F6FEFD]"]
                         ["bg-primary" "text-primary-content"])]
    [:button {:type "button"
              :class (into ["flex-1"
                            "h-10"
                            "text-sm"
                            "font-semibold"
                            "rounded-md"
                            "transition-colors"]
                           (if active?
                             active-classes
                             ["bg-[#273035]" "text-[#F6FEFD]"]))
              :on {:click on-click}}
     label]))

(defn- entry-mode-tabs [{:keys [entry-mode type pro-dropdown-open? pro-tab-label pro-dropdown-options]}]
  [:div {:class ["relative"]}
   (when pro-dropdown-open?
     [:div {:class ["fixed" "inset-0" "z-[180]"]
            :on {:click [[:actions/close-pro-order-type-dropdown]]}}])
   [:div {:class ["relative" "z-[190]" "flex" "items-center" "border-b" "border-base-300"]}
    (mode-button "Market"
                 (= entry-mode :market)
                 [[:actions/select-order-entry-mode :market]])
    (mode-button "Limit"
                 (= entry-mode :limit)
                 [[:actions/select-order-entry-mode :limit]])
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
               :on {:click [[:actions/toggle-pro-order-type-dropdown]]
                    :keydown [[:actions/handle-pro-order-type-dropdown-keydown [:event/key]]]}}
      [:span pro-tab-label]
      [:svg {:class (into ["h-3.5" "w-3.5" "transition-transform"]
                          (if pro-dropdown-open?
                            ["rotate-180"]
                            ["rotate-0"]))
             :viewBox "0 0 20 20"
             :fill "currentColor"}
       [:path {:fill-rule "evenodd"
               :clip-rule "evenodd"
               :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"}]]
      ]
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
                    :on {:click [[:actions/select-pro-order-type pro-order-type]]}}
           (order-form-vm/order-type-label pro-order-type)])])]]])

(defn- format-usdc [value]
  (if (and (number? value) (not (js/isNaN value)))
    (str (.toLocaleString (js/Number. value) "en-US"
                          #js {:minimumFractionDigits 2
                               :maximumFractionDigits 2})
         " USDC")
    "N/A"))

(defn- format-position-label [position sz-decimals]
  (let [size (:abs-size position)
        coin (:coin position)]
    (if (and (number? size) (pos? size) (seq coin))
      (str (.toLocaleString (js/Number. size) "en-US"
                            #js {:minimumFractionDigits (or sz-decimals 4)
                                 :maximumFractionDigits (or sz-decimals 4)})
           " "
           coin)
      (str "0.0000 " (or coin "--")))))

(defn- format-percent
  ([value]
   (format-percent value 2))
  ([value decimals]
   (if (and (number? value) (not (js/isNaN value)))
     (str (fmt/safe-to-fixed value decimals) "%")
     "N/A")))

(defn- metric-row
  ([title value]
   (metric-row title value nil))
  ([title value value-class]
  [:div {:class ["flex" "items-center" "justify-between"]}
   [:span {:class ["text-sm" "text-gray-400"]} title]
   [:span {:class (into ["text-sm" "font-semibold" "num"]
                        (if (seq value-class)
                          [value-class]
                          ["text-gray-100"]))}
    value]]))

(defn- tp-sl-panel [form]
  [:div {:class ["space-y-2"]}
   (row-toggle "Enable TP"
               (get-in form [:tp :enabled?])
               [[:actions/update-order-form [:tp :enabled?] [:event.target/checked]]])
   (when (get-in form [:tp :enabled?])
     [:div {:class ["space-y-2"]}
      (input (get-in form [:tp :trigger])
             [[:actions/update-order-form [:tp :trigger] [:event.target/value]]]
             :placeholder "TP trigger")
      (row-toggle "TP Market"
                  (get-in form [:tp :is-market])
                  [[:actions/update-order-form [:tp :is-market] [:event.target/checked]]])
      (when (not (get-in form [:tp :is-market]))
        (input (get-in form [:tp :limit])
               [[:actions/update-order-form [:tp :limit] [:event.target/value]]]
               :placeholder "TP limit price"))])
   (row-toggle "Enable SL"
               (get-in form [:sl :enabled?])
               [[:actions/update-order-form [:sl :enabled?] [:event.target/checked]]])
   (when (get-in form [:sl :enabled?])
     [:div {:class ["space-y-2"]}
      (input (get-in form [:sl :trigger])
             [[:actions/update-order-form [:sl :trigger] [:event.target/value]]]
             :placeholder "SL trigger")
      (row-toggle "SL Market"
                  (get-in form [:sl :is-market])
                  [[:actions/update-order-form [:sl :is-market] [:event.target/checked]]])
      (when (not (get-in form [:sl :is-market]))
        (input (get-in form [:sl :limit])
               [[:actions/update-order-form [:sl :limit] [:event.target/value]]]
               :placeholder "SL limit price"))])])

(defn- quote-accessory [quote-symbol]
  [:div {:class ["flex" "items-center" "gap-1.5" "text-sm" "font-semibold" "text-gray-100"]}
   [:span quote-symbol]
   [:svg {:class ["w-3.5" "h-3.5" "text-gray-400"]
          :viewBox "0 0 20 20"
          :fill "currentColor"}
    [:path {:fill-rule "evenodd"
            :clip-rule "evenodd"
            :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"}]]])

(defn- price-context-accessory [state form]
  (let [{:keys [source]} (trading/mid-price-summary state form)
        mid-available? (= :mid source)]
    [:button {:type "button"
              :disabled (not mid-available?)
              :class (into ["text-xs" "font-semibold" "transition-colors"]
                           (if mid-available?
                             ["text-primary" "cursor-pointer" "hover:text-primary/80"]
                             ["text-gray-500" "cursor-default"]))
              :on (when mid-available?
                    {:click [[:actions/set-order-price-to-mid]]})}
     (if mid-available? "Mid" "Ref")]))

(defn- tif-inline-control [form]
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
             :on {:change [[:actions/update-order-form [:tif] [:event.target/value]]]}}
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

(defn- render-order-type-section [section form]
  (case section
    :trigger
    [:div
     (section-label "Trigger")
     (input (:trigger-px form)
            [[:actions/update-order-form [:trigger-px] [:event.target/value]]]
            :placeholder "Trigger price")]

    :scale
    [:div {:class ["space-y-2"]}
     (section-label "Scale")
     (input (get-in form [:scale :start])
            [[:actions/update-order-form [:scale :start] [:event.target/value]]]
            :placeholder "Start price")
     (input (get-in form [:scale :end])
            [[:actions/update-order-form [:scale :end] [:event.target/value]]]
            :placeholder "End price")
     [:div {:class ["grid" "grid-cols-2" "gap-2"]}
      (inline-labeled-scale-input "Total Orders"
                                  (get-in form [:scale :count])
                                  [[:actions/update-order-form [:scale :count] [:event.target/value]]])
      (inline-labeled-scale-input "Size Skew"
                                  (get-in form [:scale :skew])
                                  [[:actions/update-order-form [:scale :skew] [:event.target/value]]])]]

    :twap
    [:div {:class ["space-y-2"]}
     (section-label "TWAP")
     (input (get-in form [:twap :minutes])
            [[:actions/update-order-form [:twap :minutes] [:event.target/value]]]
            :placeholder "Minutes")
     (row-toggle "Randomize"
                 (get-in form [:twap :randomize])
                 [[:actions/update-order-form [:twap :randomize] [:event.target/checked]]]
                 "trade-toggle-twap-randomize")]

    nil))

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
                show-limit-like-controls?
                limit-like?
                spot?
                hip3?
                read-only?
                summary
                ui-leverage
                next-leverage
                size-percent
                display-size-percent
                notch-overlap-threshold
                size-display
                price
                quote-symbol
                scale-preview-lines
                order-value
                margin-required
                liq-price
                slippage-est
                slippage-max
                fees
                error
                submitting?
                submit]}
        (order-form-vm/order-form-vm state)
        normalized-form form
        available-to-trade (:available-to-trade summary)
        position (:current-position summary)
        sz-decimals (or (get-in state [:active-market :szDecimals]) 4)
        display-price (:display price)
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
       (chip-button "Cross" true :disabled? true)
       (chip-button (str ui-leverage "x")
                    true
                    :on-click [[:actions/set-order-ui-leverage next-leverage]])
       (chip-button "Classic" true :disabled? true)]

      (entry-mode-tabs {:entry-mode entry-mode
                        :type type
                        :pro-dropdown-open? pro-dropdown-open?
                        :pro-tab-label pro-tab-label
                        :pro-dropdown-options pro-dropdown-options})

      [:div {:class ["flex" "items-center" "gap-2" "bg-base-200" "rounded-md" "p-1"]}
       (side-button "Buy / Long"
                    :buy
                    (= side :buy)
                    [[:actions/update-order-form [:side] :buy]])
       (side-button "Sell / Short"
                    :sell
                    (= side :sell)
                    [[:actions/update-order-form [:side] :sell]])]

      [:div {:class ["space-y-1.5"]}
       [:div {:class ["flex" "items-center" "justify-between"]}
        [:span {:class ["text-sm" "text-gray-400"]} "Available to Trade"]
        [:span {:class ["text-sm" "font-semibold" "text-gray-100" "num"]}
         (format-usdc available-to-trade)]]
       [:div {:class ["flex" "items-center" "justify-between"]}
        [:span {:class ["text-sm" "text-gray-400"]} "Current position"]
        [:span {:class ["text-sm" "font-semibold" "text-gray-100" "num"]}
         (format-position-label position sz-decimals)]]]

      (when show-limit-like-controls?
        (row-input display-price
                   (str "Price (" quote-symbol ")")
                   [[:actions/update-order-form [:price] [:event.target/value]]]
                   (price-context-accessory state normalized-form)
                   :input-padding-right "pr-14"
                   :on-focus [[:actions/focus-order-price-input]]
                   :on-blur [[:actions/blur-order-price-input]]))

      (row-input size-display
                 "Size"
                 [[:actions/set-order-size-display [:event.target/value]]]
                 (quote-accessory quote-symbol))

      [:div {:class ["flex" "items-center" "gap-2"]}
       [:div {:class ["relative" "flex-1"]}
        [:input {:class ["order-size-slider" "range" "range-sm" "w-full" "relative" "z-20"]
                 :type "range"
                 :min 0
                 :max 100
                 :step 1
                 :style {:--order-size-slider-progress (str size-percent "%")}
                 :value size-percent
                 :on {:input [[:actions/set-order-size-percent [:event.target/value]]]}}]
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
                              neutral-input-focus-classes)
                 :type "text"
                 :inputmode "numeric"
                 :pattern "[0-9]*"
                 :value display-size-percent
                 :on {:input [[:actions/set-order-size-percent [:event.target/value]]]}}]
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
        (render-order-type-section section normalized-form))

      [:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
       (row-toggle "Reduce Only"
                   (:reduce-only normalized-form)
                   [[:actions/update-order-form [:reduce-only] [:event.target/checked]]])
       (when show-limit-like-controls?
         (tif-inline-control normalized-form))]

      (when (not= :scale type)
        (row-toggle "Take Profit / Stop Loss"
                    (:tpsl-panel-open? normalized-form)
                    [[:actions/toggle-order-tpsl-panel]]))

      (when (and (not= :scale type) (:tpsl-panel-open? normalized-form))
        (tp-sl-panel normalized-form))

      (when (and pro-mode? limit-like?)
        (row-toggle "Post Only"
                    (:post-only normalized-form)
                    [[:actions/update-order-form [:post-only] [:event.target/checked]]]))

      [:div {:class ["flex-1"]}]

      (when (= :scale type)
        [:div {:class ["space-y-1.5"]}
         (metric-row "Start" start-preview-line)
         (metric-row "End" end-preview-line)])

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
                 :on {:click [[:actions/submit-order]]}}
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
         (metric-row "Liquidation Price"
                     (if liq-price
                       (or (fmt/format-trade-price liq-price) "N/A")
                       "N/A")))
       (metric-row "Order Value"
                   (if order-value
                     (or (fmt/format-currency order-value) "N/A")
                     "N/A"))
       (metric-row "Margin Required"
                   (if margin-required
                     (or (fmt/format-currency margin-required) "N/A")
                     "N/A"))
       (when (= :market type)
         (metric-row "Slippage"
                     (str "Est " (format-percent slippage-est 4)
                          " / Max " (format-percent slippage-max 2))
                     "text-primary"))
       (metric-row "Fees"
                   (if (and (number? (:taker fees)) (number? (:maker fees)))
                     (str (fmt/safe-to-fixed (:taker fees) 3)
                          "% / "
                          (fmt/safe-to-fixed (:maker fees) 3)
                          "%")
                     "N/A"))]]]))
