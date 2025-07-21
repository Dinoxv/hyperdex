(ns hyperopen.api
  (:require [clojure.string :as str]
            [hyperopen.utils.data_normalization :refer [normalize-asset-contexts preprocess-webdata2]]))

(defn fetch-asset-contexts! [store]
  (println "Fetching perpetual asset contexts...")
  (-> (js/fetch "https://api.hyperliquid.xyz/info"
                (clj->js {:method "POST"
                          :headers {"Content-Type" "application/json"}
                          :body (js/JSON.stringify (clj->js {"type" "metaAndAssetCtxs"}))}))
      (.then #(.json %))
      (.then #(let [data (js->clj % :keywordize-keys true)
                    normalised (normalize-asset-contexts data)]
                (swap! store assoc-in [:asset-contexts] normalised)
                (println "Loaded" (count normalised) "assets")))
      (.catch #(do (println "Error fetching asset contexts:" %)
                   (swap! store assoc-in [:asset-contexts :error] (str %)))))) 