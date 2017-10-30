(ns kwaltz.styles
  (:require [garden-watcher.def :refer [defstyles]]
            [garden.core :refer [css]]
            [garden.stylesheet :refer [at-media]]
            [garden.selectors :as s]))

(defstyles style
  ;; [:h1 {:text-decoration "underline"}]
  [:pre.wrap {:white-space "pre-wrap"}]   ;CSS3
  [:pre.wrap {:white-space "-moz-pre-wrap"}] ;Firefox
  [:pre.wrap {:white-space "-pre-wrap"}] ;Opera <7
  [:pre.wrap {:white-space "-o-pre-wrap"}] ;Opera 7
  [:pre.wrap {:word-wrap "break-word"}] ;IE
  #_(at-media {:print true}
            [:body ["*" {:visibility "hidden"}]]
            [:#section-to-print ["*" {:visibility "visible"}]]
            [:#section-to-print {:visibility "visible" :position "absolute" :left 0 :top 0}])
  [:ul#hits [:mark {:background "#F9D2A8"
                    :border "1px solid #EBC49A"}]])
#_(css style)
