(ns hyperopen.portfolio.routes
  (:require [clojure.string :as str]
            [hyperopen.router :as router]))

(def canonical-route
  "/portfolio")

(def ^:private trader-route-prefix
  "/portfolio/trader/")

(defn- portfolio-prefix-match?
  [path]
  (or (= path canonical-route)
      (str/starts-with? path (str canonical-route "/"))))

(defn- normalize-address
  [value]
  (let [text (some-> value str str/trim str/lower-case)]
    (when (and (seq text)
               (re-matches #"^0x[0-9a-f]{40}$" text))
      text)))

(defn parse-portfolio-route
  [path]
  (let [path* (router/normalize-path path)
        suffix (when (str/starts-with? path* trader-route-prefix)
                 (subs path* (count trader-route-prefix)))
        trader-address (normalize-address suffix)]
    (cond
      (= path* canonical-route)
      {:kind :page
       :path path*}

      trader-address
      {:kind :trader
       :path path*
       :address trader-address}

      (portfolio-prefix-match? path*)
      {:kind :other
       :path path*}

      :else
      {:kind :non-portfolio
       :path path*})))

(defn portfolio-route?
  [path]
  (contains? #{:page :trader :other}
             (:kind (parse-portfolio-route path))))

(defn trader-portfolio-route?
  [path]
  (= :trader (:kind (parse-portfolio-route path))))

(defn trader-portfolio-address
  [path]
  (:address (parse-portfolio-route path)))

(defn trader-portfolio-path
  [address]
  (when-let [address* (normalize-address address)]
    (str trader-route-prefix address*)))
