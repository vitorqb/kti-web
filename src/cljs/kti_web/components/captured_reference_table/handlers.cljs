(ns kti-web.components.captured-reference-table.handlers
  (:require
   [kti-web.components.captured-reference-table.helpers :as helpers]
   [kti-web.event-handlers :refer [gen-handler-vec] :as event-handlers]
   [kti-web.pagination :as pagination]))

(def refresh-paginated-vec
  [(fn reduce-before [state _ {:keys [page pageSize]}]
     (-> state
         (assoc :refs nil :status nil)
         (assoc-in [:table :loading] true)
         (assoc-in [:table :page] page)
         (assoc-in [:table :pageSize] pageSize)))

   (fn do-action
     [{:keys [filters]}
      {:keys [get-paginated-captured-references!]}
      {:keys [page pageSize]}]
     {:pre [(number? page)
            (number? pageSize)
            (fn? get-paginated-captured-references!)]}
     (get-paginated-captured-references!
      {:page (inc page)
       :page-size pageSize
       :filters (->> filters
                     helpers/remove-empty-filters
                     (map helpers/filter->hash-map)
                     (apply merge))}))

   (fn reduce-after [state _ _ {:keys [error? data]}]
     {:pre [(or error? (pagination/is-paginated? data))]}
     (as-> state it
       (assoc-in it [:table :loading] false)
       (if-not error?
         (-> it
             (assoc-in [:table :pages] (helpers/calc-page-size data))
             (assoc :refs (:items data)))
         (assoc it :status {:errors data}))))])

(defn handle-refresh! [state props]
  (fn [e]
    (event-handlers/handle!-vec e state props refresh-paginated-vec)))

(defn handle-filters-change [state props]
  (fn [new-filters]
    {:pre [(sequential? new-filters)]}
    (swap! state assoc :filters new-filters)))

(defn handle-add-empty-filter [state props]
  (fn []
    (swap! state update :filters conj helpers/empty-filter)))

(defn handle-show-article [state {:keys [on-show-article]}]
  {:pre [(or (nil? on-show-article) (fn? on-show-article))]}
  (fn [article-id]
    (on-show-article article-id)))
