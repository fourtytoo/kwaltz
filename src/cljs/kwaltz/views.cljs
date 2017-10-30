(ns kwaltz.views
  (:require [re-frame.core :refer [subscribe dispatch]]
            [reagent.core :as reagent]
            [kwaltz.modal :as modal]
            [clojure.string :as string]
            [cljs.pprint :as pprint]
            [clojure.browser.dom :as dom]
            [kwaltz.util :as util]
            [cljsjs.typeahead-bundle]))

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

(defn spinner01 []
  [:div.spinner {:style {:padding-top "4em"}}
   [:div.rect1]
   [:div.rect2]
   [:div.rect3]
   [:div.rect4]
   [:div.rect5]])

(defn loading-throbber []
  (let [loading? (subscribe [:loading?])]
    (when @loading?
      [:div.loading.text-center {:style {:margin-top "3em"}}
       [:div.three-quarters-loader "Loading..."]])))

(defn dangerous
  ([comp content]
   (dangerous comp nil content))
  ([comp props content]
   [comp (assoc props :dangerouslySetInnerHTML {:__html content})]))

(defn notes-area []
  (let [notes (subscribe [:notes])
        paragraph (subscribe [:paragraph])
        ;; text (reagent/atom @notes)
        ]
    (fn []
      [:div
       [:label {:for "notes"} "Notes:"]           
       [:textarea {:class "form-control"
                   :rows 10
                   :id "notes"
                   :placeholder "Add notes here"
                   :value @notes
                   :on-change (fn [e]
                                #_(reset! text (-> e .-target .-value))
                                (dispatch [:update-notes (-> e .-target .-value)]))}]
       [:span.input-group-btn {}
        [:button {:type "button"
                  :class (if (= @notes (get-in @paragraph [:_source :notes]))
                           "btn"
                           "btn btn-warning")
                  :title "Save notes into database"
                  :id "save"
                  :on-click (fn [_]
                              (dispatch [:save-notes @notes]))}
         "Save"]
        [:button.btn {:type "button"
                      :title "Translate the original text and append the translation to the notes."
                      :on-click (fn [_]
                                  (dispatch [:request-translation (:_id @paragraph) "en"]))}
         "Translate"]]])))

#_(defn notes-area []
  (let [notes (subscribe [:notes])]
    (fn []
      [:div
       [:label {:for "notes"} "Notes:"]           
       [:textarea {:class "form-control"
                   :rows 10
                   :id "notes"
                   :placeholder "Add notes here"
                   :value @notes
                   :on-change (fn [e]
                                (add-class (dom/get-element "save") "btn-warning")
                                (reset! notes (-> e .-target .-value)))}]
       [:span.input-group-btn {}
        [:button.btn {:type "button"
                      :title "Save notes into database"
                      :id "save"
                      :on-click (fn [_]
                                  (when-not (empty? @notes)
                                    (dispatch [:save-notes @notes])
                                    (reset! notes "")))}
         "Save"]
        [:button.btn {:type "button"
                      :title "Translate the original text to English and append the translation to the notes."
                      :on-click (fn [_]
                                  (dispatch [:translate-text]))}
         "Translate"]]])))

(defn paragraph-text []
  (let [paragraph (subscribe [:paragraph])
        bookmarks (subscribe [:bookmarks])]
    (fn []
      (let [p (:_source @paragraph)]
        (if p
          [:div
           [:h4.list-group-item-heading
            (:designation p)
            " " [:a {:href (:url p)
                     :title (:book-title p)
                     :target "_blank"}
                 (:book-abbreviation p)]
            (let [on-click (fn [id]
                             (dispatch [:set-current-paragraph id]))]
              [:div {:style {:float "right"}}
               [:span.input-group-btn
                [:button.btn {:title "print"
                              :on-click (fn [e]
                                          (js/window.print))}
                 [:span.glyphicon.glyphicon-print {:aria-hidden true}]]
                [:button {:title "bookmark"
                          :class (if (get @bookmarks (:id p))
                                   "btn btn-warning"
                                   "btn")
                          :on-click (fn [e]
                                      (dispatch [:toggle-bookmark (:id p)]))}
                 [:span.glyphicon.glyphicon-bookmark {:aria-hidden true}]]]
               [:ul.pager.small
                [:li.previous [:a {:href "#"
                                   :on-click #(on-click (:previous p))}
                               [:span.glyphicon.glyphicon-backward {:aria-hidden true
                                                                    :title "backward"}]]]
                [:li.next [:a {:href "#"
                               :on-click #(on-click (:next p))}
                           [:span.glyphicon.glyphicon-forward {:aria-hidden true
                                                               :title "forward"}]]]]])
            [:br]
            [:small (string/join " -> " (:outline p))]]
           [:h4.list-group-item-heading (:title p)
            [:br]
            [:small (:title-en p)]]
           (when (:text p)
             (dangerous :div (:text p)))
           [:div [:pre {:style {:white-space "pre-wrap"}}
                  (:text-en p)]]
           [notes-area]]
          [:div])))))

(defn split-at-em-tags [string]
  (string/split string #"</?em>"))

(defn paragraph->html [string]
  (let [parts (->> string
                   split-at-em-tags
                   (map util/remove-all-html-tags))]
    (->> parts
         rest
         (partition-all 2 )
         (mapcat (fn [[x y]]
                   (if y
                     [[:mark x]
                      y]
                     [[:mark x]])))
         (cons (first parts))
         #_vec)))

#_(paragraph->html "foo<a> bar <em>gigio</em> peppo")


(defn deselect-elements [elements]
  (doseq [e elements]
    (remove-class e "active")))

(defn select-element [element]
  (-> element parent children deselect-elements)
  (add-class element "active"))

(defn search-hits-list []
  (let [hits (subscribe [:search-hits])
        plgit (fn [l]
                (->> l
                     (map paragraph->html)
                     (interpose " [...] ")))]
    (fn []
      [:ul.list-group {:id "hits"
                       :style {:font-size "75%"}}
       (map-indexed (fn [i hit]
                      [:li.list-group-item {:key i
                                            :on-click (fn [event]
                                                        (-> event current-target select-element)
                                                        (dispatch [:set-current-paragraph (:_id hit)]))}
                       [:h5.list-group-item-heading {:title (:_id hit)}
                        (get-in hit [:_source :designation])
                        " " (get-in hit [:_source :book-abbreviation])
                        ": " (get-in hit [:_source :title])
                        [:code.small.text-right {:style {:float "right"}
                                                 :title (str "match score = " (:_score hit))}
                         (pprint/cl-format nil "~,2f" (:_score hit))]
                        [:br]
                        [:small (get-in hit [:_source :title-en])]]
                       [:p.list-group-item-text
                        (plgit (get-in hit [:highlight :text]))]
                       [:p.list-group-item-text
                        (plgit (get-in hit [:highlight :text-en]))]])
                    @hits)])))

#_(pprint/cl-format nil "~,2f" 12.3456)

(defn confirm [text fun]
  (dispatch [:modal {:show? true
                     :child [modal/confirm text fun]
                     :size :small}]))

(defn search-bar []
  (let [loading? (subscribe [:loading?])
        refreshing? (subscribe [:refreshing?])
        query-string (reagent/atom "")
        default-max-hits 42
        hits-per-query (reagent/atom default-max-hits)
        search (fn []
                 (when-not (or @loading?
                               (empty? @query-string))
                   (dispatch [:set-search-string @query-string @hits-per-query])
                   (reset! query-string "")))
        refresh (fn []
                  (confirm [:p {}
                            "Are you sure you want to refresh the entire database?"
                            [:br]
                            "This may take a long time"]
                           #(dispatch [:ask-documents-refresh])))]
    (fn []
      [:nav.navbar.navbar-default ;; .navbar-fixed-top
       [:div.container-fluid
        [:form.navbar-form
         [:div.input-group
          [:input.form-control.typeahead {:type "text"
                                          :id "search_entry"
                                          :style {:width "60em"}
                                          :placeholder "Enter query"
                                          :on-key-press (fn [e]
                                                          (when (= 13 (.-charCode e))
                                                            (search)))
                                          :on-change #(reset! query-string (-> % .-target .-value))}]
          [:span.input-group-btn
           [:button.btn.btn-default {:title "Start DB query"
                                     :on-click #(search)}
            [:span.glyphicon.glyphicon-search {:aria-hidden true}]]
           #_[:button.btn.btn-file {:title "Choose file to compare text with"
                                  ;; ;TODO: -wcp18/10/17.
                                  :on-click identity}]]
          [:select.form-control {:style {:width "5em"}
                                 :value default-max-hits
                                 :title "maximum search hits"
                                 :on-change #(reset! hits-per-query (-> % .-target .-value js/parseInt))}
           [:option {:value 10} 10]
           [:option {:value 25} 25]
           [:option {:value default-max-hits} default-max-hits]
           [:option {:value 100} 100]
           [:option {:value 0} "∞"]]]]]])))

(defn setup-typeahead []
  (let [matcher (fn [strs]
                  (fn [q cb]
                    (cb (clj->js ["foo" "bar"]))))]
    (-> (js/document.getElementById "search_entry")
        (js/jQuery.prototype.typeahead {:hint true :hightlight true :minLenght 1} {:name "states" :source (matcher nil)})
        js->clj)))

(defn home-panel []
  (let []
    (fn []
      [:div
       [:div.topbar
        [:div.container-fluid
         [:div.row
          [:div.col-lg-12 ; .col-lg-offset-3
           [search-bar]]]]]
       [:div.main-content
        [:div.container-fluid
         [:div.row
          [:div {:class "col-lg-4 pre-scrollable hidden-print"
                 :style {:max-height "90vh"
                         :resize "horizontal"}}
           [loading-throbber]
           [search-hits-list]]
          [:div.col-lg-8.pre-scrollable {:style {:max-height "90vh"}}
           [paragraph-text]]]]]])))

(defn profile-panel []
  [:div
   "To be continued..."])

(defmulti panels identity)
(defmethod panels :home-panel [] [home-panel])
(defmethod panels :profile-panel [] [profile-panel])
(defmethod panels :default [] [:div  "something went wrong"])


(defn main-panel []
  (let [active-panel (subscribe [:active-panel])]
    (fn []
      [:div
       (panels @active-panel)
       [modal/modal]])))
