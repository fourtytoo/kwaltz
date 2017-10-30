(ns kwaltz.events
  (:require [ajax.core :as ajax]
            [cljs.core.async :refer [<!]]
            [cljs.pprint :as pprint]
            [kwaltz.db :as db]
            [kwaltz.modal :as modal]
            [kwaltz.util :as util]
            [re-frame.core :refer [dispatch reg-event-db]]
            [cljs-time.coerce :as timec]
            [cljs-time.format :as timef])
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]]))

(reg-event-db
 :initialize-db
 (fn  [_ _]
   db/default-db))

(reg-event-db
 :set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(reg-event-db
 :set-search-string
 (fn [db [_ query size]]
   (dispatch [:search-database query size])
   (assoc db
          :search-string query
          :search-hits [])))

(reg-event-db
 :set-current-paragraph
 (fn [db [_ id]]
   (dispatch [:fetch-paragraph id])
   (assoc db :paragraph nil)))

(reg-event-db
 :ask-documents-refresh
 (fn [db [_]]
   (dispatch [:refresh-documents])))

(reg-event-db
 :save-notes
 (fn [db [_ notes]]
   (dispatch [:store-notes (get-in db [:paragraph :_id]) notes])
   (assoc-in db [:paragraph :_source :notes] notes)))

(reg-event-db
 :toggle-bookmark
 (fn [db [_ id]]
   (prn (:bookmarks db))                ; -wcp20/10/17.
   (update db :bookmarks (fn [bs]
                           (if (get bs id)
                             (disj bs id)
                             (conj bs id))))))

(reg-event-db
 :update-paragraph
 (fn [db [_ k v]]
   (assoc-in db [:paragraph :_source k] v)))

(reg-event-db
 :update-notes
 (fn [db [_ v]]
   (assoc-in db [:notes] v)))

#_(reg-event-db
 :translate-text
 (fn [db [_]]
   (dispatch [:request-translation
              (get-in db [:paragraph :_id])])
   db))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(reg-event-db
 :search-database
 (fn [db [_ query size]]
   (ajax/GET
    "/api/search/paragraphs"
    {:handler       #(dispatch [:process-search-response %])
     :error-handler #(dispatch [:bad-search-response %])
     :params {:query query :size size}
     :response-format :json
     :keywords? true})
   (assoc db :loading? true)))

(reg-event-db
 :fetch-paragraph
 (fn [db [_ id]]
   (ajax/GET
    (str "/api/paragraph/" id)
    {:handler       #(dispatch [:process-fetch-response %])
     :error-handler #(dispatch [:bad-fetch-response %])
     :keywords? true})
   db))

(reg-event-db
 :store-notes
 (fn [db [_ id notes]]
   (prn :store-notes id notes)          ; -wcp16/10/17.
   (ajax/PATCH
    (str "/api/paragraph/" id)
    {:handler       #(dispatch [:process-store-notes-response %])
     :error-handler #(dispatch [:bad-store-notes-response %])
     :params {:body {:notes notes}}
     :format :json
     :keywords? true})
   db))

(reg-event-db
 :refresh-documents
 (fn [db [_]]
   (ajax/GET
    (str "/api/refresh")
    {:handler       #(dispatch [:process-refresh-response %])
     :error-handler #(dispatch [:bad-refresh-response %])
     :response-format :json
     :keywords? true})
   (assoc db :refreshing? true)))

(reg-event-db
 :request-translation
 (fn [db [_ id target]]
   (ajax/GET
    (str "/api/translation/" id "/" target)
    {:handler       #(dispatch [:process-translate-response %])
     :error-handler #(dispatch [:bad-translate-response %])
     :response-format :json
     :keywords? true})
   db))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pp [thing]
  (with-out-str (pprint/pprint thing)))

(defn show-error [response]
  (dispatch [:modal {:show? true
                     :child [modal/error [:pre (pp response)]]
                     :size :large}]))

(reg-event-db
 :process-search-response
 (fn [db [_ response]]
   (assoc db
          :loading? false
          :search-hits (js->clj response))))

(reg-event-db
 :bad-search-response
 (fn [db [_ response]]
   (show-error response)
   (assoc db
          :loading? false
          :search-hits [])))

(reg-event-db
 :process-fetch-response
 (fn [db [_ response]]
   (assoc db
          :paragraph response
          :notes (get-in response [:_source :notes]))))

(reg-event-db
 :bad-fetch-response
 (fn [db [_ response]]
   (show-error response)
   (assoc db
          :paragraph (str "Error: " response)
          :notes nil)))

(reg-event-db
 :process-store-notes-response
 (fn [db [_ response]]
   ;; should set a saved flags or something -wcp30/9/17.
   db))

(reg-event-db
 :bad-store-notes-response
 (fn [db [_ response]]
   (show-error response)
   (dispatch [:set-current-paragraph (:paragraph db)])
   db))

(reg-event-db
 :process-refresh-response
 (fn [db [_ response]]
   (assoc db :refreshing? false)))

(reg-event-db
 :bad-refresh-response
 (fn [db [_ response]]
   (show-error response)
   (assoc db :refreshing? false)))

(defonce translation-date-formatter
  (timef/formatters :date-hour-minute))

(defn translation-date-str [time]
  (timef/unparse translation-date-formatter time))

(reg-event-db
 :process-translate-response
 (fn [db [_ response]]
   (update-in db [:notes]
              (fn [notes]
                (str notes "\n\n"
                     (-> response :time timec/from-long translation-date-str)
                     "\nTranslation from " (:source response) ":\n"
                     (:translation response))))))

(reg-event-db
 :bad-translate-response
 (fn [db [_ response]]
   (show-error response)
   db))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(reg-event-db
 :modal
 (fn [db [_ data]]
   (assoc-in db [:modal] data)))
