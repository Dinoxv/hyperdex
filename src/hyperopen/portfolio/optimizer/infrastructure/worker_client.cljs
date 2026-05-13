(ns hyperopen.portfolio.optimizer.infrastructure.worker-client
  (:require [hyperopen.portfolio.optimizer.infrastructure.wire :as wire]))

(def default-worker-url
  "/js/portfolio_optimizer_worker.js")

(defn normalize-worker-message
  [data]
  (wire/normalize-worker-boundary
   (cond
     (map? data) data
     (some? data) (js->clj data :keywordize-keys true)
     :else {})))

(defn make-worker!
  ([] (make-worker! default-worker-url))
  ([url]
   (when (exists? js/Worker)
     (js/Worker. url))))

(defn add-message-listener!
  [worker handler]
  (when worker
    (.addEventListener worker "message"
                       (fn [^js event]
                         (handler (normalize-worker-message (.-data event)))))
    true))

(defn current-worker
  [worker-ref]
  (cond
    (nil? worker-ref) nil
    (satisfies? IDeref worker-ref) @worker-ref
    :else worker-ref))

(defn post-run!
  ([id request]
   (post-run! (delay (make-worker!)) id request))
  ([worker-ref id request]
   (when-let [worker (current-worker worker-ref)]
     (.postMessage worker #js {:id id
                               :type "run-optimizer"
                               :payload (clj->js request)})
     true)))
