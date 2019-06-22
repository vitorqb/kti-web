(ns kti-web.components.captured-reference-table
  (:require [cljs.core.async :refer [<! >! go] :as async]
            [kti-web.utils :as utils :refer [js-alert]]
            [kti-web.components.utils :as components-utils]
            [kti-web.event-handlers :refer [gen-handler-vec]]
            [kti-web.components.rtable :refer [rtable]]
            [kti-web.pagination :as pagination]
            [reagent.core :as r]))

(declare handler-wrapper-avoid-useless-fetching)

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

(defn make-columns
  "Returns array of maps describing the table columns."
  [props]
  [{:header "Id"          :accessor :id            :width 50}
   {:header "Reference"   :accessor :reference     :width 400}
   {:header "Created At"  :accessor :created-at    :width 190}
   {:header "Article Id"  :accessor :article-id    :width 100}
   {:header "Review Id"   :accessor :review-id     :width 100}
   {:header "Rev. status" :accessor :review-status :width 150}
   {:header "Actions"     :accessor identity
    :cell-fn #(make-action-buttons props %)}])

(defn refs->data
  "Converts refs into rtable data."
  [refs]
  (->> refs (sort-by :captured-at) reverse))

(defn props->rtable-props
  "Extract the rtable props from the global props."
  [{{:keys [page pages pageSize]} :table-state :keys [refs fn-refresh!] :as props}]
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

(defn captured-refs-table-inner
  "Pure component for a table of captured references."
  [{:keys [fn-refresh!] :as props}]
  [:div
   [:h4 "Captured References Table"]
   [:button {:on-click #(fn-refresh!)} "Update"]
   [rtable (props->rtable-props props)]
   [components-utils/errors-displayer props]])

(def initial-state
  {    :refs nil
   :status {}
   :table-state
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
          :onPageChange #(swap! state assoc-in [:table-state :page] %)
          :onPageSizeChange #(swap! state assoc-in [:table-state :pageSize] %))))

(def refresh-paginated-vec
  [(fn reduce-before [state _ {:keys [page pageSize]}]
     (-> state
         (assoc :refs nil :status nil)
         (assoc-in [:table-state :loading] true)
         (assoc-in [:table-state :page] page)
         (assoc-in [:table-state :pageSize] pageSize)))

   (fn do-action
     [_ {:keys [get-paginated-captured-references!]} {:keys [page pageSize]}]
     {:pre [(number? page)
            (number? pageSize)
            (fn? get-paginated-captured-references!)]}
     (get-paginated-captured-references! {:page (inc page) :page-size pageSize}))

   (fn reduce-after [state _ _ {:keys [error? data]}]
     {:pre [(or error? (pagination/is-paginated? data))]}
     (as-> state it
       (assoc-in it [:table-state :loading] false)
       (if-not error?
         (let [{:keys [page-size total-items items]} data
               pages (.ceil js/Math (/ total-items page-size))]
           (-> it
               (assoc-in [:table-state :pages] pages)
               (assoc :refs items)))
         (assoc it :status {:errors data}))))])

(defn handler-wrapper-avoid-useless-fetching
  "Wraps a handler, and only calls it if the event :page or :pageSize has
  changed compared to the props :page and :pageSize"
  [handler {{props-page :page props-pageSize :pageSize} :table-state}]
  {:pre [(fn? handler) (number? props-page) (number? props-pageSize)]}
  (fn wrapped-handler [{event-page :page event-pageSize :pageSize :as event}]
    {:pre [(number? event-page) (number? event-pageSize)]}
    (when-not (= [props-page props-pageSize] [event-page event-pageSize])
      (handler event))))

(defn captured-refs-table
  [{:keys [on-modal-display-for-deletion] :as props}]
  (let [run-get! (gen-handler-vec state props refresh-paginated-vec)]
    (when (nil? (:refs @state))
      (run-get! (-> @state :table-state (select-keys [:page :pageSize]))))
    (fn [] [captured-refs-table-inner
            (assoc @state
                   :fn-refresh! run-get!
                   :on-modal-display-for-deletion on-modal-display-for-deletion)])))
