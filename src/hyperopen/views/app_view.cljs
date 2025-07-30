(ns hyperopen.views.app-view
  (:require [hyperopen.views.active-asset-view :as active-asset-view]
            [hyperopen.views.l2-orderbook-view :as l2-orderbook-view]
            [hyperopen.views.trading-chart.core :as trading-chart]
            [hyperopen.views.footer-view :as footer-view]))

(defn app-view [state]
  [:div.h-screen.bg-base-100.flex.flex-col
   ;; Main Content
   [:div.flex-1.p-8.overflow-auto
    [:div.max-w-7xl.mx-auto.space-y-8
     ;; Header
     [:div.text-center.space-y-4
      [:h1.text-4xl.font-bold.text-primary.font-splash (:title state)]
      [:p.text-lg.text-base-content.opacity-80 (:message state)]]
     
     ;; Controls
     [:div.flex.justify-center.space-x-4
      [:button.btn.btn-primary
       {:on {:click [[:actions/init-websockets]]}}
       "Connect WebSocket"]
      [:button.btn.btn-secondary
       {:on {:click [[:actions/subscribe-to-asset "BTC"]]}}
       "Subscribe to BTC"]
      [:button.btn.btn-secondary
       {:on {:click [[:actions/subscribe-to-asset "ETH"]]}}
       "Subscribe to ETH"]]
     
     ;; Active Assets Panel
     [:div
      (active-asset-view/active-asset-view state)]
     
     ;; L2 Order Book Panel
     [:div.flex.justify-center
      (let [active-coins (keys (:contexts (:active-assets state)))
            available-orderbook-coins (keys (:orderbooks state))
            display-coin (or (first active-coins) (first available-orderbook-coins))
            orderbook-data (when display-coin 
                            (get-in state [:orderbooks display-coin]))]
        (l2-orderbook-view/l2-orderbook-view 
          {:coin (or display-coin "No Asset Selected")
           :orderbook orderbook-data
           :loading (and display-coin (nil? orderbook-data))}))]
           
     ;; Trading Chart Panel
     [:div
      (trading-chart/trading-chart-view state)]]]
   
   ;; Footer - Pinned to bottom
   (footer-view/footer-view state)]) 