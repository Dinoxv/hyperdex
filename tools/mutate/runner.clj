(ns tools.mutate.runner
  (:require [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream]
           [java.util.concurrent TimeUnit]))

(defn- shell-argv
  [command]
  ["/bin/sh" "-lc" command])

(defn- start-output-drainer!
  [process output-buffer]
  (doto (Thread.
         (fn []
           (try
             (with-open [stream (.getInputStream process)]
               (io/copy stream output-buffer))
             (catch Exception _)))
         "mutation-output-drainer")
    (.setDaemon true)
    (.start)))

(defn run-command
  [command {:keys [timeout-ms dir]}]
  (let [builder (doto (ProcessBuilder. ^java.util.List (vec (shell-argv command)))
                  (.redirectErrorStream true))
        _ (when dir
            (.directory builder (java.io.File. dir)))
        process (.start builder)
        output-buffer (ByteArrayOutputStream.)
        drainer (start-output-drainer! process output-buffer)
        start-ms (System/currentTimeMillis)
        finished? (if timeout-ms
                    (.waitFor process (long timeout-ms) TimeUnit/MILLISECONDS)
                    (do (.waitFor process) true))
        elapsed-ms (- (System/currentTimeMillis) start-ms)]
    (when-not finished?
      (.destroyForcibly process))
    (.join drainer 1000)
    {:command command
     :exit (when finished? (.exitValue process))
     :timeout? (not finished?)
     :elapsed-ms elapsed-ms
     :output (.toString output-buffer "UTF-8")}))

(defn success?
  [{:keys [exit timeout?]}]
  (and (not timeout?)
       (zero? (or exit 1))))
