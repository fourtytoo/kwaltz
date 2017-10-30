(ns kwaltz.userdb
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [monger.query :as mq]
            [monger.joda-time]
            [kwaltz.config :as conf]
            [clj-time.core :as time]
            [onelog.core :as log])
  (:import [com.mongodb MongoOptions ServerAddress]
           [org.bson.types ObjectId]))


(defn connect [& [uri]]
  (if-let [uri (or uri
                   (conf/conf :mongodb :url))]
    (let [{:keys [conn db]} (mg/connect-via-uri uri)]
      db)
    (let [conn (mg/connect {:host (or (conf/conf :mongodb :host)
                                      "localhost")})
          db (mg/get-db conn (or (conf/conf :mongodb :name)
                                 "kwaltz"))]
      db)))

(defonce db
  (delay (connect)))

(defn- insert-new-job [collection doc]
  (mc/insert-and-return @db collection (assoc doc :_id (ObjectId.))))

;; update or insert document
(defn update [collection id doc]
  (mc/update @db collection {:_id id}
             (assoc doc
                    :_id id
                    :inserted (time/now))
             {:upsert true}))

(defn insert-note [user parid note]
  (let [id (ObjectId.)]
    (mc/insert-and-return @db "notes"
                          {:_id id
                           :paragraph parid
                           :user (:id user)
                           :note note})))

(defn fetch-user [id]
  (first (mc/find-maps @db "users" {:_id id})))

(defn user-notes [user parid]
  (mc/find-maps @db "notes" {:user (:_id user) :paragraph parid}))

(defn delete [collection id]
  (mc/remove @db collection {:_id id}))

(defn modify [collection id delta]
  (mc/update-by-id @db collection id delta))

(defn add-field
  "Add field KEY with value VALUE only if not already present in the document."
  [collection id key value]
  (mc/update @db collection
             {:_id id key {$exists false}}
             {$set {key value}}))

(defn nuke-collection [collection]
  (mc/remove @db collection))

