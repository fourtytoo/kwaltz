(ns kwaltz.application
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [system.components.endpoint :refer [new-endpoint]]
            [system.components.handler :refer [new-handler]]
            [system.components.middleware :refer [new-middleware]]
            [system.components.http-kit :refer [new-web-server]]
            [kwaltz.config :refer [api-config]]
            [kwaltz.routes :refer [home-routes]]
            [kwaltz.scraper :refer [scrape-all-books]]
            [kwaltz.docdb :as docdb]
            [onelog.core :as log]))

(defn update-database []
  (->> (scrape-all-books)
       (pmap docdb/put-book-bulk)
       (reduce +)))

(defn reboot-database []
  (time
   (do
     (docdb/reset-database)
     (update-database))))

#_(reboot-database)
#_(kwaltz.scraper/fetch-book-from-toc "https://www.gesetze-im-internet.de/anfrv/index.html")
#_(docdb/put-book-bulk (kwaltz.scraper/fetch-book-from-toc "https://www.gesetze-im-internet.de/anfrv/index.html"))
#_(docdb/put-book-bulk (kwaltz.scraper/fetch-book-from-toc "https://www.gesetze-im-internet.de/iaminvges_bk_usa_ua/index.html"))
#_(kwaltz.scraper/fetch-book-from-toc "https://www.gesetze-im-internet.de/iaminvges_bk_usa_ua/index.html")
#_(kwaltz.scraper/extract-book-links (kwaltz.scraper/slurp-url "https://www.gesetze-im-internet.de/iaminvges_bk_usa_ua/index.html"))

(defn app-system [config]
  (component/system-map
   :routes     (new-endpoint home-routes)
   :middleware (new-middleware {:middleware (:middleware config)})
   :handler    (-> (new-handler)
                   (component/using [:routes :middleware]))
   :http       (-> (new-web-server (:http-port config))
                   (component/using [:handler]))))

(defn restart [component]
  (component/stop component)
  (component/start component))

(defn -main [& _]
  (let [config (api-config)]
    ;; shouldn't we make a system component instead? -wcp2/10/17.
    (log/start! "log/kwaltz.log")
    (-> config
        app-system
        component/start)
    (println "Started kwaltz on" (str "http://localhost:" (:http-port config)))))

