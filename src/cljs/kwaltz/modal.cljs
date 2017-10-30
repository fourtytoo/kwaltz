(ns kwaltz.modal
  (:require
   [re-frame.core :refer [dispatch subscribe]]))


(defn modal-panel
  [{:keys [child size show?]}]
  [:div {:class "modal-wrapper"}
   [:div {:class "modal-backdrop"
          :on-click (fn [event]
                      (do
                        (dispatch [:modal {:show? (not show?)
                                           :child nil
                                           :size :default}])
                        (.preventDefault event)
                        (.stopPropagation event)))}]
   [:div {:class "modal-child"
          :style {:width (case size
                           :extra-small "15%"
                           :small "30%"
                           :large "70%"
                           :extra-large "85%"
                           "50%")}} child]])

(defn modal []
  (let [modal (subscribe [:modal])]
    (fn []
      [:div
       (if (:show? @modal)
         [modal-panel @modal])])))

(defn- close-modal []
  (dispatch [:modal {:show? false :child nil}]))

(defn confirm [text & [on-confirmation]]
  [:div {:class "modal-content panel-primary"}
   [:div {:class "modal-header panel-heading"}
    [:button {:type "button" :title "Cancel"
              :class "close"
              :on-click #(close-modal)}
     [:i {:class "material-icons"} "close"]]
    [:h4 {:class "modal-title"} "Please confirm"]]
   [:div {:class "modal-body"}
    [:div [:b text]]]
   [:div {:class "modal-footer"}
    [:button {:type "button" :title "Ok"
              :class "btn btn-default"
              :on-click (fn [e]
                          (when on-confirmation
                            (on-confirmation))
                          (close-modal))}
     "Ok"]]])

(defn error [text]
  [:div {:class "modal-content panel-danger"}
   [:div {:class "modal-header panel-heading"}
    [:button {:type "button" :title "Cancel"
              :class "close"
              :on-click #(close-modal)}
     [:i {:class "material-icons"} "close"]]
    [:h4 {:class "modal-title"} "An error occurred"]]
   [:div {:class "modal-body"}
    [:div [:b text]]]
   [:div {:class "modal-footer"}
    [:button {:type "button" :title "Ok"
              :class "btn btn-default"
              :on-click #(close-modal)}
     "Ok"]]])

