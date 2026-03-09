#!/usr/bin/env bb

(ns dev.check-docs
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [cheshire.core :as json]))

(def default-required-files
  ["AGENTS.md"
   "ARCHITECTURE.md"
   "docs/DESIGN.md"
   "docs/FRONTEND.md"
   "docs/PLANS.md"
   "docs/PRODUCT_SENSE.md"
   "docs/QUALITY_SCORE.md"
   "docs/RELIABILITY.md"
   "docs/SECURITY.md"
   "docs/design-docs/index.md"
   "docs/design-docs/agents-section-index.md"
   "docs/design-docs/core-beliefs.md"
   "docs/exec-plans/active/README.md"
   "docs/exec-plans/completed/README.md"
   "docs/exec-plans/deferred/README.md"
   "docs/exec-plans/tech-debt-tracker.md"
   "docs/generated/index.md"
   "docs/product-specs/index.md"
   "docs/references/index.md"])

(def default-governed-explicit
  ["AGENTS.md"
   "ARCHITECTURE.md"
   "docs/DESIGN.md"
   "docs/FRONTEND.md"
   "docs/PLANS.md"
   "docs/PRODUCT_SENSE.md"
   "docs/QUALITY_SCORE.md"
   "docs/RELIABILITY.md"
   "docs/SECURITY.md"])

(def default-governed-dirs
  ["docs/design-docs"
   "docs/product-specs"
   "docs/references"])

(def default-index-rules
  [{:index "docs/design-docs/index.md"
    :dir "docs/design-docs"}
   {:index "docs/product-specs/index.md"
    :dir "docs/product-specs"}
   {:index "docs/references/index.md"
    :dir "docs/references"}])

(def default-active-exec-plan-dir
  "docs/exec-plans/active")

(def default-agents-required-links
  ["ARCHITECTURE.md"
   "docs/DESIGN.md"
   "docs/FRONTEND.md"
   "docs/PLANS.md"
   "docs/PRODUCT_SENSE.md"
   "docs/QUALITY_SCORE.md"
   "docs/RELIABILITY.md"
   "docs/SECURITY.md"
   "docs/design-docs/index.md"
   "docs/design-docs/agents-section-index.md"
   "docs/product-specs/index.md"
   "docs/references/index.md"])

(defn utc-today
  []
  (java.time.LocalDate/now (java.time.ZoneId/of "UTC")))

(defn default-config
  []
  {:required-files default-required-files
   :governed-explicit default-governed-explicit
   :governed-dirs default-governed-dirs
   :index-rules default-index-rules
   :agents-required-links default-agents-required-links
   :active-exec-plan-dir default-active-exec-plan-dir
   :bd-show-fn nil
   :today (utc-today)})

(defn normalize-path
  [path]
  (str/replace path "\\" "/"))

(defn path-exists?
  [root rel-path]
  (.exists (io/file root rel-path)))

(defn markdown-files-under
  [root rel-dir]
  (let [dir (io/file root rel-dir)]
    (if-not (.exists dir)
      []
      (->> (file-seq dir)
           (filter #(.isFile %))
           (map #(.getCanonicalPath %))
           (filter #(str/ends-with? % ".md"))
           (map #(normalize-path (.toString (.relativize (.toPath (io/file root))
                                                        (.toPath (io/file %))))))
           sort
           vec))))

(defn markdown-files-in-dir
  [root rel-dir]
  (let [dir (io/file root rel-dir)]
    (if-not (.exists dir)
      []
      (->> (.listFiles dir)
           (filter #(.isFile %))
           (map #(.getCanonicalPath %))
           (filter #(str/ends-with? % ".md"))
           (map #(normalize-path (.toString (.relativize (.toPath (io/file root))
                                                        (.toPath (io/file %))))))
           sort
           vec))))

(defn parse-front-matter
  [text]
  (if-let [[_ meta-block body] (re-matches #"(?s)^---\n(.*?)\n---\n?(.*)$" text)]
    (let [lines (str/split-lines meta-block)
          parsed (reduce (fn [acc line]
                           (if (str/blank? line)
                             acc
                             (if-let [[_ k v] (re-matches #"^([a-z_]+):\s*(.+)$" line)]
                               (update acc :fields assoc (keyword k) (str/trim v))
                               (update acc :errors conj (str "invalid front matter line: " line)))))
                         {:fields {} :errors []}
                         lines)]
      (assoc parsed :body body))
    {:fields nil :errors ["missing front matter"] :body text}))

(defn parse-int
  [s]
  (try
    (Integer/parseInt s)
    (catch Exception _ nil)))

(defn parse-date
  [s]
  (try
    (java.time.LocalDate/parse s)
    (catch Exception _ nil)))

(defn parse-bool
  [s]
  (cond
    (= s "true") true
    (= s "false") false
    :else nil))

(defn err
  [code path message]
  {:code code :path path :message message})

(defn validate-front-matter-fields
  [rel-path fields today]
  (let [owner (:owner fields)
        status (:status fields)
        reviewed-raw (:last_reviewed fields)
        cycle-raw (:review_cycle_days fields)
        source-raw (:source_of_truth fields)
        reviewed (parse-date reviewed-raw)
        cycle (parse-int cycle-raw)
        source (parse-bool source-raw)
        allowed-status #{"canonical" "supporting" "draft" "deprecated"}
        owner-pattern #"^[a-z][a-z0-9-]*$"
        max-age (when cycle (min cycle 90))
        base-errors (-> []
                        (cond->
                          (not (and owner (re-matches owner-pattern owner)))
                          (conj (err :invalid-owner rel-path "owner must be a team slug (e.g. platform, trading-ui)"))

                          (not (contains? allowed-status status))
                          (conj (err :invalid-status rel-path "status must be canonical|supporting|draft|deprecated"))

                          (nil? reviewed)
                          (conj (err :invalid-last-reviewed rel-path "last_reviewed must be YYYY-MM-DD"))

                          (not (and cycle (pos? cycle)))
                          (conj (err :invalid-review-cycle rel-path "review_cycle_days must be a positive integer"))

                          (nil? source)
                          (conj (err :invalid-source-of-truth rel-path "source_of_truth must be true or false"))))]
    (if (and reviewed max-age)
      (let [age (.between java.time.temporal.ChronoUnit/DAYS reviewed today)]
        (if (> age max-age)
          (conj base-errors (err :stale-doc rel-path (str "document is stale: " age " days old, max allowed " max-age)))
          base-errors))
      base-errors)))

(defn link-targets
  [markdown]
  (map second (re-seq #"\[[^\]]+\]\(([^)]+)\)" markdown)))

(defn clean-link-target
  [raw]
  (-> raw
      str/trim
      (str/replace #"^<" "")
      (str/replace #">$" "")
      (str/split #"\s+" 2)
      first
      (#(first (str/split % #"[?#]" 2)))))

(defn external-link?
  [target]
  (or (str/blank? target)
      (str/starts-with? target "#")
      (str/starts-with? target "http://")
      (str/starts-with? target "https://")
      (str/starts-with? target "mailto:")
      (str/starts-with? target "tel:")))

(defn rel-path-from-canonical
  [root canonical]
  (let [root-path (normalize-path (.getCanonicalPath (io/file root)))
        canonical-path (normalize-path canonical)
        prefix (str root-path "/")]
    (when (str/starts-with? canonical-path prefix)
      (subs canonical-path (count prefix)))))

(defn resolve-link
  [root doc-rel target]
  (let [clean (clean-link-target target)]
    (when-not (external-link? clean)
      (cond
        (str/starts-with? clean "/hyperopen/")
        (subs clean (count "/hyperopen/"))

        (str/starts-with? clean "/")
        (subs clean 1)

        :else
        (let [doc-file (io/file root doc-rel)
              parent (or (.getParentFile doc-file) (io/file root))
              resolved (.getCanonicalPath (io/file parent clean))]
          (rel-path-from-canonical root resolved))))))

(defn validate-links
  [root rel-path body]
  (let [targets (link-targets body)]
    (reduce (fn [errors target]
              (if-let [resolved (resolve-link root rel-path target)]
                (if (path-exists? root resolved)
                  errors
                  (conj errors (err :broken-link rel-path (str "broken link target: " target " -> " resolved))))
                errors))
            []
            targets)))

(defn validate-no-machine-paths
  [rel-path markdown]
  (if (re-find #"/Users/[A-Za-z0-9._-]+" markdown)
    [(err :machine-specific-path rel-path "contains machine-specific absolute path; use /hyperopen/... repo-root paths")]
    []))

(defn required-file-errors
  [root required-files]
  (reduce (fn [errors rel-path]
            (if (path-exists? root rel-path)
              errors
              (conj errors (err :missing-file rel-path "required file is missing"))))
          []
          required-files))

(defn governed-doc-paths
  [root {:keys [governed-explicit governed-dirs]}]
  (->> (concat governed-explicit
               (mapcat #(markdown-files-under root %) governed-dirs))
       distinct
       sort
       vec))

(defn file-text
  [root rel-path]
  (slurp (io/file root rel-path)))

(def active-exec-plan-issue-pattern
  #"\bhyperopen-[a-z0-9]+(?:\.[0-9]+)?\b")

(defn extract-active-exec-plan-issue-ids
  [text]
  (->> (re-seq active-exec-plan-issue-pattern text)
       distinct
       sort
       vec))

(defn active-exec-plan-unchecked-count
  [text]
  (count (re-seq #"(?m)^- \[ \]" text)))

(defn default-bd-show-fn
  [issue-ids]
  (reduce (fn [acc issue-id]
            (let [{:keys [exit out err]} (shell/sh "bd" "show" issue-id "--json")]
              (cond
                (zero? exit)
                (let [rows (json/parse-string out true)
                      status (-> rows first :status)]
                  (if status
                    (assoc acc issue-id status)
                    acc))

                (re-find #"not found|no issue|unknown issue|does not exist" (str/lower-case err))
                acc

                :else
                (throw (ex-info (str "bd show failed for " issue-id)
                                {:issue-id issue-id
                                 :stderr err
                                 :exit exit})))))
          {}
          issue-ids))

(defn active-exec-plan-errors
  [root {:keys [active-exec-plan-dir bd-show-fn]}]
  (let [plan-dir (or active-exec-plan-dir default-active-exec-plan-dir)
        plan-files (->> (markdown-files-in-dir root plan-dir)
                        (remove #(= % (str plan-dir "/README.md")))
                        vec)]
    (if (empty? plan-files)
      []
      (let [plans (mapv (fn [rel-path]
                          (let [text (file-text root rel-path)]
                            {:path rel-path
                             :text text
                             :issue-ids (extract-active-exec-plan-issue-ids text)
                             :unchecked-count (active-exec-plan-unchecked-count text)}))
                        plan-files)
            all-ids (->> plans (mapcat :issue-ids) distinct sort vec)
            lookup (or bd-show-fn default-bd-show-fn)]
        (try
          (let [status-by-id (lookup all-ids)]
            (mapcat (fn [{:keys [path issue-ids unchecked-count]}]
                      (let [valid-ids (filter #(contains? status-by-id %) issue-ids)
                            open-ids (filter #(not= "closed" (get status-by-id %)) valid-ids)]
                        (cond-> []
                          (empty? valid-ids)
                          (conj (err :active-exec-plan-missing-bd-link
                                     path
                                     "active ExecPlan must reference at least one valid bd issue id"))

                          (and (seq valid-ids) (empty? open-ids))
                          (conj (err :active-exec-plan-no-open-bd-issue
                                     path
                                     (str "active ExecPlan only references closed bd issues: "
                                          (str/join ", " valid-ids))))

                          (zero? unchecked-count)
                          (conj (err :active-exec-plan-no-unchecked-progress
                                     path
                                     "active ExecPlan has no remaining unchecked progress items; move it out of active")))))
                    plans))
          (catch Exception ex
            [(err :bd-query-failed plan-dir (or (.getMessage ex) "failed to query bd issue status"))]))))))

(defn validate-governed-doc
  [root rel-path today]
  (let [text (file-text root rel-path)
        {:keys [fields errors body]} (parse-front-matter text)]
    (into []
          (concat
           (validate-no-machine-paths rel-path text)
           (map #(err :front-matter-parse rel-path %) errors)
           (when fields
             (concat
              (validate-front-matter-fields rel-path fields today)
              (validate-links root rel-path body)))))))

(defn index-coverage-errors
  [root {:keys [index dir]}]
  (if-not (path-exists? root index)
    [(err :missing-index index "index file is missing")]
    (let [index-text (file-text root index)
          index-body (:body (parse-front-matter index-text))
          index-links (->> (link-targets index-body)
                           (map #(resolve-link root index %))
                           (remove nil?)
                           set)
          files (->> (markdown-files-under root dir)
                     (remove #(= % index))
                     set)
          missing (sort (remove index-links files))]
      (mapv #(err :missing-index-link index (str "index missing link to " %)) missing))))

(defn agents-link-errors
  [root agents-required-links]
  (if-not (path-exists? root "AGENTS.md")
    [(err :missing-file "AGENTS.md" "AGENTS.md is missing")]
    (let [text (file-text root "AGENTS.md")
          body (:body (parse-front-matter text))
          links (->> (link-targets body)
                     (map #(resolve-link root "AGENTS.md" %))
                     (remove nil?)
                     set)]
      (->> agents-required-links
           (remove links)
           (mapv #(err :missing-agents-link "AGENTS.md" (str "AGENTS is missing link to " %)))))))

(defn check-repo
  ([root]
   (check-repo root (default-config)))
  ([root config]
  (let [today (:today config)
        required (:required-files config)
        governed (governed-doc-paths root config)
        governed-errors (mapcat #(validate-governed-doc root % today) governed)
        index-errors (mapcat #(index-coverage-errors root %) (:index-rules config))
        active-plan-errors (active-exec-plan-errors root config)]
     (-> []
         (into (required-file-errors root required))
         (into governed-errors)
         (into index-errors)
         (into active-plan-errors)
         (into (agents-link-errors root (:agents-required-links config)))))))

(defn print-errors!
  [errors]
  (doseq [{:keys [code path message]} errors]
    (println (str "[" (name code) "] " path " - " message))))

(defn -main
  [& _args]
  (let [root (.getCanonicalPath (io/file "."))
        errors (check-repo root)]
    (if (empty? errors)
      (do
        (println "Docs check passed.")
        (System/exit 0))
      (do
        (print-errors! errors)
        (System/exit 1)))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
