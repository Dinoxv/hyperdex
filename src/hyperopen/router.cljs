(ns hyperopen.router
  (:require [clojure.string :as str]))

(defn normalize-path [path]
  (let [p (or path "/")]
    (if (or (= p "") (= p "/")) "/trade" p)))

(defn set-route! [store path]
  (swap! store assoc :router {:path (normalize-path path)}))

(defn current-path []
  (normalize-path (.-pathname js/location)))

(defn init! [store]
  (set-route! store (current-path))
  (.addEventListener js/window "popstate"
                     (fn [_]
                       (set-route! store (current-path)))))
