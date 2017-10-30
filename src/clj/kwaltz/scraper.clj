(ns kwaltz.scraper
  (:require [clj-time.coerce :as ctime]
            [clj-time.core :as time]
            [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.data.xml :as dxml]
            [clojure.zip :refer [xml-zip node]]
            [clojure.data.zip :as dz]
            [clojure.data.zip.xml :as dzx]
            [clojure.string :as string]
            [hiccup.core :refer [html]]
            #_[clojure.tools.logging :as log]
            [onelog.core :as log]
            [jsoup.soup :as soup]
            [org.httpkit.client :as http]
            [kwaltz.config :as conf]
            [kwaltz.numbers :refer [sum-words]]
            [clojure.data.json :as json]
            [autoclave.core :as autoclave]))

(def #^:dynamic *default-root-page* "https://www.gesetze-im-internet.de/aktuell.html")

(defn root-page [& [url]]
  (or url (conf/conf :scrape) *default-root-page*))

(defn sleep [secs]
  (java.lang.Thread/sleep (* 1000 secs)))

(defn retry [fun waits & {:keys [ex-handler]
                          :or {ex-handler #(log/error (.getMessage %))}}]
  (loop [[time & rem] waits]
    (let [{:keys [res ex]} (try
                             {:res (fun)}
                             (catch Exception e
                               (when ex-handler
                                 (ex-handler e))
                               {:ex e}))]
      (if-not ex
        res
        (do
          (sleep time)
          (if (seq rem)
            (recur rem)
            (throw ex)))))))

(defn http-success? [status]
  (and status
       (< 199 status 300)))

(defn http-get! [url]
  (let [result (-> (http/get url) deref)
        status (:status result)]
    (if (http-success? status)
      result
      (throw (ex-info "HTTP GET failed" result)))))

#_(http-get! "https://www.gesetze-im-internet.de/englisch_bgb/index.html")

#_(defn slurp-url [url]
  (retry #(soup/get! url :encoding "iso-8859-1") [2 5 10 20 40]))

;; The page doesn't correctly declare an encoding.  We have to force
;; it ourselves, first downloading the page, then paring it specifying
;; ISO-8859-1.
(defn slurp-url [url]
  (-> (retry #(http-get! url) [2 5 10 20 40])
      :body
      (string/replace "\240" " ") ;get rid of the funky non-break spaces
      (soup/parse :base-uri url :encoding "iso-8859-1")))

(defn extract-root-links [body]
  (->> body
     (soup/select "div#content > div#container a.alphabet[href]")
     (soup/attr "abs:href")))

(defn get-root-links [url]
  (-> (slurp-url url)
      extract-root-links))

(defn extract-books-list [body]
  (->> body
       (soup/select "div > p > a[href$=.html]")
       (soup/attr "abs:href")))

(defn get-books-list
  "For each law book listed at URL, return a link (URL) to its Table
  of Contents."
  [url]
  (-> (slurp-url url)
      extract-books-list))


(defn select-index-headline [body]
  (soup/select "div > h2.headline" body))

(defn extract-book-link [body suffix selector]
  (->> body
       select-index-headline
       (soup/select (str "a[href$=." suffix "]:" selector))
       (soup/attr "abs:href")
       first))

(defn extract-book-xml-link [body]
  (extract-book-link body "zip" "contains(XML)"))

(defn extract-book-html-link [body]
  (extract-book-link body "html" "contains(HTML)"))

(defn extract-book-english-link [body]
  (extract-book-link body "html" "has(img[title~=englisch])"))

(defn as-zip-stream [thing]
  (-> (io/input-stream thing)
      java.util.zip.ZipInputStream.))

(defn slurp-zip-entry [stream]
  (let [out (java.io.StringWriter.)]
    (clojure.java.io/copy stream out)
    (.toString out)))

(defn map-zip-stream [f stream]
  (let [zs (as-zip-stream stream)]
    (->> (repeatedly (fn []
                       (let [entry (.getNextEntry zs)]
                         (when entry
                           (f entry zs)))))
         (take-while some?))))

(defn unzip-stream [stream]
  (map-zip-stream (fn [entry in]
                    {:name (.getName entry)
                     :comment (.getComment entry)
                     :time (-> entry .getTime ctime/from-long)
                     :body (slurp-zip-entry in)})
                  stream))

(defn slurp-zip [url]
  (-> (retry #(http-get! url) [2 5 10 20 40])
      :body
      unzip-stream))

(defn pathname-extension? [pathname extension]
  (string/ends-with? (string/lower-case pathname)
                     (str "." extension)))

(defn path-to-xml? [pathname]
  (pathname-extension? pathname "xml"))

(defn find-xml-zipentry [entries]
  (->> entries
       (filter (comp path-to-xml? :name))
       first))

(defn slurp-book-xml-zip [url]
  (-> (slurp-zip url)
      find-xml-zipentry
      (update :body dxml/parse-str)))

(defn extract-book-norms [xml]
  (-> (xml-zip xml)
      (dzx/xml-> :norm)))

;; (slurp-xml-zip "https://www.gesetze-im-internet.de/luftvg/xml.zip")

(comment
  (defn index-xml-book [url]
    (let [string (-> (slurp-zip url)
                     find-xml-zipentry
                     :body)]
      (-> string
          .getBytes
          io/input-stream
          dxml/source-seq
          xmlidx.core/index-text-path-simple)))

  (run! println (index-xml-book "https://www.gesetze-im-internet.de/luftvg/xml.zip")))

(defn simplify-xml [element]
  (if (map? element)
    (into [(:tag element)
           (:attrs element)]
          (map simplify-xml (:content element)))
    element))

(defn xml->html [element]
  (simplify-xml element))

(defn html->string [element]
  (html element))

(defn normalise-designation-string [string]
  (let [words (string/split string #"\s+")
        [number end] (sum-words words)]
    (if (> end 0)
      (str (string/join " " (drop end words))
           " " number)
      string)))

(let [rx (fn [word]
           (re-pattern (str "(?i)" word "\\s+([0-9]\\w*)")))
      regexes [[(rx "Buch") :book]
               [(rx "Book") :book]
               [(rx "Abschnitt") :section]
               [(rx "Titel") :title]
               [(rx "Title") :title]
               [(rx "Untertitel") :subtitle]
               [(rx "Subtitle") :subtitle]
               [(rx "Kapitel") :chapter]
               [(rx "Chapter") :chapter]
               [(rx "Anlage") :unit]
               [(rx "Unit") :unit]
               [(rx "Absatz") :paragraph]
               [(rx "Abs\\.?") :paragraph]
               [(rx "Paragraph") :paragraph]
               [(rx "Artikel") :article]
               [(rx "Article") :article]
               [(rx "Art\\.?") :article]
               [(rx "Anhang") :attachment]
               [(rx "Attachment") :attachment]
               [(rx "Teil") :part]
               [(rx "Part") :part]
               [(rx "Sections?") :section]
               [(rx "§§?") :section]]]
  (defn parse-designation-string
    "From a paragraph string, such as \"Buch 1\" or \"Title 2\",
  extract the number and the designation type as keyword."
    [string]
    (let [string (normalise-designation-string string)]
      (->> regexes
           (map (fn [[regex key]]
                  (let [[match? x] (re-matches regex string)]
                    (when match?
                      [key x]))))
           (drop-while nil?)
           first))))

#_(parse-designation-string "§ 2371")
#_(normalise-designation-string "Zweite Buch")
#_(parse-designation-string "Zweite Buch")
#_(parse-designation-string "Article 6")

(defn normalise-section-string [string]
  "From a paragraph string, such as \"§ 17a\" or \"Section 22b\",
extract just the id (the \"17a\" bit of the first example)."
  (let [[match? sec x _ _ to y desc] (re-matches #"(?i).*(§§?|sections?)\s*([0-9]\w*)(\s*(-|,|–|(bis))\s+([0-9]\w*))?(\s+.*)?" string)]
    (if match?
      [(if y
         [x y]
         x)
       (when desc
         (string/trim desc))]
      (do
        (log/warn+ "couldn't parse " string " as section string")
        [string nil]))))

(comment
  (normalise-section-string "Sections 3 – 6 (repealed)")
  (normalise-section-string "(XXXX) §§ 1858 bis 1881")
  (normalise-section-string " Section  176e  Very Important Section!! ")
  (normalise-section-string " Section  176e"))

(defn update*
  "Just like UPDATE, but perform the update of the map only if the key
  is already present."
  [m k f & args]
  (if (get m k)
    (apply update m k f args)
    m))

;; The pages provided by gesetze-im-internet.de do not conform to any
;; semantic HTML principle.  No surprise here; for lawyer by lawyers.
;; But, the XML isn't any better.  In the XML file we see a flat
;; stream of paragraph elements (the norms) whose true semantics are
;; implied by the position in the stream and the text within.  The
;; hierarchical place of a section (book, title, subtitle, chapter) is
;; impled by the paragraphs coming before it.
;; 
;; Put a nutcracker in a monkey's hands and he will keep bashing his
;; nuts with it.  Pun intended.

(defn to-lower [c]
  (Character/toLowerCase c))

(defn string-common-prefix [s1 s2]
  (->> (map vector
            (seq s1)
            (seq s2))
       (take-while (fn [[x y]]
                     (= (to-lower x) (to-lower y))))
       (map first)))

(defn same-outline-marker? [s1 s2]
  (> (count (string-common-prefix (normalise-designation-string s1)
                                  (normalise-designation-string s2)))
     (/ (min (count s1) (count s2)) 2)))

(defn extract-norm-metadata [xml]
  (let [keys [[:jurabk :book-abbreviation]
              [:enbez :designation]
              [:titel :title]
              [:langue :title]
              [[:gliederungseinheit :gliederungsbez] :outline-designation]
              [[:gliederungseinheit :gliederungstitel] :outline-title]]]
    (reduce (fn [m [kold knew]]
              (if-let [v (if (vector? kold)
                           (apply dzx/xml1-> xml (conj kold dzx/text))
                           (dzx/xml1-> xml kold dzx/text))]
                (assoc m knew v)
                m))
            {} keys)))

(comment
  (-> metadata
      (update* :designation (comp first normalise-section-string))
      (update* :outline-designation normalise-designation-string)))

(defn parse-book-xml [xml]
  (->> (extract-book-norms xml)
       (map (fn [norm]
              (merge (->> (dzx/xml1-> norm :metadaten extract-norm-metadata))
                     {:id (dzx/xml1-> norm (dzx/attr :doknr))
                      :text (->> (dzx/xml-> norm :textdaten :text :Content dz/children node)
                                 (map (comp html->string xml->html))
                                 (string/join "\n"))})))))

#_(slurp-book-xml-zip "https://www.gesetze-im-internet.de/luftvg/xml.zip")

#_(-> (slurp-book-xml-zip "https://www.gesetze-im-internet.de/bgb/xml.zip")
      :body
      parse-book-xml)
#_(slurp-book-xml-zip "https://www.gesetze-im-internet.de/m_nz2eurobek_2013-01/xml.zip")
#_(-> (slurp-book-xml-zip "https://www.gesetze-im-internet.de/m_nz2eurobek_2013-01/xml.zip")
    :body
    parse-book-xml)

(defn extract-book-html-paragraphs [body]
  (->> body
       (soup/select "div#content > div#container p")))

;;TODO: -wcp4/9/17.
(defn slurp-book-html-text [url]
  (-> (slurp-url url)
      extract-book-html-paragraphs))

#_(defn slurp-english-book-html-text [url]
  (-> url
      slurp-url
      extract-book-html-link
      slurp-url))

#_(-> (slurp-url "https://www.gesetze-im-internet.de/bgb/index.html")
    extract-book-xml-link)

(defn extract-book-links
  "From a page body, look for the links to the XML document, the
  complete HTML document, and the English translation.  Return the
  result in a map."
  [body]
  {:xml (extract-book-xml-link body)
   :html (extract-book-html-link body)
   :html-en (extract-book-english-link body)})

#_(extract-book-links (slurp-url "https://www.gesetze-im-internet.de/bgb/index.html"))

(defn add-outline-to-paragraphs [paragraphs]
  (letfn [(walk [branch paragraphs]
            (lazy-seq
             (when (not (empty? paragraphs))
               (let [par (first paragraphs)
                     designation (:outline-designation par)
                     branch' (if designation
                               (conj (vec (take-while (complement (partial same-outline-marker? designation))
                                                      branch))
                                     designation)
                               branch)]
                 (cons (assoc par :outline branch')
                       (walk branch' (rest paragraphs)))))))]
    (walk [] paragraphs)))

(defn add-sibling-links [paragraphs]
  (let [head (assoc (first paragraphs) :next (:id (second paragraphs)))]
    (->> paragraphs
         (partition-all 3 1)
         (map (fn [[prev par next]]
                (when par
                  (assoc par :previous
                         (:id prev)
                         :next (:id next)))))
         (cons head))))

#_(take-while #(not (same-outline-marker? "foo" %)) ["abc" "baz" "foo" "bar"])

(def sanitize-policy
  "What is allowed and what is discarded in the HTML imported from the online books."
  (autoclave/html-policy :allow-common-block-elements
                         :allow-common-inline-formatting-elements))

(defn sanitize-html [string]
  (autoclave/html-sanitize sanitize-policy string))

(defn normalise-xml-book
  "Convert the XML data, as found in the book downloaded from the
  website, to a list of maps each one containing a paragraph of the
  law."
  [elements]
  (let [[head & norms] elements
        book (:id head)
        book-title (:title head)]
    (->> elements
         (map #(assoc %
                      :book-id book
                      :book-title book-title))
         (map #(update % :text sanitize-html))
         add-outline-to-paragraphs
         add-sibling-links)))

(defn normalise-html-book [body]
  "Parse the BODY's paragraphs and try to reconstruct the semantic
structure of the sections within."
  (letfn [(section-head? [element]
            (->> element
                 (soup/select "p[style~=center]")
                 empty?
                 not))
          (parse-head-ps [html-elements]
            ;; the designation and the title appear in the same <P>
            ;; element divided by a <BR>
            (->> html-elements
                 (mapcat #(.childNodes %))
                 (filter (partial instance? org.jsoup.nodes.TextNode))
                 soup/text))
          (parse-text-ps [html-elements]
            (->> html-elements
                 soup/text
                 ;; the last <P> is a link to the top of the page
                 drop-last
                 (string/join "\n")))]
    (->> body
         (drop-while (comp not section-head?))
         (partition-by section-head?)
         (partition 2)
         (map (fn [[heads texts]]
                (let [[id title] (parse-head-ps heads)]
                  {:designation id
                   :title title
                   :text (parse-text-ps texts)}))))))

(defn merge-translation [book title-key text-key translations]
  (let [translation-map (into {}
                              (->> translations
                                   (filter :designation)
                                   (map (juxt (fn [norm]
                                                (-> norm
                                                    :designation
                                                    parse-designation-string))
                                              identity))))]
    (map (fn [norm]
           (let [des (:designation norm)]
             (if des
               (let [translation (get translation-map
                                      (parse-designation-string des))]
                 (assoc norm
                          title-key (:title translation)
                          text-key (:text translation)))
               norm)))
         book)))

(defn check-translation [key book]
  (doseq [norm book]
    (when-not (get norm key)
      (log/warn "Missing translation for: " norm)))
  book)

(defn fetch-book-from-toc [url]
  (log/info+ "fetching " url)
  (let [links (-> url slurp-url extract-book-links)
        book (cond (:xml links)
                   (let [{:keys [name time body]} (slurp-book-xml-zip (:xml links))]
                     (->> body
                          parse-book-xml
                          normalise-xml-book
                          (map #(assoc %
                                       :file name
                                       :url url
                                       :modified time))))

                   (:html links)
                   (let [body (slurp-book-html-text (:html links))]
                     (normalise-html-book body))

                   :else
                   (do
                     (log/error "cannot find link to book text at " url)
                     nil))]
    (when (empty? book)
      (log/error+ "Missing book at " url))
    (if-let [url (:html-en links)]
      (->> url
           fetch-book-from-toc
           (merge-translation book :title-en :text-en)
           (check-translation :title-en))
      book)))

#_(-> "https://www.gesetze-im-internet.de/englisch_bgb/index.html"
      slurp-book-html-text 
      normalise-html-book)

#_(-> "https://www.gesetze-im-internet.de/englisch_bgb/index.html"
    fetch-book-from-toc)

#_(-> "https://www.gesetze-im-internet.de/englisch_bgb/index.html"
    slurp-url)

#_(fetch-book-from-toc "https://www.gesetze-im-internet.de/m_nz2eurobek_2013-01/index.html")
#_(map (juxt :designation :title :title-en) (fetch-book-from-toc "https://www.gesetze-im-internet.de/bgb/index.html"))
#_(map :outline-designation (fetch-book-from-toc "https://www.gesetze-im-internet.de/bgb/index.html"))
#_(-> "https://www.gesetze-im-internet.de/bfdg/index.html"
    fetch-book-from-toc
    count)

(defn all-book-tocs
  "Return a list of URLs to the Table of Contents of each law book is."
  [root]
  (->> (get-root-links root)
       (mapcat get-books-list)
       (into #{})))

(defn scrape-all-books [& [url]]
  (->> (root-page url)
       all-book-tocs
       (pmap fetch-book-from-toc)
       (remove empty?)))

(defn scraper-stats []
  (time
   (let [books (scrape-all-books)]
     (println (count books) " books")
     (println (->> books
                   (map count)
                   (reduce +))
              " sections")
     (println (->> (mapcat #(map :text-en %) books)
                   (remove nil?)
                   count)
              " translations"))))

(defn get-all-xml-books [& [url]]
  (->> (root-page url)
       all-book-tocs
       (map extract-book-links)
       (remove nil?)
       (map :xml)
       (remove nil?)
       (map (comp find-xml-zipentry slurp-zip))))

#_(-> (get-all-xml-books)
    first
    :time
    ctime/to-long)

(defn filename [path]
  (last (string/split path #"/")))

(defn save-all-books [output-directory & [url]]
  (doseq [entry (get-all-xml-books (root-page url))]
    (let [name (:name entry)
          output-path (io/file output-directory (filename name))]
      (io/make-parents output-path)
      (log/info+ name " -> " output-path)
      (io/copy (:body entry) output-path)
      (.setLastModified output-path (ctime/to-long (:time entry))))))

#_(save-all-books "/home/wcp/tmp/kwaltz")
