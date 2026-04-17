(ns hyperopen.views.portfolio.volume-history-popover
  (:require [hyperopen.views.portfolio.format :as portfolio-format]
            [hyperopen.views.ui.anchored-popover :as anchored-popover]
            [hyperopen.views.ui.dialog-focus :as dialog-focus]))

(def ^:private preferred-width-px
  560)

(def ^:private estimated-height-px
  260)

(def ^:private trigger-selector
  "[data-role='portfolio-volume-history-trigger']")

(def ^:private focus-on-render
  (dialog-focus/dialog-focus-on-render
   {:restore-selector "[data-role=\"portfolio-volume-history-trigger\"]"}))

(defn- close-icon []
  [:svg {:class ["h-4" "w-4"]
         :fill "none"
         :stroke "currentColor"
         :viewBox "0 0 24 24"
         :aria-hidden true}
   [:path {:stroke-linecap "round"
           :stroke-linejoin "round"
           :stroke-width 2
           :d "M6 18 18 6M6 6l12 12"}]])

(defn- formatted-volume [value]
  (portfolio-format/format-currency value))

(defn- volume-cell [value]
  [:td {:class ["px-2" "py-2.5" "text-left" "align-middle" "sm:px-3"]}
   [:span {:class ["num" "text-xs" "font-medium" "tabular-nums" "text-trading-green" "sm:text-sm"]
           :style {:overflow-wrap "anywhere"}}
    (formatted-volume value)]])

(defn- total-row [{:keys [date-label
                          exchange-volume
                          weighted-maker-volume
                          weighted-taker-volume]}]
  [:tr {:data-role "portfolio-volume-history-total-row"}
   [:td {:class ["px-2" "py-2.5" "text-left" "align-middle" "text-xs" "font-medium" "text-trading-green" "sm:px-3" "sm:text-sm"]
         :style {:overflow-wrap "anywhere"}}
    (or date-label "Total")]
   (volume-cell exchange-volume)
   (volume-cell weighted-maker-volume)
   (volume-cell weighted-taker-volume)])

(defn- header-cell [label]
  [:th {:class ["px-2" "py-2.5" "font-normal" "leading-snug" "sm:px-3"]
        :style {:overflow-wrap "anywhere"}}
   label])

(defn- status-message [{:keys [loading? error]}]
  (cond
    loading?
    [:div {:class ["rounded-md"
                   "border"
                   "border-base-300"
                   "bg-base-200/70"
                   "px-3"
                   "py-2"
                   "text-xs"
                   "text-trading-text-secondary"]
           :role "status"
           :data-role "portfolio-volume-history-loading"}
     "Loading volume history..."]

    (seq error)
    [:div {:class ["rounded-md"
                   "border"
                   "border-error/35"
                   "bg-error/10"
                   "px-3"
                   "py-2"
                   "text-xs"
                   "text-error"]
           :data-role "portfolio-volume-history-error"}
     error]

    :else
    nil))

(defn- trigger-anchor-bounds
  []
  (let [document* (some-> js/globalThis .-document)
        target (some-> document* (.querySelector trigger-selector))]
    (when (and target (fn? (.-getBoundingClientRect target)))
      (let [rect (.getBoundingClientRect target)]
        {:left (.-left rect)
         :right (.-right rect)
         :top (.-top rect)
         :bottom (.-bottom rect)
         :width (.-width rect)
         :height (.-height rect)
         :viewport-width (some-> js/globalThis .-innerWidth)
         :viewport-height (some-> js/globalThis .-innerHeight)}))))

(defn- popover-style
  [anchor]
  (let [anchor* (if (anchored-popover/complete-anchor? anchor)
                  anchor
                  (trigger-anchor-bounds))]
    (if (anchored-popover/complete-anchor? anchor*)
      (anchored-popover/anchored-popover-layout-style
       {:anchor anchor*
        :preferred-width-px preferred-width-px
        :estimated-height-px estimated-height-px})
      {:left "12px"
       :top "12px"
       :width "min(calc(100vw - 24px), 560px)"})))

(defn volume-history-popover [{:keys [anchor open? rows] :as model}]
  (when open?
    (let [row-models (if (seq rows)
                       rows
                       [{:id :total
                         :date-label "Total"
                         :exchange-volume 0
                         :weighted-maker-volume 0
                         :weighted-taker-volume 0}])]
      [:div {:class ["fixed" "inset-0" "z-[90]" "pointer-events-none"]
             :data-role "portfolio-volume-history-layer"}
       [:button {:type "button"
                 :class ["absolute" "inset-0" "pointer-events-auto" "bg-transparent"]
                 :aria-label "Close volume history"
                 :data-role "portfolio-volume-history-backdrop"
                 :on {:click [[:actions/close-portfolio-volume-history]]}}]
       [:section {:class ["absolute"
                          "z-[91]"
                          "pointer-events-auto"
                          "rounded-lg"
                          "border"
                          "border-base-300"
                          "bg-base-100"
                          "p-4"
                          "shadow-2xl"
                          "outline-none"
                          "sm:p-5"]
                  :style (popover-style anchor)
                  :role "dialog"
                  :aria-labelledby "portfolio-volume-history-title"
                  :tab-index 0
                  :data-role "portfolio-volume-history-popover"
                  :replicant/on-render focus-on-render
                  :on {:keydown [[:actions/handle-portfolio-volume-history-keydown [:event/key]]]}}
        [:div {:class ["mb-4" "flex" "items-start" "justify-between" "gap-3"]}
         [:h2 {:id "portfolio-volume-history-title"
               :class ["text-lg" "font-semibold" "text-trading-text"]}
          "Your Volume History"]
         [:button {:type "button"
                   :class ["inline-flex"
                           "h-8"
                           "w-8"
                           "items-center"
                           "justify-center"
                           "rounded-md"
                           "text-trading-text-secondary"
                           "transition-colors"
                           "hover:bg-base-200"
                           "hover:text-trading-text"
                           "focus:outline-none"
                           "focus:ring-1"
                           "focus:ring-trading-green/40"]
                   :aria-label "Close volume history"
                   :data-role "portfolio-volume-history-close"
                   :on {:click [[:actions/close-portfolio-volume-history]]}}
          (close-icon)]]
        (status-message model)
        [:div {:class ["overflow-hidden" "rounded-md" "border" "border-base-300" "bg-base-200/45"]
               :data-role "portfolio-volume-history-table-frame"}
         [:table {:class ["w-full" "table-fixed" "border-collapse"]
                  :style {:table-layout "fixed"}
                  :data-role "portfolio-volume-history-table"}
          [:thead
           [:tr {:class ["text-left" "text-xs" "font-normal" "text-trading-text-secondary"]}
            (header-cell "Date (UTC)")
            (header-cell "Exchange Volume")
            (header-cell "Your Weighted Maker Volume")
            (header-cell "Your Weighted Taker Volume")]]
          (into [:tbody]
                (for [{:keys [id] :as row} row-models]
                  ^{:key (name (or id :total))}
                  (total-row row)))]]]])))
