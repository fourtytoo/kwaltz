(ns kwaltz.db)

(def default-db
  {:active-panel :home-panel
   :loading? false
   :refreshing? false
   :page 0
   :page-size 10
   :search-hits []
   :paragraph nil
   :bookmarks #{}})
