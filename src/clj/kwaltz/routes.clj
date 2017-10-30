(ns kwaltz.routes
  (:require [clojure.java.io :as io]
            [compojure.core :refer [ANY GET PUT POST PATCH DELETE routes context defroutes]]
            [compojure.route :refer [resources]]
            [ring.util.response :as response]
            [clojure.data.json :as json]
            [kwaltz.docdb :as docdb]
            [gtbot.core :refer [translate]]
            [onelog.core :as log]
            [autoclave.core :as autoclave]
            [clj-time.core :as time]))


(defn search-paragraphs [query size]
  (->> (if (and size
                (> size 0))
         (-> (docdb/search-paragraphs query :size size)
             :hits :hits)
         (docdb/search-all-paragraphs query))
       ;; do not pass on the bulky stuff, just the highlights
       (map #(update % :_source dissoc :text :text-en))
       response/response))

(defn fetch-paragraph [id]
  (->> (docdb/get-paragraph id)
       response/response))

(defn update-paragraph [id body]
  (log/debug+ "update-paragraph: " id ", " body) ; -wcp17/10/17.
  (if body
    (->> (docdb/update-paragraph id body)
         response/response)
    (response/not-found "update-paragraph without a body")))

#_(docdb/update-paragraph "BJNR001950896BJNE160607308" {:notes "foo"})
#_(docdb/get-paragraph "BJNR001950896BJNE160607308")

(defn remove-html [string]
  (autoclave/html-sanitize string))

(defn translate-paragraph [paragraph-id language]
  (let [translation (->> (docdb/get-paragraph paragraph-id)
                         :_source :text
                         remove-html
                         (translate "de" language))]
    (-> (assoc translation :time (time/now))
        response/response)))

#_(translate-paragraph "BJNR001950896BJNE160607308" "en")

(defn get-user [id])

(defn update-user [id body])

(defroutes api-routes
  (GET "/search/paragraphs" [query size]
       (search-paragraphs query (when size
                                  (Integer/parseInt size))))
  (GET "/translation/:id/:lang" [id lang]
       (translate-paragraph id lang))
  (GET "/paragraph/:id" [id]
       (fetch-paragraph id))
  (PATCH "/paragraph/:id" [id body]
         (update-paragraph id body))
  (GET "/user/:id" [id]
       (get-user id))
  (PATCH "/user/:id" [id body]
         (update-user id body)))

(defn home-routes [endpoint]
  (routes
   (GET "/" _
     (-> "public/index.html"
         io/resource
         io/input-stream
         response/response
         (assoc :headers {"Content-Type" "text/html; charset=utf-8"})))
   (context "/api" []
            api-routes)
   (resources "/")))


