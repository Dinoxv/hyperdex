(ns hyperopen.views.footer-view)

(defn footer-view [state]
  [:footer.bg-base-200.border-t.border-base-300.w-full
   [:div.max-w-7xl.mx-auto.px-8.py-6
    [:div.flex.justify-between.items-center
     ;; Connection Status
     [:div.flex.items-center.space-x-2
      [:div.w-3.h-3.rounded-full.border.border-success
       [:div.w-2.h-2.rounded-full.bg-success.mx-0.5.my-0.5]]
      [:span.text-success.text-sm.font-medium 
       (str "WebSocket: " (name (get-in state [:websocket :status] :disconnected)))]]
     
     ;; Footer Links
     [:div.flex.space-x-6
      [:a.text-base-content.opacity-70.hover:opacity-100.hover:text-primary.transition-colors
       {:href "#"} "Docs"]
      [:a.text-base-content.opacity-70.hover:opacity-100.hover:text-primary.transition-colors
       {:href "#"} "Support"]
      [:a.text-base-content.opacity-70.hover:opacity-100.hover:text-primary.transition-colors
       {:href "#"} "Terms"]
      [:a.text-base-content.opacity-70.hover:opacity-100.hover:text-primary.transition-colors
       {:href "#"} "Privacy Policy"]]]]])