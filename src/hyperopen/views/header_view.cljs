(ns hyperopen.views.header-view)

(defn header-view [state]
  [:header.bg-base-200.border-b.border-base-300.w-full
   [:div.max-w-8xl.mx-auto.px-4.py-3
    [:div.flex.justify-between.items-center
     ;; Logo and Brand
     [:div.flex.items-center.space-x-3
      [:span.text-primary.text-3xl.font-bold.font-splash "HyperOpen"]]
     
     ;; Navigation Links
     [:nav.hidden.md:flex.items-center.space-x-8
      [:a.text-cyan-400.font-medium.hover:text-cyan-300.transition-colors
       {:href "#"} "Trade"]
      [:a.text-white.opacity-80.hover:opacity-100.hover:text-white.transition-colors
       {:href "#"} "Vaults"]
      [:a.text-white.opacity-80.hover:opacity-100.hover:text-white.transition-colors
       {:href "#"} "Portfolio"]
      [:a.text-white.opacity-80.hover:opacity-100.hover:text-white.transition-colors
       {:href "#"} "Staking"]
      [:a.text-white.opacity-80.hover:opacity-100.hover:text-white.transition-colors
       {:href "#"} "Referrals"]
      [:a.text-white.opacity-80.hover:opacity-100.hover:text-white.transition-colors
       {:href "#"} "Leaderboard"]
      [:div.relative.group
       [:a.text-white.opacity-80.hover:opacity-100.hover:text-white.transition-colors.flex.items-center.space-x-1
        {:href "#"}
        [:span "More"]
        [:svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
         [:path {:fill-rule "evenodd" :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" :clip-rule "evenodd"}]]]
       ;; Dropdown menu would go here
       ]]
     
     ;; Right Section - Connect Button and Icons
     [:div.flex.items-center.space-x-4
      ;; Connect Button
      [:button.bg-teal-600.hover:bg-teal-700.text-teal-100.px-4.py-2.rounded-lg.font-medium.transition-colors
       {:on {:click [[:actions/init-websockets]]}}
       "Connect"]
      
      ;; Utility Icons
      [:button.w-10.h-10.bg-base-300.hover:bg-base-400.rounded-lg.flex.items-center.justify-center.transition-colors
       {:title "Language/Region"}
       [:svg.w-5.h-5.text-white {:viewBox "0 0 20 20" :fill "currentColor"}
        [:path {:fill-rule "evenodd" :d "M10 18a8 8 0 100-16 8 8 0 000 16zM4.332 8.027a6.012 6.012 0 011.912-2.706C6.512 5.73 6.974 6 7.5 6A1.5 1.5 0 019 7.5V8a2 2 0 004 0 2 2 0 011.523-1.943A5.977 5.977 0 0116 10c0 .34-.028.675-.083 1H15a2 2 0 00-2 2v2.197A5.973 5.973 0 0110 16v-2a2 2 0 00-2-2 2 2 0 01-2-2 2 2 0 00-1.668-1.973z" :clip-rule "evenodd"}]]]
      
      [:button.w-10.h-10.bg-base-300.hover:bg-base-400.rounded-lg.flex.items-center.justify-center.transition-colors
       {:title "Settings"}
       [:svg.w-5.h-5.text-white {:viewBox "0 0 20 20" :fill "currentColor"}
        [:path {:fill-rule "evenodd" :d "M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947zM10 13a3 3 0 100-6 3 3 0 000 6z" :clip-rule "evenodd"}]]]
      
      ;; Mobile menu button
      [:button.md:hidden.w-10.h-10.bg-base-300.hover:bg-base-400.rounded-lg.flex.items-center.justify-center.transition-colors
       {:title "Menu"}
       [:svg.w-5.h-5.text-white {:viewBox "0 0 20 20" :fill "currentColor"}
        [:path {:fill-rule "evenodd" :d "M3 5a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zM3 10a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1zM3 15a1 1 0 011-1h12a1 1 0 110 2H4a1 1 0 01-1-1z" :clip-rule "evenodd"}]]]]]]]) 