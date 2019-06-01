(ns kti-web.components.captured-reference-table
  (:require [cljs.core.async :refer [<! >! go]]
            [kti-web.utils :as utils :refer [js-alert]]
            [kti-web.components.utils :as components-utils]
            [kti-web.event-handlers :refer [gen-handler]]
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
  [{:head-text "Id"          :fn-get :id}
   {:head-text "Reference"   :fn-get :reference}
   {:head-text "Created At"  :fn-get :created-at}
   {:head-text "Classified?" :fn-get (comp str :classified)}
   {:head-text "Article Id"  :fn-get :article-id}
   {:head-text "Review Id"   :fn-get :review-id}
   {:head-text "Rev. status" :fn-get :review-status}
   {:head-text "Actions"     :fn-get #(make-action-buttons props %)}])

(defn- make-thead
  "Prepares a thead for the table, given an array of columns."
  [cols]
  (->> cols
       (map (fn [{:keys [head-text]}] [:th {:key head-text} head-text]))
       (apply vector :tr)
       (vector :thead)))

(defn- make-tbody
  "prepares a tbody for the table, given an array of columns and rows."
  [cols rows]
  (->> rows
       (map (fn [row]
              (->> cols
                   (map (fn [{:keys [fn-get]}] [:td (fn-get row)]))
                   (apply vector :tr))))
       (apply vector :tbody)))

(defn captured-refs-table-inner
  "Pure component for a table of captured references."
  [{:keys [loading? refs fn-refresh!] :as props}]
  (let [columns (make-columns props)]
    [:div
     [:h4 "Captured References Table"]
     [:button {:on-click #(fn-refresh!)} "Update"]
     (if loading?
       [:div "LOADING..."]
       [:table
        (make-thead columns)
        (->> refs (sort-by :created-at) reverse (make-tbody columns))])
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
