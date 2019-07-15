(ns kti-web.components.captured-reference-table
  (:require [cljs.core.async :refer [<! >! go] :as async]
            [kti-web.utils :as utils :refer [js-alert]]
            [kti-web.components.utils :as components-utils]
            [kti-web.event-handlers :refer [gen-handler-vec] :as event-handlers]
            [kti-web.components.rtable :refer [rtable]]
            [kti-web.pagination :as pagination]
            [reagent.core :as r]))

(declare handler-wrapper-avoid-useless-fetching
         make-action-buttons
         article-id-action-button)

;; Helpers
(def empty-filter {:name nil :value nil})

(defn- remove-empty-filters [coll] (remove #{empty-filter} coll))

(defn- filter->hash-map [{:keys [name value]}] {name value})

(defn make-columns
  "Returns array of maps describing the table columns."
  [{:keys [on-show-article] :as props}]
  [{:header "Id"          :accessor :id            :width 50}
   {:header "Reference"   :accessor :reference     :width 400}
   {:header "Created At"  :accessor :created-at    :width 190}
   {:header "Article Id"  :accessor :article-id    :width 100
    :cell-fn #(article-id-action-button {:on-show-article on-show-article :article-id %})}
   {:header "Review Id"   :accessor :review-id     :width 100}
   {:header "Rev. status" :accessor :review-status :width 150}
   {:header "Actions"     :accessor identity
    :cell-fn #(make-action-buttons props %)}])

(defn refs->data
  "Converts refs into rtable data."
  [refs]
  (sort-by :captured-at refs))

(defn props->rtable-props
  "Extract the rtable props from the global props."
  [{{:keys [page pages pageSize]} :table :keys [refs fn-refresh!] :as props}]
  {:pre [(number? page) (number? pageSize) (seqable? refs) (fn? fn-refresh!)]}
  (let [columns (make-columns props)
        data (refs->data refs)]
    {:page page
     :pages pages
     :pageSize pageSize
     :columns columns
     :data data
     :on-fetch-data (-> fn-refresh! (handler-wrapper-avoid-useless-fetching props))
     :manual true}))

(defn calc-page-size [{:keys [total-items page-size]}]
  (.ceil js/Math (/ total-items page-size)))

(defn handler-wrapper-avoid-useless-fetching
  "Wraps a handler, and only calls it if the event :page or :pageSize has
  changed compared to the props :page and :pageSize"
  [handler {{props-page :page props-pageSize :pageSize} :table}]
  {:pre [(fn? handler) (number? props-page) (number? props-pageSize)]}
  (fn wrapped-handler [{event-page :page event-pageSize :pageSize :as event}]
    {:pre [(number? event-page) (number? event-pageSize)]}
    (when-not (= [props-page props-pageSize] [event-page event-pageSize])
      (handler event))))

;; Reducers


;; Handlers
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
                     remove-empty-filters
                     (map filter->hash-map)
                     (apply merge))}))

   (fn reduce-after [state _ _ {:keys [error? data]}]
     {:pre [(or error? (pagination/is-paginated? data))]}
     (as-> state it
       (assoc-in it [:table :loading] false)
       (if-not error?
         (-> it
             (assoc-in [:table :pages] (calc-page-size data))
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
    (swap! state update :filters conj empty-filter)))

(defn handle-show-article [state {:keys [on-show-article]}]
  {:pre [(or (nil? on-show-article) (fn? on-show-article))]}
  (fn [article-id]
    (on-show-article article-id)))

;; State
(def initial-state
  {    :refs nil
   :status {}
   :filters [{:name "" :value ""}]
   :table
   {:loading true
    :defaultPage 0
    :page 0
    :pageSize 10
    :pages nil
    :defaultPageSize 10
    :manual true}})

(defonce state
  (r/atom
   (assoc initial-state
          :onPageChange #(swap! state assoc-in [:table :page] %)
          :onPageSizeChange #(swap! state assoc-in [:table :pageSize] %))))

;; Components
(defn article-id-action-button
  "Returns an action button for an article id."
  [{:keys [article-id on-show-article]}]
  (when (and (not (nil? article-id)) (not= article-id ""))
    [:button {:on-click #(on-show-article article-id)} article-id]))

(defn delete-captured-ref-action-button
  "Returns a action button for deleting a captured ref"
  [{:keys [on-modal-display-for-deletion row-captured-ref]}]
  (let [on-click #(on-modal-display-for-deletion (:id row-captured-ref))
        props {:className "delete-button" :on-click on-click}]
    [:button props "D"]))

(defn make-action-buttons
  "Returns a div of action buttons"
  [{:keys [on-modal-display-for-deletion]} row]
  [:div
   [delete-captured-ref-action-button
    {:on-modal-display-for-deletion on-modal-display-for-deletion
     :row-captured-ref row}]])

(defn filter-picker-name-input
  [props]
  [components-utils/input (assoc props :width "250px")])

(defn filter-picker-value-input
  [props]
  [components-utils/input (assoc props :width "500px")])

(defn remove-filter-button
  [{:keys [on-remove-filter]}]
  [:button.remove-filter-button {:on-click #(on-remove-filter)} "-"])

(defn filter-picker
  "A picker for a filter (name and value)"
  [{{:keys [name value] :as filter} :filter
    :keys [on-filter-change on-remove-filter]
    :as props}]
  [:div
   [filter-picker-name-input
    {:value name
     :on-change #(on-filter-change (assoc filter :name %))}]
   [filter-picker-value-input
    {:value value
     :on-change #(on-filter-change (assoc filter :value %))}]
   [remove-filter-button props]])

(defn filters->filter-pickers [filters {:keys [on-filters-change]}]
  (let [indexed-filters (map vector filters (range))]
    (for [[filter i] indexed-filters
          :let [on-change #(-> filters (assoc i %) on-filters-change)
                on-remove #(-> filters (utils/dissoc-vec i) on-filters-change)
                props {:key i
                       :filter filter
                       :on-filter-change on-change
                       :on-remove-filter on-remove}]]
      [filter-picker props])))

(defn add-filter-picker-button
  "A button to add a filter."
  [{:keys [on-add-empty-filter]}]
  [:button.add-filter-picker-button {:on-click #(on-add-empty-filter)} "+"])

(defn filters-picker
  "Returns a component with the filters for the table."
  [{:keys [on-filters-change filters] :as props}]
  [:div.captured-reference-table_filters-picker
   (filters->filter-pickers filters props)
   [add-filter-picker-button props]])

(defn captured-refs-table-inner
  "Pure component for a table of captured references."
  [{{:keys [page pageSize]} :table :keys [fn-refresh!] :as props}]
  [:div
   [:h4 "Captured References Table"]
   [filters-picker props]
   [:button {:on-click #(fn-refresh! {:page page :pageSize pageSize})} "Update"]
   [rtable (props->rtable-props props)]
   [components-utils/errors-displayer props]])

(defn captured-refs-table
  [{:keys [on-modal-display-for-deletion] :as props}]
  (let [refresh! (handle-refresh! state props)
        on-filters-change (handle-filters-change state props)
        on-add-empty-filter (handle-add-empty-filter state props)
        on-show-article (handle-show-article state props)]
    (when (nil? (:refs @state))
      (refresh! (-> @state :table (select-keys [:page :pageSize]))))
    (fn []
      [captured-refs-table-inner
       (assoc @state
              :fn-refresh! refresh!
              :on-modal-display-for-deletion on-modal-display-for-deletion
              :on-filters-change on-filters-change
              :on-add-empty-filter on-add-empty-filter
              :on-show-article on-show-article)])))
