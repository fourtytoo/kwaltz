(ns kwaltz.config
  (:require [cprop.core :refer [load-config]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.gzip :refer [wrap-gzip]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.middleware.format :refer [wrap-restful-format]]))

(def ^:dynamic *default-api-port* 10555)

(defonce configuration (atom (load-config)))

(defn reload []
  (reset! configuration (load-config)))

(defn conf [& keys]
  (get-in @configuration keys))

(defn configure! [keys value]
  (swap! configuration #(assoc-in % keys value)))

(defn server-port []
  (Integer. (or (conf :api-port)
                *default-api-port*)))

(defn api-config []
  {:http-port  (Integer. (or (conf :port) *default-api-port*))
   :middleware [[wrap-defaults api-defaults]
                wrap-restful-format
                wrap-with-logger
                wrap-gzip]})
