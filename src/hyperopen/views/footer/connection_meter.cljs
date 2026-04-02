(ns hyperopen.views.footer.connection-meter)

(def ^:private meter-bar-heights
  [6 9 12 15])

(def ^:private label-tone-classes
  {:success "text-success"
   :warning "text-warning"
   :error "text-error"
   :neutral "text-base-content/70"})

(def ^:private bar-tone-classes
  {:success "bg-success"
   :warning "bg-warning"
   :error "bg-error"
   :neutral "bg-base-content/70"})

(defn- signal-meter-bars
  [bar-count active-bars bar-active-class]
  [:span {:class ["inline-flex" "items-end" "gap-[2px]"]
          :data-role "footer-connection-meter-bars"}
   (for [idx (range bar-count)]
     ^{:key (str "meter-bar|" idx)}
     (let [active? (< idx active-bars)]
       [:span {:class (into ["block"
                             "w-[3px]"
                             "rounded-sm"
                             ]
                            (if active?
                              [bar-active-class]
                              ["bg-base-300/70"]))
               :style {:height (str (nth meter-bar-heights idx 15) "px")}
               :data-role "footer-connection-meter-bar"
               :data-active (if active? "true" "false")}]))])

(defn render
  [meter]
  (let [label-class (get label-tone-classes (:tone meter) "text-base-content/70")
        bar-class (get bar-tone-classes (:tone meter) "bg-base-content/70")]
    [:button {:type "button"
              :class ["inline-flex"
                      "min-h-6"
                      "items-end"
                      "gap-2"
                      "px-1"
                      "py-1"
                      "text-xs"
                      "font-medium"
                      "focus:outline-none"
                      "focus:ring-0"
                      "focus:ring-offset-0"
                      label-class]
              :on {:click [[:actions/toggle-ws-diagnostics]]}
              :title (:tooltip meter)
              :data-role "footer-connection-meter-button"}
     (signal-meter-bars (:bar-count meter) (:active-bars meter) bar-class)
     [:span {:class ["relative" "top-px" "leading-none"]} (:label meter)]]))
