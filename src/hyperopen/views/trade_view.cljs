(ns hyperopen.views.trade-view
  (:require [hyperopen.surface-modules :as surface-modules]
            [hyperopen.trade-modules :as trade-modules]
            [hyperopen.views.active-asset.vm :as active-asset-vm]
            [hyperopen.views.active-asset-view :as active-asset-view]
            [hyperopen.views.asset-selector-view :as asset-selector-view]
            [hyperopen.views.ui.funding-modal-positioning :as funding-modal-positioning]
            [hyperopen.views.l2-orderbook-view :as l2-orderbook-view]
            [hyperopen.views.trade.order-form-view :as order-form-view]
            [hyperopen.views.trade-view.layout-state :as layout-state]))

(def ^:private trade-mobile-surfaces
  [[:chart "Chart"]
   [:orderbook "Order Book"]
   [:trades "Trades"]])

(def ^:private trade-chart-view-base-state-keys
  [:active-asset
   :active-market
   :account
   :asset-contexts
   :candles
   :chart-options
   :orders
   :perp-dex-clearinghouse
   :positions-ui
   :router
   :spot
   :trading-settings
   :trade-modules
   :webdata2])

(def ^:private account-info-view-base-state-keys
  [:account
   :account-info
   :orders
   :perp-dex-clearinghouse
   :positions-ui
   :spot
   :webdata2])

(def ^:private account-equity-view-state-keys
  [:account
   :perp-dex-clearinghouse
   :spot
   :webdata2])

(def ^:private order-form-view-state-keys
  [:account
   :active-asset
   :active-assets
   :active-market
   :asset-contexts
   :order-form
   :order-form-runtime
   :order-form-ui
   :orderbooks
   :perp-dex-clearinghouse
   :spot
   :wallet
   :webdata2])

(declare trade-chart-loading-shell)
(declare render-active-asset-panel-state
         desktop-secondary-panel-placeholder
         render-account-info-panel-state
         render-account-equity-panel-state
         render-account-equity-metrics-state
         trade-chart-panel-content-state)

(defn- memoize-last
  [f]
  (let [cache (atom nil)]
    (fn [& args]
      (let [cached @cache]
        (if (and (map? cached)
                 (= args (:args cached)))
          (:result cached)
          (let [result (apply f args)]
            (reset! cache {:args args
                           :result result})
            result))))))

(defn- select-view-state
  [state ks]
  (select-keys (or state {}) ks))

(defn- surface-freshness-cues-enabled?
  [state]
  (boolean (get-in state [:websocket-ui :show-surface-freshness-cues?] false)))

(defn- active-asset-view-state
  [state]
  (active-asset-vm/panel-dependency-state state))

(defn- asset-selector-market-lookup-state
  [state]
  {:asset-selector {:market-by-key (get-in state [:asset-selector :market-by-key] {})}})

(defn- trade-chart-view-state
  [state]
  (cond-> (merge (select-view-state state trade-chart-view-base-state-keys)
                 (asset-selector-market-lookup-state state))
    (surface-freshness-cues-enabled? state)
    (assoc :websocket (:websocket state)
           :websocket-ui (:websocket-ui state))))

(defn- account-info-view-state
  [state]
  (cond-> (merge (select-view-state state account-info-view-base-state-keys)
                 (asset-selector-market-lookup-state state))
    (surface-freshness-cues-enabled? state)
    (assoc :websocket (:websocket state)
           :websocket-ui (:websocket-ui state))))

(defn- account-equity-view-state
  [state]
  (merge (select-view-state state account-equity-view-state-keys)
         (asset-selector-market-lookup-state state)))

(defn- order-form-view-state
  [state]
  (select-view-state state order-form-view-state-keys))

(defn- account-surface-export
  [export-id]
  (surface-modules/resolved-surface-export :account-surfaces export-id))

(def ^:private memoized-active-asset-view
  (memoize-last (fn [render-fn state]
                  (render-fn state))))

(def ^:private memoized-trade-chart-panel-content
  (memoize-last (fn [render-fn state]
                  (or (render-fn state)
                      (trade-chart-loading-shell state)))))

(def ^:private memoized-account-info-view
  (memoize-last (fn [render-fn state opts]
                  (render-fn state opts))))

(def ^:private memoized-account-equity-view
  (memoize-last (fn [render-fn state opts]
                  (render-fn state opts))))

(def ^:private memoized-orderbook-view
  (memoize-last (fn [render-fn state]
                  (render-fn state))))

(def ^:private memoized-order-form-view
  (memoize-last (fn [render-fn state]
                  (render-fn state))))

(def ^:private memoized-account-equity-metrics
  (memoize-last (fn [metrics-fn state]
                  (metrics-fn state))))

(defonce ^:private frozen-trade-chart-view-state* (atom nil))

(defonce ^:private frozen-active-asset-view-state* (atom nil))

(defonce ^:private frozen-account-info-view-state* (atom nil))

(defonce ^:private frozen-account-equity-view-state* (atom nil))

(defonce ^:private frozen-orderbook-view-state* (atom nil))

(defonce ^:private frozen-order-form-view-state* (atom nil))

(defn- viewport-width-px []
  (some-> js/globalThis .-innerWidth))

(defn- mobile-surface-button
  [selected-surface [surface-id label]]
  [:button {:type "button"
            :data-role (str "trade-mobile-surface-button-" (name surface-id))
            :class (into ["flex-1"
                          "border-b-2"
                          "px-2"
                          "py-2"
                          "text-sm"
                          "font-medium"
                          "transition-colors"
                          "focus:outline-none"
                          "focus:ring-0"
                          "focus:ring-offset-0"]
                         (if (= selected-surface surface-id)
                           ["border-primary" "text-trading-text"]
                           ["border-transparent" "text-trading-text-secondary" "hover:text-trading-text"]))
            :on {:click [[:actions/select-trade-mobile-surface surface-id]]}}
   label])

(defn- mobile-orderbook-view-state
  [orderbook-view-state mobile-surface]
  (assoc orderbook-view-state
         :show-tabs? false
         :active-tab-override (if (= mobile-surface :trades)
                                :trades
                                :orderbook)))

(defn- render-active-asset-panel
  [state]
  (render-active-asset-panel-state (active-asset-view-state state)))

(defn- render-active-asset-panel-state
  [view-state]
  (memoized-active-asset-view active-asset-view/active-asset-view
                              view-state))

(defn- render-account-info-panel
  ([state]
   (render-account-info-panel state {}))
  ([state opts]
   (render-account-info-panel-state (account-info-view-state state) opts)))

(defn- render-account-info-panel-state
  [view-state opts]
  (when-let [render-fn (account-surface-export :account-info-view)]
    (memoized-account-info-view render-fn
                                view-state
                                opts)))

(defn- render-account-equity-panel
  [state equity-metrics opts]
  (render-account-equity-panel-state (account-equity-view-state state)
                                     equity-metrics
                                     opts))

(defn- render-account-equity-panel-state
  [view-state equity-metrics opts]
  (when-let [render-fn (account-surface-export :account-equity-view)]
    (memoized-account-equity-view render-fn
                                  view-state
                                  (assoc opts :metrics equity-metrics))))

(defn- render-account-equity-metrics
  [state]
  (render-account-equity-metrics-state (account-equity-view-state state)))

(defn- render-account-equity-metrics-state
  [view-state]
  (when-let [metrics-fn (account-surface-export :account-equity-metrics)]
    (memoized-account-equity-metrics metrics-fn
                                     view-state)))

(defn- trade-chart-panel-content
  [state]
  (trade-chart-panel-content-state (trade-chart-view-state state)))

(defn- trade-chart-panel-content-state
  [view-state]
  (memoized-trade-chart-panel-content trade-modules/render-trade-chart-view
                                      view-state))

(defn- render-orderbook-panel
  [state]
  (memoized-orderbook-view l2-orderbook-view/l2-orderbook-view
                           state))

(defn- render-order-form-panel
  [state]
  (memoized-order-form-view order-form-view/order-form-view
                            state))

(defn- freeze-heavy-trade-panels?
  [state desktop-layout?]
  (and desktop-layout?
       (= :asset-selector (get-in state [:asset-selector :visible-dropdown]))
       (asset-selector-view/asset-list-freeze-active?)))

(defn- selector-scroll-snapshot
  [snapshot* freeze? next-state-fn]
  (if freeze?
    (let [snapshot @snapshot*]
      (if (some? snapshot)
        snapshot
        (do
          (let [next-state (next-state-fn)]
            (reset! snapshot* next-state)
            next-state))))
    (do
      (let [next-state (next-state-fn)]
        (reset! snapshot* next-state)
        next-state))))

(defn- orderbook-view-state
  [state active-asset orderbook-data show-surface-freshness-cues? websocket-health]
  {:coin (or active-asset "No Asset Selected")
   :market (:active-market state)
   :orderbook orderbook-data
   :orderbook-ui (:orderbook-ui state)
   :trading-settings (:trading-settings state)
   :show-surface-freshness-cues? show-surface-freshness-cues?
   :websocket-health (when show-surface-freshness-cues?
                       websocket-health)
   :loading (and active-asset (nil? orderbook-data))})

(defn- mobile-account-surface [state equity-metrics]
  (let [account-equity-panel (render-account-equity-panel state
                                                          equity-metrics
                                                          {:fill-height? false
                                                           :show-funding-actions? false})
        funding-actions-view (account-surface-export :funding-actions-view)]
    (if (and account-equity-panel
             (fn? funding-actions-view))
      [:div {:class ["flex" "h-full" "min-h-0" "flex-col" "bg-base-100"]
             :data-parity-id "trade-mobile-account-surface"}
       account-equity-panel
       (funding-actions-view
        state
        {:container-classes ["mt-auto"
                             "border-t"
                             "border-base-300"
                             "bg-base-100"
                             "px-3"
                             "pt-2"
                             "pb-1.5"
                             "space-y-2"]
         :data-parity-id "trade-mobile-account-actions"})]
      (desktop-secondary-panel-placeholder "Account"
                                           "trade-mobile-account-surface-placeholder"
                                           :fill-height? true))))

(defn- trade-chart-loading-shell
  [state]
  (let [error-message (trade-modules/trade-chart-error state)
        route (get-in state [:router :path] "/trade")
        selected-timeframe (get-in state [:chart-options :selected-timeframe] :1d)]
    [:div {:class ["w-full" "h-full" "min-h-0" "min-w-0" "overflow-hidden"]
           :data-parity-id "trade-chart-module-shell"}
     [:div {:class ["w-full" "h-full" "flex" "flex-col" "min-h-0" "min-w-0" "overflow-hidden"]}
      [:div {:class ["flex"
                     "items-center"
                     "border-b"
                     "border-gray-700"
                     "px-4"
                     "pt-2"
                     "pb-1"
                     "w-full"
                     "min-w-0"
                     "space-x-4"
                     "bg-base-100"]
             :data-role "trade-chart-shell-toolbar"}
       [:div {:class ["flex" "items-center" "space-x-1"]}
        (for [timeframe [:5m :1h :1d]]
          ^{:key (str "trade-chart-shell-timeframe-" (name timeframe))}
          [:div {:class (if (= timeframe selected-timeframe)
                          ["px-3"
                           "py-1"
                           "text-sm"
                           "font-medium"
                           "rounded"
                           "text-trading-green"]
                          ["px-3"
                           "py-1"
                           "text-sm"
                           "font-medium"
                           "rounded"
                           "text-gray-300"
                           "bg-base-200/70"])
                 :data-role (str "trade-chart-shell-timeframe-" (name timeframe))}
           (name timeframe)])
        [:div {:class ["flex"
                       "items-center"
                       "px-3"
                       "py-1"
                       "text-sm"
                       "font-medium"
                       "rounded"
                       "text-gray-300"]
               :data-role "trade-chart-shell-timeframe-dropdown"}
         [:span "▼"]]]
       [:div {:class ["w-px" "h-6" "bg-gray-700"]
              :data-role "trade-chart-shell-divider"}]
       [:div {:class ["flex" "items-center" "gap-1"]
              :data-role "trade-chart-shell-controls"}
        [:div {:class ["h-6" "w-6" "rounded" "bg-base-200/60"]
               :data-role "trade-chart-shell-chart-type"}]
        [:div {:class ["w-px" "h-6" "bg-gray-700"]
               :data-role "trade-chart-shell-divider"}]
        [:div {:class ["flex"
                       "items-center"
                       "gap-1.5"
                       "h-8"
                       "px-3"
                       "text-base"
                       "font-medium"
                       "rounded-none"
                       "text-gray-300"
                       "bg-gray-900/40"]
               :data-role "trade-chart-shell-indicators"}
         [:span "Indicators"]]]]
      [:div {:class ["w-full"
                     "relative"
                     "flex-1"
                     "min-h-[360px]"
                     "min-w-0"
                     "bg-base-100"
                     "trading-chart-host"]}
       [:div {:class ["absolute"
                      "inset-0"
                      "flex"
                      "items-center"
                      "justify-center"
                      "px-6"
                      "py-10"]}
        [:div {:class ["flex"
                       "max-w-md"
                       "flex-col"
                       "items-center"
                       "gap-3"
                       "text-center"]}
         [:div {:class ["text-sm"
                        "font-semibold"
                        "uppercase"
                        "tracking-[0.12em]"
                        "text-trading-text-secondary"]}
          (if error-message
            "Chart Load Failed"
            "Loading Chart")]
         [:p {:class ["text-sm" "text-trading-text-secondary"]}
          (or error-message
              "Loading the trade chart on demand to keep the initial trade bundle smaller.")]
         (when error-message
           [:button {:type "button"
                     :class ["rounded-lg"
                             "border"
                             "border-base-300"
                             "px-3"
                             "py-2"
                             "text-sm"
                             "font-medium"
                             "text-trading-text"
                             "transition-colors"
                             "hover:border-primary"
                             "hover:text-primary"]
                     :on {:click [[:actions/navigate route {:replace? true}]]}}
            "Retry"])]]]]]))

(defn- trade-view-layout-context
  [state]
  (let [funding-tooltip-open? (true? (active-asset-vm/active-asset-funding-tooltip-open? state))
        layout (layout-state/trade-layout-state state
                                               (viewport-width-px)
                                               funding-tooltip-open?)]
    {:desktop-layout? (:desktop-layout? layout)
     :funding-tooltip-open? funding-tooltip-open?
     :mobile-surface (:mobile-surface layout)
     :layout layout}))

(defn- desktop-secondary-panels-ready?
  [state desktop-layout?]
  (or (not desktop-layout?)
      (not= false (get-in state [:trade-ui :desktop-secondary-panels-ready?]))))

(defn- desktop-secondary-panel-placeholder
  [title data-role & {:keys [fill-height?]
                      :or {fill-height? false}}]
  [:div {:class (into ["w-full"
                       "min-h-0"
                       "overflow-hidden"
                       "bg-base-100"
                       "p-3"
                       "space-y-3"]
                      (cond
                        fill-height?
                        ["h-full"]

                        :else
                        ["min-h-[9rem]"]))
         :data-role data-role}
   [:div {:class ["text-sm" "font-semibold" "text-trading-text-secondary"]}
    title]
   [:div {:class ["space-y-2"]}
    [:div {:class ["h-3" "w-28" "rounded" "bg-base-300/60"]}]
    [:div {:class ["h-3" "w-full" "rounded" "bg-base-300/50"]}]
    [:div {:class ["h-3" "w-5/6" "rounded" "bg-base-300/40"]}]
    [:div {:class ["h-3" "w-2/3" "rounded" "bg-base-300/30"]}]]])

(defn- trade-view-panel-context
  [state {:keys [desktop-layout? layout mobile-surface]}]
  (let [active-asset (:active-asset state)
        orderbook-data (when active-asset (get-in state [:orderbooks active-asset]))
        freeze-heavy-panels? (freeze-heavy-trade-panels? state desktop-layout?)
        desktop-secondary-panels-ready?* (desktop-secondary-panels-ready? state desktop-layout?)
        active-asset-panel-state (selector-scroll-snapshot
                                  frozen-active-asset-view-state*
                                  freeze-heavy-panels?
                                  #(active-asset-view-state state))
        trade-chart-panel-state (selector-scroll-snapshot
                                 frozen-trade-chart-view-state*
                                 freeze-heavy-panels?
                                 #(trade-chart-view-state state))
        account-info-panel-state (when desktop-secondary-panels-ready?*
                                  (selector-scroll-snapshot
                                   frozen-account-info-view-state*
                                   freeze-heavy-panels?
                                   #(account-info-view-state state)))
        account-equity-panel-state (when (and (:show-equity-surface? layout)
                                              desktop-secondary-panels-ready?*)
                                    (selector-scroll-snapshot
                                     frozen-account-equity-view-state*
                                     freeze-heavy-panels?
                                     #(account-equity-view-state state)))
        show-surface-freshness-cues? (surface-freshness-cues-enabled? state)
        websocket-health (get-in state [:websocket :health])
        equity-metrics (when (and (:show-equity-surface? layout)
                                  desktop-secondary-panels-ready?*)
                         (render-account-equity-metrics-state account-equity-panel-state))
        orderbook-panel-state (selector-scroll-snapshot
                               frozen-orderbook-view-state*
                               freeze-heavy-panels?
                               #(orderbook-view-state state
                                                     active-asset
                                                     orderbook-data
                                                     show-surface-freshness-cues?
                                                     websocket-health))
        order-form-panel-state (selector-scroll-snapshot
                                frozen-order-form-view-state*
                                freeze-heavy-panels?
                                #(order-form-view-state state))]
    {:active-asset-panel-state active-asset-panel-state
     :trade-chart-panel-state trade-chart-panel-state
     :desktop-secondary-panels-ready? desktop-secondary-panels-ready?*
     :account-info-panel-state account-info-panel-state
     :account-equity-panel-state account-equity-panel-state
     :equity-metrics equity-metrics
     :orderbook-panel-state orderbook-panel-state
     :order-form-panel-state order-form-panel-state
     :mobile-orderbook-panel-state (when-not desktop-layout?
                                    (mobile-orderbook-view-state orderbook-panel-state mobile-surface))}))

(defn- render-mobile-active-asset-strip
  [state {:keys [layout]}]
  [:div {:class (:mobile-active-asset-strip-classes layout)
         :data-parity-id "trade-mobile-active-asset-strip"}
   (when (:show-mobile-active-asset? layout)
     (render-active-asset-panel state))])

(defn- render-mobile-surface-tabs
  [mobile-surface {:keys [layout]}]
  [:div {:class (:mobile-surface-tabs-classes layout)
         :data-parity-id "trade-mobile-surface-tabs"}
   [:div {:class ["flex" "items-center" "gap-0"]}
    (for [[surface-id _label :as surface] trade-mobile-surfaces]
      ^{:key (str "trade-mobile-surface-" (name surface-id))}
      (mobile-surface-button mobile-surface surface))]])

(defn- render-trade-chart-panel
  [desktop-layout?
   {:keys [layout]}
   {:keys [active-asset-panel-state trade-chart-panel-state]}]
  [:div {:class (:chart-panel-classes layout)
         :data-parity-id "trade-chart-panel"}
   [:div {:class (:desktop-active-asset-shell-classes layout)}
    (when desktop-layout?
      (render-active-asset-panel-state active-asset-panel-state))]
   (when (:chart-panel-visible? layout)
     [:div {:class ["overflow-hidden" "flex-1" "min-h-0" "min-w-0"]}
      (trade-chart-panel-content-state trade-chart-panel-state)])])

(defn- render-orderbook-panel-shell
  [desktop-layout?
   {:keys [layout]}
   {:keys [orderbook-panel-state mobile-orderbook-panel-state]}]
  [:div {:class (:orderbook-panel-classes layout)
         :data-parity-id "trade-orderbook-panel"}
   [:div {:class ["h-full" "min-h-0" "lg:hidden"]}
    (when (and (:orderbook-panel-visible? layout)
               (not desktop-layout?))
      (render-orderbook-panel mobile-orderbook-panel-state))]
   [:div {:class ["hidden" "h-full" "min-h-0" "lg:block"]}
    (when (and (:orderbook-panel-visible? layout)
               desktop-layout?)
      (render-orderbook-panel orderbook-panel-state))]])

(defn- render-order-entry-panel-shell
  [desktop-layout?
   {:keys [layout]}
   {:keys [account-equity-panel-state
           desktop-secondary-panels-ready?
           equity-metrics
           order-form-panel-state]}]
  [:div {:class (:order-entry-panel-classes layout)
         :data-parity-id funding-modal-positioning/trade-order-entry-panel-parity-id}
   (when (:order-entry-panel-visible? layout)
     (render-order-form-panel order-form-panel-state))
   [:div {:class ["hidden" "border-t" "border-base-300" "lg:block"]
          :data-parity-id "trade-desktop-account-equity-panel"}
     (when (and desktop-layout?
                (:order-entry-panel-visible? layout))
      (if desktop-secondary-panels-ready?
        (or (render-account-equity-panel-state account-equity-panel-state equity-metrics {})
            (desktop-secondary-panel-placeholder "Account Equity"
                                                 "trade-desktop-account-equity-placeholder"))
        (desktop-secondary-panel-placeholder "Account Equity"
                                             "trade-desktop-account-equity-placeholder")))]])

(defn- render-account-panel-shell
  [state
   desktop-layout?
   {:keys [layout]}
   {:keys [account-info-panel-state desktop-secondary-panels-ready?]}]
  [:div {:class (:account-panel-classes layout)
         :data-parity-id "trade-account-tables-panel"}
   [:div {:class ["w-full" "lg:hidden"]
          :data-parity-id "trade-mobile-account-panel"}
    (when (and (:account-panel-visible? layout)
               (not desktop-layout?))
      (or (render-account-info-panel state)
          (desktop-secondary-panel-placeholder "Account"
                                               "trade-mobile-account-panel-placeholder"
                                               :fill-height? true)))]
   [:div {:class ["hidden" "w-full" "min-h-0" "lg:flex"]
          :data-parity-id "trade-desktop-account-panel"}
    (when (and (:account-panel-visible? layout)
               desktop-layout?)
      (if desktop-secondary-panels-ready?
        (or (render-account-info-panel-state account-info-panel-state
                                             {:default-panel-classes ["h-full"]})
            (desktop-secondary-panel-placeholder "Account"
                                                 "trade-desktop-account-panel-placeholder"
                                                 :fill-height? true))
        (desktop-secondary-panel-placeholder "Account"
                                             "trade-desktop-account-panel-placeholder"
                                             :fill-height? true)))]])

(defn- render-mobile-account-summary
  [state {:keys [layout]} {:keys [equity-metrics]}]
  (when (:mobile-account-summary-visible? layout)
    [:div {:class (:mobile-account-summary-classes layout)
           :data-parity-id "trade-mobile-account-summary-panel"}
     (mobile-account-surface state equity-metrics)]))

(defn- render-trade-grid
  [state
   {:keys [desktop-layout? layout] :as layout-context}
   panel-context]
  [:div {:class ["relative" "flex-1" "min-h-0"]}
   [:div {:class ["hidden" "xl:block" "absolute" "top-0" "bottom-0" "right-[320px]" "w-px" "bg-base-300" "pointer-events-none" "z-10"]}]
   [:div {:class ["grid"
                  "h-full"
                  "min-h-0"
                  "grid-cols-1"
                  "gap-x-0" "gap-y-0"
                  "bg-base-100"
                  "items-stretch"
                  "lg:h-full"
                  "lg:grid-cols-[minmax(0,1fr)_320px]"
                  "xl:grid-cols-[minmax(0,1fr)_280px_320px]"]
          :style (:grid-style layout)}
    (render-trade-chart-panel desktop-layout? layout-context panel-context)
    (render-orderbook-panel-shell desktop-layout? layout-context panel-context)
    (render-order-entry-panel-shell desktop-layout? layout-context panel-context)
    (render-account-panel-shell state desktop-layout? layout-context panel-context)]
   (render-mobile-account-summary state layout-context panel-context)])

(defn trade-view [state]
  (let [{:keys [mobile-surface] :as layout-context} (trade-view-layout-context state)
        panel-context (trade-view-panel-context state layout-context)]
    [:div {:class ["flex-1" "flex" "flex-col" "min-h-0" "overflow-hidden"]
           :data-parity-id "trade-root"}
     [:div {:class ["w-full"
                    "h-full"
                    "px-0"
                    "py-0"
                    "space-y-0"
                    "flex"
                    "flex-col"
                    "min-h-0"
                    "scrollbar-hide"
                    "overflow-y-auto"]
            :data-role "trade-scroll-shell"}
      (render-mobile-active-asset-strip state layout-context)
      (render-mobile-surface-tabs mobile-surface layout-context)
      (render-trade-grid state layout-context panel-context)]]))
