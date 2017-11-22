(ns kwaltz.util
  (:require [clojure.string :as string]))

(defn remove-all-html-tags [string]
  (string/replace string #"<[^>]+>" ""))

(defn parent [e]
  (.-parentElement e))

(defn children [e]
  (js->clj (goog.array/toArray (.-childNodes e))))

(defn add-class [e c]
  (let [classes (remove empty? (string/split (.-className e) #" +"))]
    (when (not-any? (partial = c) classes)
      (->> (conj classes c)
           (string/join " ")
           (set! (.-className e)))))
  e)

(defn remove-class [e c]
  (let [classes (string/split (.-className e) #" +")]
    (->> (remove (partial = c) classes)
         (string/join " ")
         (set! (.-className e))))
  e)

(defn target [e]
  (.-target e))

(defn current-target [e]
  (.-currentTarget e))

