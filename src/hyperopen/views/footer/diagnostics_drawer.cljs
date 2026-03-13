(ns hyperopen.views.footer.diagnostics-drawer
  (:require [hyperopen.views.footer.market-projection-diagnostics :as market-projection-diagnostics]))

(def ^:private tone-classes
  {:neutral {:border "border-base-300"
             :bg "bg-base-200/40"
             :text "text-base-content/70"}
   :success {:border "border-success/50"
             :bg "bg-success/10"
             :text "text-success"}
   :warning {:border "border-warning/50"
             :bg "bg-warning/10"
             :text "text-warning"}
   :error {:border "border-error/50"
           :bg "bg-error/10"
           :text "text-error"}})

(def ^:private banner-tone-classes
  {:warning ["border-warning/40" "bg-warning/10" "text-warning"]
   :error ["border-error/40" "bg-error/10" "text-error"]
   :info ["border-info/40" "bg-info/10" "text-info"]})

(defn banner-classes
  [tone]
  (get banner-tone-classes tone ["border-base-300" "bg-base-200/50" "text-base-content"]))

(defn- status-chip
  [{:keys [status-label tone]}]
  (let [{:keys [border bg text]} (get tone-classes tone (:neutral tone-classes))]
    [:span {:class ["inline-flex"
                    "items-center"
                    "rounded"
                    "border"
                    "px-2"
                    "py-0.5"
                    "text-xs"
                    "font-semibold"
                    "uppercase"
                    "tracking-wide"
                    border
                    bg
                    text]}
     status-label]))

(defn- hover-tooltip
  [message child]
  [:div {:class ["relative" "group" "w-full"]}
   child
   [:div {:class ["pointer-events-none"
                  "absolute"
                  "left-1/2"
                  "top-full"
                  "z-50"
                  "mt-1.5"
                  "-translate-x-1/2"
                  "opacity-0"
                  "transition-opacity"
                  "duration-150"
                  "group-hover:opacity-100"
                  "group-focus-within:opacity-100"]
          :style {:min-width "max-content"}}
    [:div {:class ["max-w-[18rem]"
                   "whitespace-normal"
                   "rounded"
                   "border"
                   "border-base-300"
                   "bg-base-100"
                   "px-2"
                   "py-1"
                   "text-xs"
                   "leading-4"
                   "text-base-content"
                   "spectate-lg"]}
     message]]])

(defn- surface-freshness-toggle
  [checked?]
  [:label {:class ["flex"
                   "items-center"
                   "justify-between"
                   "gap-3"
                   "rounded"
                   "border"
                   "border-base-300"
                   "bg-base-200/50"
                   "px-3"
                   "py-2.5"
                   "text-xs"
                   "text-base-content"
                   "cursor-pointer"
                   "select-none"]
           :data-role "surface-freshness-toggle"}
   [:span "Show freshness cues"]
   [:input {:type "checkbox"
            :class ["h-5"
                    "w-5"
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
            :checked checked?
            :on {:click [[:actions/toggle-show-surface-freshness-cues]]}}]])

(defn- copy-status-view
  [copy-status]
  (when copy-status
    (let [success? (= :success (:kind copy-status))]
      [:div {:class ["space-y-2"]}
       [:div {:class (if success?
                       ["rounded" "border" "border-success/40" "bg-success/10" "px-3" "py-2" "text-xs" "text-success"]
                       ["rounded" "border" "border-warning/40" "bg-warning/10" "px-3" "py-2" "text-xs" "text-warning"])}
        (:message copy-status)]
       (when-let [fallback-json (:fallback-json copy-status)]
        [:pre {:class ["max-h-40"
                        "overflow-auto"
                        "rounded"
                        "border"
                        "border-base-300"
                        "bg-base-200/50"
                        "p-2"
                        "text-xs"
                        "leading-5"
                        "break-all"]}
          fallback-json])])))

(defn- summary-card
  [rows]
  [:div {:class ["rounded" "border" "border-base-300" "bg-base-200/50" "p-3" "space-y-1.5" "text-xs"]}
   (for [{:keys [label value class]} rows]
     ^{:key label}
     [:div {:class ["flex" "justify-between"]}
      [:span label]
      [:span {:class class} value]])])

(defn- meter-breakdown-card
  [connection-meter]
  [:div {:class ["rounded" "border" "border-base-300" "bg-base-200/50" "p-3" "space-y-2" "text-xs"]}
   [:div {:class ["font-semibold" "uppercase" "tracking-wide" "text-base-content/70"]}
    "Connection meter"]
   (summary-card (:rows connection-meter))
   [:div {:class ["space-y-1.5"]}
    (for [{:keys [key label penalty-text detail]} (:breakdown connection-meter)]
      ^{:key key}
      [:div {:class ["rounded" "border" "border-base-300/60" "bg-base-100/70" "px-2.5" "py-2" "space-y-1"]}
       [:div {:class ["flex" "justify-between" "gap-3"]}
        [:span label]
        [:span {:class ["text-base-content/70"]} penalty-text]]
       [:div {:class ["text-base-content/60"]}
        detail]])]])

(defn- timeline-card
  [timeline]
  [:div {:class ["rounded" "border" "border-base-300" "bg-base-200/50" "p-3" "space-y-1.5" "text-xs"]}
   [:div {:class ["font-semibold" "uppercase" "tracking-wide" "text-base-content/70"]}
    "Recent timeline"]
   (if (seq timeline)
     (for [{:keys [key event-label age-label details-text]} timeline]
       ^{:key key}
       [:div {:class ["space-y-0.5"]}
        [:div {:class ["flex" "justify-between"]}
         [:span event-label]
         [:span age-label]]
        (when details-text
          [:div {:class ["text-base-content/60" "break-all"]}
           details-text])])
     [:div {:class ["text-base-content/70"]}
      "No events yet"])])

(defn render
  [diagnostics]
  [:div {:class ["fixed" "inset-0" "z-[220]"]}
   [:button {:type "button"
             :class ["absolute" "inset-0" "bg-black/40"]
             :on {:click [[:actions/close-ws-diagnostics]]}}
    [:span {:class ["sr-only"]} "Close diagnostics"]]
   [:aside {:class ["absolute"
                    "right-0"
                    "top-0"
                    "h-full"
                    "w-full"
                    "max-w-[30rem]"
                    "border-l"
                    "border-base-300"
                    "bg-base-100"
                    "shadow-2xl"
                    "overflow-y-auto"
                    "p-4"
                    "space-y-4"]}
    [:div {:class ["flex" "items-center" "justify-between"]}
     [:h2 {:class ["text-sm" "font-semibold" "uppercase" "tracking-wide"]}
      "Connection diagnostics"]
     [:button.btn.btn-xs.btn-spectate
      {:type "button"
       :on {:click [[:actions/close-ws-diagnostics]]}}
      "Close"]]

    [:div {:class ["flex" "gap-2" "pt-1"]}
     [:button.btn.btn-sm.btn-outline
      {:type "button"
       :on {:click [[:actions/ws-diagnostics-copy]]}}
      "Copy diagnostics"]
     [:button.btn.btn-sm.btn-warning
      {:type "button"
       :disabled (get-in diagnostics [:reconnect-control :disabled?])
       :on {:click [[:actions/ws-diagnostics-reconnect-now]]}}
      (get-in diagnostics [:reconnect-control :label])]]

    [:div {:class ["grid" "grid-cols-1" "gap-2" "sm:grid-cols-3"]}
     (for [{:keys [key label tooltip disabled? click-action]} (:reset-buttons diagnostics)]
       ^{:key key}
       (hover-tooltip
        tooltip
        [:button.btn.btn-sm
         {:type "button"
          :class (if (= key "all")
                   ["btn-spectate"]
                   ["btn-outline"])
          :title tooltip
          :disabled disabled?
          :on {:click click-action}}
         label]))]

    [:div {:class ["flex" "items-center" "justify-between" "text-xs" "text-base-content/70"]}
     [:span "Sensitive values are masked by default"]
     [:button.btn.btn-xs.btn-spectate
      {:type "button"
       :on {:click [[:actions/toggle-ws-diagnostics-sensitive]]}}
      (if (:reveal-sensitive? diagnostics) "Hide sensitive" "Reveal sensitive")]]

    (copy-status-view (:copy-status diagnostics))

    [:section {:class ["space-y-2"]}
     [:h3 {:class ["text-xs" "font-semibold" "uppercase" "tracking-wide" "text-base-content/70"]}
      "Diagnostics"]
     (surface-freshness-toggle (:show-surface-freshness-cues? diagnostics))
     (summary-card (:summary-rows diagnostics))
     (meter-breakdown-card (:connection-meter diagnostics))
     (timeline-card (:timeline diagnostics))]

    (market-projection-diagnostics/render-store-section (:market-projection diagnostics))

    [:section {:class ["space-y-2"]}
     [:h3 {:class ["text-xs" "font-semibold" "uppercase" "tracking-wide" "text-base-content/70"]}
      "Transport"]
     (summary-card (:transport-rows diagnostics))]

    [:section {:class ["space-y-2"]}
     [:h3 {:class ["text-xs" "font-semibold" "uppercase" "tracking-wide" "text-base-content/70"]}
      "Group health"]
     [:div {:class ["rounded" "border" "border-base-300" "bg-base-200/50" "p-3" "space-y-2"]}
      (for [{:keys [key label status-label tone]} (:group-health diagnostics)]
        ^{:key key}
        [:div {:class ["flex" "items-center" "justify-between" "text-xs"]}
         [:span label]
         (status-chip {:status-label status-label
                       :tone tone})])]]

    [:section {:class ["space-y-2"]}
     [:h3 {:class ["text-xs" "font-semibold" "uppercase" "tracking-wide" "text-base-content/70"]}
      "Streams"]
     (if (seq (:stream-groups diagnostics))
       (for [{:keys [key title count streams]} (:stream-groups diagnostics)]
         ^{:key key}
         [:details {:class ["rounded" "border" "border-base-300" "bg-base-200/50"] :open true}
          [:summary {:class ["cursor-pointer" "px-3" "py-2" "text-xs" "font-semibold" "uppercase" "tracking-wide"]}
           (str title " (" count ")")]
          [:div {:class ["px-3" "pb-3" "space-y-2"]}
           (for [{:keys [key
                         topic
                         status-label
                         tone
                         age-text
                         threshold-text
                         sequence-text
                         gap-text
                         last-gap-text
                         subscription-text
                         descriptor-text]} streams]
             ^{:key key}
             [:div {:class ["rounded" "border" "border-base-300" "bg-base-100" "p-2" "space-y-1"]}
              [:div {:class ["flex" "items-center" "justify-between" "text-xs"]}
               [:code topic]
               (status-chip {:status-label status-label
                             :tone tone})]
              [:div {:class ["text-xs" "text-base-content/70"]}
               (str "Age: " age-text
                    " | Threshold: " threshold-text)]
              [:div {:class ["text-xs" "text-base-content/70"]}
               (str "Seq: " sequence-text
                    " | Gap: " gap-text)]
              (when last-gap-text
                [:div {:class ["text-xs" "text-base-content/60" "break-all"]}
                 (str "Last gap: " last-gap-text)])
              [:div {:class ["text-xs" "text-base-content/70" "break-all"]}
               (str "Subscription: " subscription-text)]
              [:div {:class ["text-xs" "text-base-content/70" "break-all"]}
               (str "Descriptor: " descriptor-text)]])]])
       [:div {:class ["rounded" "border" "border-base-300" "bg-base-200/50" "p-3" "text-xs" "text-base-content/70"]}
        "No active stream diagnostics available."])]

    (market-projection-diagnostics/render-flushes-section (:market-projection diagnostics))]])
