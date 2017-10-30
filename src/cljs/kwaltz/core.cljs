(ns kwaltz.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [kwaltz.events]
            [kwaltz.subs]
            [kwaltz.views :as views]
            [kwaltz.config :as config]))

(enable-console-print!)

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")))

(defn mount-root []
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app"))
  (views/setup-typeahead))

(defn render []
  (re-frame/dispatch-sync [:initialize-db])
  (dev-setup)
  (mount-root))
