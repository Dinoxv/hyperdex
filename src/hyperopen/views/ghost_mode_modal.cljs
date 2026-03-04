(ns hyperopen.views.ghost-mode-modal
  (:require [hyperopen.account.context :as account-context]
            [hyperopen.wallet.core :as wallet]))

(def ^:private panel-gap-px
  10)

(def ^:private panel-margin-px
  12)

(def ^:private preferred-panel-width-px
  520)

(def ^:private estimated-panel-height-px
  560)

(def ^:private fallback-viewport-width
  1280)

(def ^:private fallback-viewport-height
  800)

(def ^:private fallback-anchor-top
  84)

(def ^:private fallback-anchor-selector
  "[data-role='ghost-mode-open-button']")

(defn- clamp
  [value min-value max-value]
  (-> value
      (max min-value)
      (min max-value)))

(defn- anchor-number
  [anchor k default]
  (let [value (get anchor k)]
    (if (number? value)
      value
      default)))

(defn- complete-anchor?
  [anchor]
  (and (map? anchor)
       (number? (:left anchor))
       (number? (:right anchor))
       (number? (:top anchor))
       (number? (:bottom anchor))))

(defn- element-anchor-bounds
  [selector]
  (when (seq selector)
    (let [document* (some-> js/globalThis .-document)
          target (some-> document* (.querySelector selector))]
      (when (and target (fn? (.-getBoundingClientRect target)))
        (let [rect (.getBoundingClientRect target)]
          {:left (.-left rect)
           :right (.-right rect)
           :top (.-top rect)
           :bottom (.-bottom rect)
           :width (.-width rect)
           :height (.-height rect)
           :viewport-width (some-> js/globalThis .-innerWidth)
           :viewport-height (some-> js/globalThis .-innerHeight)})))))

(defn- anchored-panel-layout-style
  [anchor]
  (let [anchor* (if (map? anchor) anchor {})
        viewport-width (max 320
                            (anchor-number anchor* :viewport-width fallback-viewport-width)
                            (+ (anchor-number anchor* :right 0) panel-margin-px))
        viewport-height (max 320
                             (anchor-number anchor* :viewport-height fallback-viewport-height))
        anchor-right (anchor-number anchor* :right (- viewport-width panel-margin-px))
        anchor-top (anchor-number anchor* :top fallback-anchor-top)
        anchor-bottom (anchor-number anchor* :bottom (+ anchor-top 40))
        panel-width (clamp (- viewport-width (* 2 panel-margin-px))
                           320
                           preferred-panel-width-px)
        left (clamp (- anchor-right panel-width)
                    panel-margin-px
                    (- viewport-width panel-width panel-margin-px))
        below-top (+ anchor-bottom panel-gap-px)
        above-top (- anchor-top panel-gap-px estimated-panel-height-px)
        fits-below? (<= (+ below-top estimated-panel-height-px panel-margin-px)
                        viewport-height)
        top (if fits-below?
              (clamp below-top
                     panel-margin-px
                     (- viewport-height estimated-panel-height-px panel-margin-px))
              (clamp above-top
                     panel-margin-px
                     (- viewport-height estimated-panel-height-px panel-margin-px)))
        max-height (max 300
                        (- viewport-height top panel-margin-px))]
    {:left (str left "px")
     :top (str top "px")
     :width (str panel-width "px")
     :max-height (str max-height "px")}))

(defn- modal-button-classes
  [primary? disabled?]
  (cond
    disabled?
    ["rounded-lg"
     "border"
     "border-[#2a4b4b]"
     "bg-[#08202a]/55"
     "px-3.5"
     "py-2"
     "text-sm"
     "font-medium"
     "text-[#6c8e93]"
     "cursor-not-allowed"]

    primary?
    ["rounded-lg"
     "border"
     "border-[#2f625a]"
     "bg-[#0d3a35]"
     "px-3.5"
     "py-2"
     "text-sm"
     "font-medium"
     "text-[#daf3ef]"
     "hover:border-[#3f7f75]"
     "hover:bg-[#115046]"]

    :else
    ["rounded-lg"
     "border"
     "border-[#2c4b50]"
     "bg-transparent"
     "px-3.5"
     "py-2"
     "text-sm"
     "text-[#b7c8cc]"
     "hover:border-[#3d666b]"
     "hover:text-[#e5eef1]"]))

(defn- watchlist-row
  [address active?]
  [:li {:class ["flex"
                "items-center"
                "justify-between"
                "gap-2"
                "rounded-lg"
                "border"
                "px-2.5"
                "py-2"]
        :data-role "ghost-mode-watchlist-row"}
   [:div {:class ["min-w-0" "flex" "flex-col" "gap-0.5"]}
    [:span {:class ["text-sm" "font-medium" "text-[#e5eef1]"]}
     (wallet/short-addr address)]
    [:span {:class ["num" "truncate" "text-xs" "text-[#94a9af]"]}
     address]]
   [:div {:class ["flex" "items-center" "gap-1.5"]}
    [:button {:type "button"
              :class (modal-button-classes true false)
              :on {:click [[:actions/spectate-ghost-mode-watchlist-address address]]}
              :data-role "ghost-mode-watchlist-spectate"}
     (if active? "Spectating" "Spectate")]
    [:button {:type "button"
              :class (modal-button-classes false false)
              :on {:click [[:actions/remove-ghost-mode-watchlist-address address]]}
              :data-role "ghost-mode-watchlist-remove"}
     "Remove"]]])

(defn ghost-mode-modal-view
  [state]
  (let [ui-state (get-in state [:account-context :ghost-ui] {})
        open? (true? (:modal-open? ui-state))
        anchor (:anchor ui-state)
        search (or (:search ui-state) "")
        search-error (:search-error ui-state)
        watchlist (account-context/normalize-watchlist
                   (get-in state [:account-context :watchlist]))
        active? (account-context/ghost-mode-active? state)
        active-address (account-context/ghost-address state)
        valid-search? (some? (account-context/normalize-address search))
        start-disabled? (not valid-search?)
        add-disabled? (not valid-search?)
        stored-anchor* (if (map? anchor) anchor {})
        fallback-anchor* (when-not (complete-anchor? stored-anchor*)
                           (element-anchor-bounds fallback-anchor-selector))
        anchor* (or fallback-anchor* stored-anchor*)
        panel-style (anchored-panel-layout-style anchor*)]
    (when open?
      [:div {:class ["fixed" "inset-0" "z-[290]" "pointer-events-none"]
             :data-role "ghost-mode-modal-root"}
       [:div {:class ["absolute"
                      "pointer-events-auto"
                      "rounded-2xl"
                      "border"
                      "border-[#1f3b3c]"
                      "bg-[#081b24]"
                      "p-4"
                      "shadow-2xl"
                      "space-y-4"
                      "overflow-y-auto"]
              :style panel-style
              :role "dialog"
              :aria-modal false
              :aria-label "Ghost Mode"
              :data-role "ghost-mode-modal"
              :data-ghost-mode-surface "true"}
        [:div {:class ["flex" "items-center" "justify-between" "gap-3"]}
         [:div {:class ["flex" "min-w-0" "flex-col"]}
          [:h2 {:class ["text-lg" "font-semibold" "text-[#e5eef1]"]}
           "Ghost Mode"]
          [:p {:class ["text-sm" "text-[#97adb3]"]}
           "Spectate any public address in read-only mode."]]
         [:button {:type "button"
                   :class ["text-sm" "text-[#8ea4ab]" "hover:text-[#e5eef1]"]
                   :on {:click [[:actions/close-ghost-mode-modal]]}
                   :data-role "ghost-mode-close"}
          "Close"]]
        (when active?
          [:div {:class ["rounded-lg"
                         "border"
                         "border-[#1f4f4f]"
                         "bg-[#0a2f33]/60"
                         "px-3"
                         "py-2"
                         "text-sm"
                         "text-[#bdeee8]"]
                 :data-role "ghost-mode-active-summary"}
           [:span {:class ["font-medium"]}
            "Currently spectating: "]
           [:span {:class ["num"]} active-address]])
        [:div {:class ["space-y-2"]}
         [:label {:class ["block"
                          "text-xs"
                          "font-medium"
                          "uppercase"
                          "tracking-[0.08em]"
                          "text-[#8ea4ab]"]}
          "Public Address"]
         [:input {:type "text"
                  :value search
                  :placeholder "0x..."
                  :spell-check false
                  :auto-capitalize "off"
                  :auto-complete "off"
                  :class ["w-full"
                          "rounded-lg"
                          "border"
                          "border-[#28474b]"
                          "bg-[#0c2028]"
                          "px-3"
                          "py-2.5"
                          "text-sm"
                          "text-[#e6eff2]"
                          "outline-none"
                          "focus:border-[#4f8f87]"]
                  :on {:input [[:actions/set-ghost-mode-search [:event.target/value]]]}
                  :data-role "ghost-mode-search-input"}]
         (when (seq search-error)
           [:div {:class ["rounded-md"
                          "border"
                          "border-[#7b3340]"
                          "bg-[#3a1b22]/55"
                          "px-3"
                          "py-2"
                          "text-sm"
                          "text-[#f2b8c5]"]
                  :data-role "ghost-mode-search-error"}
            search-error])]
        [:div {:class ["flex" "flex-wrap" "justify-end" "gap-2"]}
         [:button {:type "button"
                   :class (modal-button-classes false false)
                   :on {:click [[:actions/close-ghost-mode-modal]]}
                   :data-role "ghost-mode-cancel"}
          "Cancel"]
         (when active?
           [:button {:type "button"
                     :class (modal-button-classes false false)
                     :on {:click [[:actions/stop-ghost-mode]]}
                     :data-role "ghost-mode-stop"}
            "Stop Ghost Mode"])
         [:button {:type "button"
                   :class (modal-button-classes false add-disabled?)
                   :disabled add-disabled?
                   :on {:click [[:actions/add-ghost-mode-watchlist-address]]}
                   :data-role "ghost-mode-add-watchlist"}
          "Add To Watchlist"]
         [:button {:type "button"
                   :class (modal-button-classes true start-disabled?)
                   :disabled start-disabled?
                   :on {:click [[:actions/start-ghost-mode]]}
                   :data-role "ghost-mode-start"}
          (if active? "Switch Spectating" "Start Spectating")]]
        [:div {:class ["space-y-2"]}
         [:div {:class ["flex" "items-center" "justify-between"]}
          [:h3 {:class ["text-sm" "font-semibold" "text-[#e5eef1]"]}
           "Watchlist"]]
         (if (seq watchlist)
           (into
            [:ul {:class ["max-h-56" "space-y-2" "overflow-y-auto"]
                  :data-role "ghost-mode-watchlist"}]
            (map (fn [address]
                   ^{:key address}
                   (watchlist-row address (= address active-address)))
                 watchlist))
           [:div {:class ["rounded-lg"
                          "border"
                          "border-dashed"
                          "border-[#2a4b4f]"
                          "bg-[#081f28]"
                          "px-3"
                          "py-3"
                          "text-sm"
                          "text-[#90a6ad]"]
                  :data-role "ghost-mode-watchlist-empty"}
            "No spectated addresses saved yet."])]]])))
