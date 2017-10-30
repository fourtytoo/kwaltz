(ns kwaltz.util
  (:require [clojure.string :as string]))

(defn remove-all-html-tags [string]
  (string/replace string #"<[^>]+>" ""))


