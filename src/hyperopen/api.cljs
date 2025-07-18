(ns hyperopen.api
  (:require [clojure.string :as str]))

(defn fetch-asset-contexts! [store]
  (println "Fetching perpetual asset contexts...")
  (-> (js/fetch "https://api.hyperliquid.xyz/info"
                (clj->js {:method "POST"
                          :headers {"Content-Type" "application/json"}
                          :body (js/JSON.stringify (clj->js {"type" "metaAndAssetCtxs"}))}))
      (.then #(.json %))
      (.then #(let [data (js->clj % :keywordize-keys true)]
                (swap! store assoc-in [:asset-contexts] data)
                (println "Asset contexts loaded:" (count (second data)) "assets")))
      (.catch #(do (println "Error fetching asset contexts:" %)
                   (swap! store assoc-in [:asset-contexts :error] (str %)))))) 