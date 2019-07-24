(ns kti-web.components.captured-reference-table
  (:require [cljs.core.async :refer [<! >! go] :as async]
            [kti-web.components.captured-reference-table.helpers :as helpers]
            [kti-web.components.captured-reference-table.handlers :as handlers]
            [kti-web.utils :as utils :refer [js-alert]]
            [kti-web.components.utils :as components-utils]
            [kti-web.event-handlers :refer [gen-handler-vec] :as event-handlers]
            [kti-web.components.rtable :refer [rtable]]
            [kti-web.pagination :as pagination]
            [reagent.core :as r]))

(declare make-action-buttons
         article-id-action-button)

;; Helpers
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

(defn props->rtable-props
  "Extract the rtable props from the global props."
  [{{:keys [page pages pageSize]} :table :keys [refs fn-refresh!] :as props}]
  {:pre [(number? page) (number? pageSize) (seqable? refs) (fn? fn-refresh!)]}
  (let [columns (make-columns props)
        data (helpers/refs->data refs)]
    {:page page
     :pages pages
     :pageSize pageSize
     :columns columns
     :data data
     :on-fetch-data
     (-> fn-refresh! (helpers/handler-wrapper-avoid-useless-fetching props))
     :manual true}))

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
  (let [refresh! (handlers/handle-refresh! state props)
        on-filters-change (handlers/handle-filters-change state props)
        on-add-empty-filter (handlers/handle-add-empty-filter state props)
        on-show-article (handlers/handle-show-article state props)]
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
