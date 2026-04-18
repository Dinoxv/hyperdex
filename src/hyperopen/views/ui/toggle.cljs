(ns hyperopen.views.ui.toggle
  (:require [cljs.spec.alpha :as s]))

(s/def :hyperopen.views.ui.toggle/on? boolean?)
(s/def :hyperopen.views.ui.toggle/aria-label string?)
(s/def :hyperopen.views.ui.toggle/data-role string?)
(s/def :hyperopen.views.ui.toggle/on-change some?)
(s/def :hyperopen.views.ui.toggle/disabled? (s/nilable boolean?))
(s/def :hyperopen.views.ui.toggle/props
  (s/keys :req-un [:hyperopen.views.ui.toggle/on?
                   :hyperopen.views.ui.toggle/aria-label
                   :hyperopen.views.ui.toggle/on-change]
          :opt-un [:hyperopen.views.ui.toggle/data-role
                   :hyperopen.views.ui.toggle/disabled?]))

(defn toggle
  [{:keys [aria-label data-role disabled? on-change on?]}]
  [:button {:type "button"
            :class ["hx-toggle" (when on? "on")]
            :role "switch"
            :aria-checked (if on? "true" "false")
            :aria-label aria-label
            :data-role data-role
            :disabled disabled?
            :on {:click on-change}}
   [:span {:class ["hx-toggle-knob"]}]])
