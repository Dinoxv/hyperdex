(ns hyperopen.test-runner-support)

(defn exit-code-for-results
  [results]
  (if (zero? (+ (or (:fail results) 0)
                (or (:error results) 0)))
    0
    1))

(defn apply-process-exit!
  [results]
  (when (exists? js/process)
    (set! (.-exitCode js/process)
          (exit-code-for-results results)))
  results)
