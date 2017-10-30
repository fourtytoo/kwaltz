(ns kwaltz.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [reg-sub reg-sub-raw]]))

#_(reg-sub
   :name
   (fn [db]
     (:name db)))

(reg-sub
 :active-panel
 (fn [db _]
   (:active-panel db)))

(reg-sub
 :loading?
 (fn [db]
   (:loading? db)))

(reg-sub
 :refreshing?
 (fn [db]
   (:refreshing? db)))

(reg-sub
 :search-hits
 (fn [db]
   (:search-hits db)))

(reg-sub
 :paragraph
 (fn [db]
   (:paragraph db)))

(reg-sub
   :notes
   (fn [db]
     (:notes db)))

(reg-sub
   :bookmarks
   (fn [db]
     (:bookmarks db)))

#_(reg-sub-raw
 :notes
 (fn [db _]
   (reaction (get-in @db [:paragraph :_source :notes]))))

(reg-sub-raw
 :modal
 (fn [db _]
   (reaction (:modal @db))))
