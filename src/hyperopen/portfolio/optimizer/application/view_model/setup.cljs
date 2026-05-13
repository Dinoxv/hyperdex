(ns hyperopen.portfolio.optimizer.application.view-model.setup
  (:require [clojure.string :as str]
            [hyperopen.portfolio.optimizer.application.setup-readiness :as setup-readiness]
            [hyperopen.portfolio.optimizer.application.view-model.universe :as universe]))

(defn- warning-code-label
  [warning]
  (some-> (:code warning) name))

(defn- warning-message
  [readiness warning]
  (or (:message warning)
      (setup-readiness/warning-display-message (:request readiness) warning)
      (warning-code-label warning)))

(defn- readiness-copy
  [readiness]
  (case (:reason readiness)
    :missing-universe "Select a universe before running."
    :no-eligible-history "History starts loading as assets are included. Run Optimization retries anything still missing."
    :incomplete-history "History is incomplete for this universe. Run Optimization retries anything still missing."
    :history-loading "History is loading for the selected assets."
    :missing-black-litterman-views "Add a view before running Use my views."
    "Optimizer inputs are ready to run."))

(defn- history-load-copy
  [history-load-state readiness]
  (case (:status history-load-state)
    :loading "Loading optimizer history for the selected assets."
    :succeeded "Optimizer history is loaded for the selected assets."
    :failed "History load failed. Existing history, if any, is retained."
    (readiness-copy readiness)))

(defn readiness-panel-model
  [readiness history-load-state]
  (let [warnings (vec (or (seq (:blocking-warnings readiness))
                          (:warnings readiness)))]
    {:title "Readiness"
     :copy (history-load-copy history-load-state readiness)
     :error-message (get-in history-load-state [:error :message])
     :warnings (mapv (fn [warning]
                       {:message (warning-message readiness warning)
                        :code-label (warning-code-label warning)})
                     warnings)}))

(defn- title-case-token
  [token]
  (if (seq token)
    (str (str/upper-case (subs token 0 1))
         (subs token 1))
    token))

(defn- default-labelize
  [value]
  (cond
    (keyword? value)
    (->> (str/split (name value) #"-")
         (map title-case-token)
         (str/join " "))

    (some? value)
    (str value)

    :else
    "--"))

(defn- apply-labelize
  [formatter value]
  (cond
    (nil? value)
    "--"

    (fn? formatter)
    (formatter value)

    (map? formatter)
    (get formatter value (default-labelize value))

    :else
    (default-labelize value)))

(defn- apply-percent-label
  [formatter value]
  (if (fn? formatter)
    (formatter value)
    (str value)))

(defn- active-preset
  [draft]
  (let [objective-kind (get-in draft [:objective :kind])
        return-kind (get-in draft [:return-model :kind])]
    (cond
      (= :black-litterman return-kind) :use-my-views
      (= :max-sharpe objective-kind) :risk-adjusted
      :else :conservative)))

(defn- universe-label
  [instrument]
  (if (= :vault (:market-type instrument))
    (universe/instrument-primary-label instrument)
    (or (:coin instrument)
        (universe/instrument-primary-label instrument))))

(defn- universe-summary
  [draft]
  (let [universe (vec (:universe draft))
        labels (->> universe
                    (keep universe-label)
                    (take 5)
                    (str/join ", "))]
    (str (count universe) " assets"
         (when (seq labels) (str " - " labels)))))

(defn- summary-row
  [label title copy]
  {:label label
   :title title
   :copy copy})

(defn setup-summary-model
  ([draft]
   (setup-summary-model draft nil))
  ([draft {:keys [labelize percent-label]}]
   (let [preset (active-preset draft)
         objective-kind (get-in draft [:objective :kind])
         return-kind (get-in draft [:return-model :kind])
         constraints (:constraints draft)
         labelize* #(apply-labelize labelize %)
         percent-label* #(apply-percent-label percent-label %)
         bl? (= :black-litterman return-kind)]
     {:active-preset preset
      :black-litterman? bl?
      :summary-rows
      [(summary-row "Preset" (labelize* preset)
                    "You can deviate from the preset below without changing the universe.")
       (summary-row "Universe" (universe-summary draft)
                    "Selected instruments are optimized as one cross-margin book.")
       (summary-row "Expected Returns" (labelize* return-kind)
                    "Funding-adjusted return assumptions are kept separate from covariance.")
       (summary-row "Objective" (labelize* objective-kind)
                    "Objective remains separate from return model selection.")
       (summary-row "Constraints"
                    (str "gross <= " (or (:gross-max constraints) "--")
                         " - cap <= " (percent-label* (:max-asset-weight constraints)))
                    "Constraints are enforced before the recommendation is accepted.")
       (summary-row "Horizon" "Annualized"
                    "Displayed return and volatility metrics use the optimizer annualization convention.")]})))
