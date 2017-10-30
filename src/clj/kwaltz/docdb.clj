(ns kwaltz.docdb
  (:require [clojurewerkz.elastisch.rest :as rest]
            [clojurewerkz.elastisch.rest.index :as index]
            [clojurewerkz.elastisch.rest.document :as doc]
            [clojurewerkz.elastisch.rest.response :refer [hits-from]]
            [clojurewerkz.elastisch.rest.bulk :refer [bulk bulk-index]]
            [clojurewerkz.elastisch.query :as q]
            [onelog.core :as log]
            [clj-time.core :as time]
            [clj-time.coerce]
            [kwaltz.config :as conf]
            [clojure.java.io :as io]
            [clojure.data.xml :as dxml]
            [clojure.string :as string]
            [cheshire.generate]))


(def #^:dynamic *default-elasticsearch-url*
  "http://127.0.0.1:9200")

(cheshire.generate/add-encoder
 org.joda.time.DateTime
 (fn [c jg]
   (cheshire.generate/encode-long (clj-time.coerce/to-long c) jg)))

(defn connect []
  (rest/connect (or (conf/conf :elasticsearch :url)
                    *default-elasticsearch-url*)
                {:basic-auth (conf/conf :elasticsearch :auth)}))

(defonce conn (delay (connect)))

(defn index [& [i]]
  (str (or (conf/conf :elasticsearch :index)
           "kwaltz")
       (if i
         (str "_v" i)
         "")))

(def ^:dynamic *notes-analyser* "english")

(defn setup-database [index]
  (let [mappings
        {"paragraph"
         {:properties
          {:book-id {:type "string" :index "not_analyzed"}
           :designation {:type "string"
                         :analyzer "simple_analyser"}
           :updated {:type "date" :index "not_analyzed"}
           :previous {:type "string" :index "not_analyzed"}
           ;; :outline {:type "array" :index "not_analyzed"}
           :next {:type "string" :index "not_analyzed"}
           :book-abbreviation {:type "string"
                               :analyzer "simple_analyser"}
           :book-title {:type "string"
                        :index "analyzed"
                        :analyzer "german"}
           :title {:type "string"
                   :index "analyzed"
                   :analyzer "german"}
           :text {:type "string"
                  :index "analyzed"
                  :analyzer "german_html"}
           :notes {:type "string"
                   :index "analyzed"
                   :analyzer *notes-analyser*}
           :title-en {:type "string"
                      :index "analyzed"
                      :analyzer "english"}
           :text-en {:type "string"
                     :index "analyzed"
                     :analyzer "english"}}}
         "book"
         {:properties
          {:book {:type "string" :index "not_analyzed"}
           :updated {:type "date" :index "not_analyzed"}
           :book-abbreviation {:type "string"
                               :analyzer "simple_analyser"}
           :title {:type "string"
                   :index "analyzed"
                   :analyzer "german"}
           :text {:type "string"
                  :index "analyzed"
                  :analyzer "german_html"}
           :file {:type "string"
                  :index "not_analyzed"}}}}
        settings
        {:analysis
         {:filter {"german_stemmer" {:type "stemmer"
                                     :language "light_german"}
                   "german_stop" {:type "stop"
                                  :stopwords "_german_"}}
          :analyzer {"german_html" {:tokenizer "standard"
                                    :filter ["standard"
                                             "lowercase"
                                             "german_stop"
                                             "german_normalization"
                                             "german_stemmer"]
                                    :char_filter "html_strip"}
                     "simple_analyser" {:tokenizer "standard"
                                        :filter "lowercase"}}}}]
    (index/create @conn index
                {:mappings mappings
                 :settings settings})))

(comment
  (doc/analyze @conn "Hinterland<br> Ostpark<p> Ballett Taenzerin und Maenner oder Frauen"
               :index (index) :analyzer "german_html")
  (index/get-settings @conn (index)))

#_(doc/analyze @conn "§§6" :index (index) :analyzer "simple_analyser")

(defn drop-database []
  (index/delete @conn))

(defn drop-index [idx]
  (index/delete @conn idx))

#_(index/get-mapping @conn (index))

(defn list-index-aliases [& idx]
  (index/get-aliases @conn (or idx "_all")))

#_(list-index-aliases)

(defn switch-alias [alias idx-old idx-new]
  (index/update-aliases @conn
                        {:remove {:index idx-old :alias alias}}
                        {:add {:index idx-new :alias alias}}))

(defn add-alias [idx alias]
  (let [current (list-index-aliases idx)]
    (apply index/update-aliases @conn
           (map (fn [[idx v]]
                  {:add {:index idx :alias alias}})
                current))))

(defn drop-alias [alias idx]
  (index/update-aliases @conn {:remove {:index idx :alias alias}}))

;; Elastisch seems to have left this out (at least in the version I'm
;; using).
(defn reindex [source dest & [options]]
  (rest/post @conn (rest/url-with-path @conn "_reindex")
             {:body (merge (or options {})
                           {:source {:index source}
                            :dest {:index dest}})}))

(defn parse-index-name [idx]
  (let [[match? & parts] (re-matches #"(.*)_v([0-9]+)" (name idx))]
    (if match?
      parts
      [idx nil])))

(defn reindex-database [& [{:keys [reset options]}]]
  (let [indexes (list-index-aliases)]
    (doseq [[idx aliases] indexes]
      (let [[root version] (parse-index-name idx)]
        (let [n (Integer. (or version 1))
              new-idx (str root "_v" (if reset
                                       1
                                       (+ 1 n)))]
          (setup-database new-idx)
          (reindex idx new-idx options)
          (switch-alias root idx new-idx)
          (drop-index (name idx)))))))

#_(reindex-database)

;; UGLY -wcp20/10/17.
(defn- example-function-for-fields-renaming []
  (let [new-idx (index 1)]
    (reindex-database :options {:script
                                {:lang "groovy"
                                 :inline
                                 (str "ctx._source.'book-id' = ctx._source.remove(\"book\");"
                                      "ctx._source.'book-abbreviation' = ctx._source.remove(\"abbreviation\");")}})))

#_(reindex-database)
#_(list-index-aliases)
#_(index/get-mapping @conn (index))

(defn reset-database []
  (let [idx (index 1)]
    (drop-database)
    (setup-database idx)
    (add-alias idx (index))))

(defn put-paragraph [par]
  (log/debug "indexing paragraph " (:id par))
  (doc/put @conn (index) "paragraph" (:id par) par))

#_(put-paragraph {:id "foo" :data "bar"})

(defn add-batch-data [type recs]
  "Record in batch ops need to carry the relevant metadata, such as
  the _index and the _type.  This function just does that."
  (map (fn [rec]
         (assoc rec
                :_index (index)
                :_type type
                :_id (:id rec)))
       recs))

(def #^:dynamic *index-batch-size* 64)

(defn put-book-bulk [book]
  (let [[head & paragraphs] book
        {:keys [id title]} head]
    (log/info+ "indexing book " id " " title)
    (doc/put @conn (index) "book" (:id head) head)
    (->> paragraphs
         (partition-all *index-batch-size*)
         (map (partial add-batch-data "paragraph"))
         (map bulk-index)
         (pmap #(bulk @conn %))
         (map count)
         (reduce +))))

(defn put-book [book]
  (let [[head & paragraphs] book
        {:keys [id title]} head]
    (log/info+ "indexing book " id " " title)
    (->> paragraphs
         rest
         (map put-paragraph)
         count)))

(defn clean-query-result [result]
  (->> (hits-from result)
       (map :_source))
  #_(map (fn [{:keys [_id _score _type _source]}]
         (merge {:score _score} _source))
       ))

(defn parse-query-string [string]
  (let [re #"([a-z]+):(\w+)"
        p (re-matcher re  string)
        filters (->> (map rest (re-seq re string))
                     (reduce (fn [m [k v]]
                               (update m k conj v))
                             {}))]
    [(string/replace string re "")
     filters]))

#_(parse-query-string "unter book:bgb maenschen book:stgb")

(defn string->query-options [string]
  (let [[string specials] (parse-query-string string)
        match-query {:bool
                     {:should
                      (concat [{:match {:book-title {:query string :boost 0.3}}}
                               {:match {:title {:query string :boost 2}}}
                               {:match {:title-en {:query string :boost 2}}}
                               {:match {:text string}}
                               {:match {:text-en string}}
                               {:match {:notes {:query string :boost 0.5}}}]
                              (map (fn [v]
                                     {:match {:book-abbreviation {:query v :boost 3}}})
                                   (get specials "book"))
                              (map (fn [v]
                                     {:match {:designation {:query v :boost 3}}})
                                   (get specials "sec")))
                      :minimum_should_match 1}}]
    [:query match-query]))

#_(string->query-options "vater book:FamFG mutter book:BGB")

(defn search-paragraphs [query & options]
  (cond (string? query)
        (apply search-paragraphs (string->query-options query) options)

        (some? query)
        (apply doc/search @conn (index) "paragraph"
               (concat query
                       [:highlight {:fields {:title {} :text {} :title-en {} :text-en {}}}]
                       options))

        :else
        (apply doc/search @conn (index) "paragraph" options)))

#_(->> (search-paragraphs "eltern book:FamFG" :size 1000)
     :hits :hits
     (map #(get-in % [:_source :book-abbreviation])))

(defn get-paragraph [id]
  (doc/get @conn (index) "paragraph" id))

#_(doc/get @conn (index 1) "paragraph" "BJNR159800003BJNE001100000")

#_(get-paragraph "BJNR159800003BJNE001100000")
#_(get-paragraph "BJNR258700008BJNE017700000")

#_(get-paragraph "BJNR258700008BJNE010400000")
#_(get-paragraph "BJNR258700008")
#_(take 10 (search-paragraphs "father"))

#_(doc/search @conn (index) "paragraph" :query {:match {:text "Deutschland"}})
#_(search-paragraphs "father")

(defn search-all-paragraphs [query]
  (doc/scroll-seq @conn (search-paragraphs query :size 128 :scroll "1m")))

(defn list-all-books []
  (->> (search-all-paragraphs nil)
       (map #(get-in % [:_source :abbreviation]))
       (into #{})))

(defn count-books []
  (count (list-all-books)))

(defn count-paragraphs []
  (-> (doc/search @conn (index) "paragraph" :size 0)
      (get-in [:hits :total])))

(defn update-paragraph [id doc]
  (doc/update-with-partial-doc @conn (index) "paragraph" id doc))

