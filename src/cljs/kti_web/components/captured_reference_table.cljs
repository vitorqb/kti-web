(ns kti-web.components.captured-reference-table
  (:require [cljs.core.async :refer [<! >! go]]
            [kti-web.utils :as utils :refer [js-alert]]
            [kti-web.components.utils :as components-utils]
            [kti-web.event-handlers :refer [gen-handler]]
            [kti-web.components.rtable :refer [rtable]]
            [reagent.core :as r]))

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

(defn captured-refs-table-inner
  "Pure component for a table of captured references."
  [{:keys [loading? refs fn-refresh!] :as props}]
  (let [columns (make-columns props)
        data (->> refs (sort-by :created-at) reverse)]
    [:div
     [:h4 "Captured References Table"]
     [:button {:on-click #(fn-refresh!)} "Update"]
     (if loading?
       [:div "LOADING..."]
       [rtable {:columns columns :data data}])
     [components-utils/errors-displayer props]]))

(defonce state (r/atom {:loading? true :refs nil :status {}}))             

(def refresh
  {:r-before (fn [state {}] (assoc state :loading? true :refs nil :status {}))
   :action (fn [_ {:keys [get!]}] (get!))
   :r-after
   (fn [state {:keys [c-done]} {:keys [error? data]}]
     (and c-done (go (>! c-done 1)))
     (assoc state
            :loading? false
            :status (if error? {:errors data}  {:success-msg "Success!"})
            :refs (when-not error? data)))})

(defn captured-refs-table
  [{:keys [get! c-done on-modal-display-for-deletion] :as props}]
  (let [run-get! (gen-handler state props refresh)]
    (when (nil? (@state :refs)) (run-get!))
    (fn [] [captured-refs-table-inner
            (assoc @state
                   :fn-refresh! run-get!
                   :on-modal-display-for-deletion on-modal-display-for-deletion)])))
