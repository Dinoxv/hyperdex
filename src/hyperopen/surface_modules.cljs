(ns hyperopen.surface-modules
  (:require [clojure.string :as str]
            [goog.object :as gobj]
            [shadow.loader :as loader]))

(def ^:private module-name-by-id
  {:funding-modal "funding_modal"
   :spectate-mode-modal "spectate_mode_modal"})

(def ^:private exported-view-path-by-id
  {:funding-modal ["hyperopen" "views" "funding_modal_module" "funding_modal_view"]
   :spectate-mode-modal ["hyperopen" "views" "spectate_mode_modal_module" "spectate_mode_modal_view"]})

(defonce ^:private resolved-surface-views (atom {}))

(declare resolve-module-view)

(defn default-state
  []
  {:loaded #{}
   :loading nil
   :errors {}})

(defn surface-module-id
  [surface-id]
  (when (contains? module-name-by-id surface-id)
    surface-id))

(defn resolved-surface-view
  [surface-id]
  (get @resolved-surface-views (surface-module-id surface-id)))

(defn- resolve-exported-view
  [path-segments]
  (let [root (or (some-> js/goog .-global)
                 js/globalThis)]
    (reduce (fn [acc segment]
              (when acc
                (gobj/get acc segment)))
            root
            path-segments)))

(defn- resolve-module-view
  [surface-id]
  (when-let [module-id (surface-module-id surface-id)]
    (resolve-exported-view (get exported-view-path-by-id module-id))))

(defn- cached-or-exported-view
  [surface-id]
  (when-let [module-id (surface-module-id surface-id)]
    (let [cached-view (resolved-surface-view module-id)]
      (cond
        (fn? cached-view)
        cached-view

        (some? cached-view)
        (do
          (swap! resolved-surface-views dissoc module-id)
          nil)

        :else
        (when-let [resolved-view (resolve-module-view module-id)]
          (when (fn? resolved-view)
            (swap! resolved-surface-views assoc module-id resolved-view)
            resolved-view))))))

(defn surface-ready?
  [_state surface-id]
  (some? (cached-or-exported-view surface-id)))

(defn surface-loading?
  [state surface-id]
  (= (get-in state [:surface-modules :loading])
     (surface-module-id surface-id)))

(defn surface-error
  [state surface-id]
  (get-in state [:surface-modules :errors (surface-module-id surface-id)]))

(defn render-surface-view
  [state surface-id]
  (when-let [view (cached-or-exported-view surface-id)]
    (view state)))

(defn mark-surface-module-loading
  [state surface-id]
  (if-let [module-id (surface-module-id surface-id)]
    (-> state
        (assoc-in [:surface-modules :loading] module-id)
        (update-in [:surface-modules :errors] dissoc module-id))
    (assoc-in state [:surface-modules :loading] nil)))

(defn mark-surface-module-loaded
  [state surface-id]
  (if-let [module-id (surface-module-id surface-id)]
    (-> state
        (update-in [:surface-modules :loaded] (fnil conj #{}) module-id)
        (assoc-in [:surface-modules :loading] nil)
        (update-in [:surface-modules :errors] dissoc module-id))
    (assoc-in state [:surface-modules :loading] nil)))

(defn mark-surface-module-failed
  [state surface-id err]
  (let [module-id (surface-module-id surface-id)
        message (or (some-> err .-message)
                    (some-> err str str/trim not-empty)
                    "Failed to load surface.")]
    (-> state
        (assoc-in [:surface-modules :loading] nil)
        (assoc-in [:surface-modules :errors module-id] message))))

(defn load-surface-module!
  [store surface-id]
  (if-let [module-id (surface-module-id surface-id)]
    (if-let [existing-view (cached-or-exported-view module-id)]
      (do
        (swap! store mark-surface-module-loaded module-id)
        (js/Promise.resolve existing-view))
      (let [module-name (get module-name-by-id module-id)
            resolve-loaded-view!
            (fn []
              (let [resolved-view (resolve-module-view module-id)]
                (when-not (fn? resolved-view)
                  (throw (js/Error.
                          (str "Loaded surface module without exported view: " module-id))))
                (swap! resolved-surface-views assoc module-id resolved-view)
                (swap! store mark-surface-module-loaded module-id)
                resolved-view))]
        (swap! store mark-surface-module-loading module-id)
        (try
          (if (loader/loaded? module-name)
            (js/Promise.resolve (resolve-loaded-view!))
            (-> (loader/load module-name)
                (.then (fn [_]
                         (resolve-loaded-view!)))
                (.catch (fn [err]
                          (swap! store mark-surface-module-failed module-id err)
                          (js/Promise.reject err)))))
          (catch :default err
            (swap! store mark-surface-module-failed module-id err)
            (js/Promise.reject err)))))
    (do
      (swap! store mark-surface-module-loaded nil)
      (js/Promise.resolve nil))))
