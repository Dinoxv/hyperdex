(ns hyperopen.views.footer.links
  (:require [clojure.string :as str]))

(def footer-link-classes
  ["text-sm" "text-trading-text" "hover:text-primary" "transition-colors"])

(def ^:private footer-utility-link-classes
  ["flex" "items-center" "gap-4"])

(def ^:private footer-text-link-classes
  ["flex" "items-center" "space-x-6"])

(def ^:private social-link-group-classes
  ["flex" "items-center" "gap-2"])

(def ^:private footer-build-id-shell-classes
  ["inline-flex"
   "items-center"
   "gap-1.5"
   "text-xs"
   "text-trading-text-secondary"
   "font-mono"])

(def ^:private footer-build-id-icon-classes
  ["h-3.5"
   "w-3.5"
   "shrink-0"
   "opacity-70"])

(def ^:private footer-build-id-trigger-classes
  ["inline-flex"
   "items-center"
   "gap-1.5"
   "rounded"
   "focus-visible:outline-none"
   "focus-visible:ring-2"
   "focus-visible:ring-trading-green/70"
   "focus-visible:ring-offset-1"
   "focus-visible:ring-offset-base-100"])

(def ^:private social-link-shell-classes
  ["inline-flex"
   "h-6"
   "w-6"
   "items-center"
   "justify-center"
   "text-trading-text"])

(def ^:private social-link-anchor-classes
  ["inline-flex"
   "h-6"
   "w-6"
   "items-center"
   "justify-center"
   "text-trading-text"
   "transition-colors"
   "hover:text-primary"])

(def ^:private social-link-icon-classes
  ["h-4"
   "w-4"
   "shrink-0"])

(def ^:private github-icon
  {:view-box "0 0 98 96"
   :paths [{:d "M41.4395 69.3848C28.8066 67.8535 19.9062 58.7617 19.9062 46.9902C19.9062 42.2051 21.6289 37.0371 24.5 33.5918C23.2559 30.4336 23.4473 23.7344 24.8828 20.959C28.7109 20.4805 33.8789 22.4902 36.9414 25.2656C40.5781 24.1172 44.4062 23.543 49.0957 23.543C53.7852 23.543 57.6133 24.1172 61.0586 25.1699C64.0254 22.4902 69.2891 20.4805 73.1172 20.959C74.457 23.543 74.6484 30.2422 73.4043 33.4961C76.4668 37.1328 78.0937 42.0137 78.0937 46.9902C78.0937 58.7617 69.1934 67.6621 56.3691 69.2891C59.623 71.3945 61.8242 75.9883 61.8242 81.252L61.8242 91.2051C61.8242 94.0762 64.2168 95.7031 67.0879 94.5547C84.4102 87.9512 98 70.6289 98 49.1914C98 22.1074 75.9883 6.69539e-07 48.9043 4.309e-07C21.8203 1.92261e-07 -1.9479e-07 22.1074 -4.3343e-07 49.1914C-6.20631e-07 70.4375 13.4941 88.0469 31.6777 94.6504C34.2617 95.6074 36.75 93.8848 36.75 91.3008L36.75 83.6445C35.4102 84.2188 33.6875 84.6016 32.1562 84.6016C25.8398 84.6016 22.1074 81.1563 19.4277 74.7441C18.375 72.1602 17.2266 70.6289 15.0254 70.3418C13.877 70.2461 13.4941 69.7676 13.4941 69.1934C13.4941 68.0449 15.4082 67.1836 17.3223 67.1836C20.0977 67.1836 22.4902 68.9063 24.9785 72.4473C26.8926 75.2227 28.9023 76.4668 31.2949 76.4668C33.6875 76.4668 35.2187 75.6055 37.4199 73.4043C39.0469 71.7773 40.291 70.3418 41.4395 69.3848Z"}]})

(def ^:private telegram-icon
  {:view-box "186 300 546 454"
   :paths [{:d "M226.328419,494.722069 C372.088573,431.216685 469.284839,389.350049 517.917216,369.122161 C656.772535,311.36743 685.625481,301.334815 704.431427,301.003532 C708.567621,300.93067 717.815839,301.955743 723.806446,306.816707 C728.864797,310.92121 730.256552,316.46581 730.922551,320.357329 C731.588551,324.248848 732.417879,333.113828 731.758626,340.040666 C724.234007,419.102486 691.675104,610.964674 675.110982,699.515267 C668.10208,736.984342 654.301336,749.547532 640.940618,750.777006 C611.904684,753.448938 589.856115,731.588035 561.733393,713.153237 C517.726886,684.306416 492.866009,666.349181 450.150074,638.200013 C400.78442,605.66878 432.786119,587.789048 460.919462,558.568563 C468.282091,550.921423 596.21508,434.556479 598.691227,424.000355 C599.00091,422.680135 599.288312,417.758981 596.36474,415.160431 C593.441168,412.561881 589.126229,413.450484 586.012448,414.157198 C581.598758,415.158943 511.297793,461.625274 375.109553,553.556189 C355.154858,567.258623 337.080515,573.934908 320.886524,573.585046 C303.033948,573.199351 268.692754,563.490928 243.163606,555.192408 C211.851067,545.013936 186.964484,539.632504 189.131547,522.346309 C190.260287,513.342589 202.659244,504.134509 226.328419,494.722069 Z"}]})

(defn- social-icon
  [data-role label {:keys [view-box paths]}]
  [:span {:class ["inline-flex" "items-center" "justify-center"]
          :data-role data-role
          :role "img"
          :aria-label label}
   (into [:svg {:class social-link-icon-classes
                :viewBox view-box
                :fill "currentColor"
                :stroke "none"
                :aria-hidden true}]
         (map (fn [{:keys [d fill-rule clip-rule]}]
                [:path (cond-> {:d d}
                         fill-rule (assoc :fill-rule fill-rule)
                         clip-rule (assoc :clip-rule clip-rule))])
              paths))])

(defn- render-social-icons
  []
  [:div {:class social-link-group-classes
         :data-role "footer-social-links"}
   [:a {:class social-link-anchor-classes
        :href "https://t.me/hyperopen"
        :target "_blank"
        :rel "noreferrer"
        :aria-label "Telegram"}
    (social-icon "footer-social-telegram" "Telegram" telegram-icon)]
   [:a {:class social-link-anchor-classes
        :href "https://github.com/thegeronimo/hyperopen"
        :target "_blank"
        :rel "noreferrer"
        :aria-label "GitHub"}
    (social-icon "footer-social-github" "GitHub" github-icon)]])

(defn- short-build-id
  [build-id]
  (let [text (some-> build-id str str/trim)]
    (when (seq text)
      (subs text 0 (min 7 (count text))))))

(defn- render-build-id
  [build-id]
  (when-let [short-id (short-build-id build-id)]
    [:span {:class ["group/tooltip" "relative" "inline-flex" "items-center"]
            :data-role "footer-build-id-shell"}
     [:span {:class (into footer-build-id-shell-classes footer-build-id-trigger-classes)
             :data-role "footer-build-id"
             :tab-index 0}
      [:svg {:class footer-build-id-icon-classes
             :viewBox "0 0 16 16"
             :fill "none"
             :stroke "currentColor"
             :stroke-width "1.5"
             :stroke-linecap "round"
             :stroke-linejoin "round"
             :aria-hidden true}
       [:path {:d "M6.2 5.2 4.7 3.7a2 2 0 0 0-2.8 2.8l1.5 1.5a2 2 0 0 0 2.8 0l.8-.8"}]
       [:path {:d "M9.8 10.8 11.3 12.3a2 2 0 1 0 2.8-2.8l-1.5-1.5a2 2 0 0 0-2.8 0l-.8.8"}]
       [:path {:d "M6.3 9.7 9.7 6.3"}]]
      short-id]
     [:div {:class ["pointer-events-none"
                    "absolute"
                    "bottom-[calc(100%+9px)]"
                    "right-0"
                    "z-20"
                    "w-max"
                    "max-w-[calc(100vw-2rem)]"
                    "rounded-[10px]"
                    "border"
                    "border-[#2f3a40]"
                    "bg-[#162127]"
                    "px-3"
                    "py-2.5"
                    "text-[0.69rem]"
                    "leading-[1.45]"
                    "text-left"
                    "text-[#d7e1e3]"
                    "opacity-0"
                    "shadow-[0_18px_50px_rgba(0,0,0,0.38)]"
                    "transition-all"
                    "duration-150"
                    "translate-y-1"
                    "group-hover/tooltip:translate-y-0"
                    "group-hover/tooltip:opacity-100"
                    "group-focus-within/tooltip:translate-y-0"
                    "group-focus-within/tooltip:opacity-100"]
            :role "tooltip"
            :data-role "footer-build-id-tooltip"}
      [:div {:class ["flex" "items-center" "gap-2" "whitespace-nowrap"]
             :data-role "footer-build-id-tooltip-content"}
       [:div {:class ["text-[0.62rem]"
                      "font-semibold"
                      "uppercase"
                      "tracking-[0.12em]"
                      "text-[#8ea2a8]"]}
        "Build"]
       [:div {:class ["font-mono" "text-[0.72rem]" "whitespace-nowrap" "text-[#d7e1e3]"]
              :data-role "footer-build-id-tooltip-value"}
        build-id]]]
     [:div {:class ["pointer-events-none"
                    "absolute"
                    "bottom-[calc(100%+4px)]"
                    "right-[1.35rem]"
                    "h-2.5"
                    "w-2.5"
                    "rotate-45"
                    "border-b"
                    "border-r"
                    "border-[#2f3a40]"
                    "bg-[#162127]"
                    "opacity-0"
                    "transition-opacity"
                    "duration-150"
                    "group-hover/tooltip:opacity-100"
                    "group-focus-within/tooltip:opacity-100"]
            :data-role "footer-build-id-tooltip-caret"}]]))

(defn render
  [{:keys [links build-id]}]
  [:div {:class footer-utility-link-classes
         :data-role "footer-utility-links"}
   (when (seq links)
     [:<>
      [:div {:class footer-text-link-classes
             :data-role "footer-text-links"}
       (for [{:keys [label href]} links]
         ^{:key label}
         [:a {:class footer-link-classes
              :href href}
          label])]
      [:span {:class ["h-3" "w-px" "bg-base-content/15"]
              :data-role "footer-links-divider"
              :aria-hidden true}]])
   (render-build-id build-id)
   (render-social-icons)])
