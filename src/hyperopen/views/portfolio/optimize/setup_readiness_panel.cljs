(ns hyperopen.views.portfolio.optimize.setup-readiness-panel
  (:require [hyperopen.portfolio.optimizer.application.view-model :as optimizer-view-model]))

(defn readiness-panel
  [readiness history-load-state]
  (let [{:keys [copy error-message warnings]}
        (optimizer-view-model/readiness-panel-model readiness history-load-state)]
    [:div {:class ["mt-4" "rounded-lg" "border" "border-base-300" "bg-base-200/40" "p-3"]
           :data-role "portfolio-optimizer-readiness-panel"}
     [:p {:class ["text-[0.65rem]"
                  "font-semibold"
                  "uppercase"
                  "tracking-[0.18em]"
                  "text-trading-muted"]}
      "Readiness"]
     [:p {:class ["mt-2" "text-xs" "text-trading-muted"]}
      copy]
     (when error-message
       [:p {:class ["mt-3"
                    "rounded-md"
                    "border"
                    "border-error/40"
                    "bg-error/10"
                    "px-2"
                    "py-1.5"
                    "text-xs"
                     "text-error"]}
        error-message])
     (when (seq warnings)
       (into
        [:div {:class ["mt-3" "space-y-2"]}]
        (map (fn [{:keys [message code-label]}]
               [:div {:class ["rounded-md"
                              "border"
                              "border-warning/40"
                              "bg-warning/10"
                              "px-2"
                              "py-1.5"
                              "text-xs"
                              "text-warning"]
                      :data-role "portfolio-optimizer-readiness-warning"}
                [:p {:class ["font-semibold"]}
                 message]
                (when code-label
                  [:p {:class ["mt-1"
                               "font-mono"
                               "text-[0.65rem]"
                               "uppercase"
                               "tracking-[0.08em]"
                               "text-warning/80"]}
                   code-label])])
             warnings)))]))
