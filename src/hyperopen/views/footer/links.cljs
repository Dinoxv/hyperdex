(ns hyperopen.views.footer.links)

(def footer-link-classes
  ["text-sm" "text-trading-text" "hover:text-primary" "transition-colors"])

(defn render
  [links]
  [:div {:class ["flex" "space-x-6"]}
   (for [{:keys [label href]} links]
     ^{:key label}
     [:a {:class footer-link-classes
          :href href}
      label])])
