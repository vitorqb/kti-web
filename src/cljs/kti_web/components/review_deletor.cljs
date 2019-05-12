(ns kti-web.components.review-deletor
  (:require
   [reagent.core :as r]
   [cljs.core.async :as async :refer [<! >!]]
   [kti-web.components.review-selector :refer [review-selector]]
   [kti-web.components.utils :as components-utils :refer [input]]
   [kti-web.utilsc :refer-macros [go-with-done-chan]]
   [kti-web.models.reviews :as models-reviews]
   [kti-web.event-handlers :refer [gen-handler]]))

(def inputs (assoc models-reviews/inputs :id [input {:text "Id"}]))
(def inputs-keys [:id :id-article :feedback-text :status])

(defn review-displayer
  "Displays a review (before deletion)"
  [{:keys [selected-review]}]
  (into
   [:div {}]
   (for [k inputs-keys :let [[comp props] (get inputs k)]]
     [comp (assoc props :disabled true :value (get selected-review k))])))

(defn delete-button
  "A button to actually delete a review."
  [{:keys [on-delete-review-submit]}]
  [:button {:on-click (fn [& _] (on-delete-review-submit))} "DELETE!"])

(defn review-deletor-inner
  "Pure component for deleting a review"
  [{:keys [selected-review] :as props}]
  [:div {}
   [:h3 "Delete Review"]
   [review-selector props]
   (when selected-review [review-displayer props])
   (when selected-review [delete-button props])
   [components-utils/success-message-displayer props]
   [components-utils/errors-displayer props]])

(def review-selection-submit
  {:r-before
   #(assoc % :loading? true :status {} :selection-status {} :selected-review nil)
   :action
   (fn [{:keys [selected-review-id]} {:keys [get-review!]}]
     (get-review! selected-review-id))
   :r-after
   (fn [state _ {:keys [error? data]}]
     (assoc state
         :loading? false
         :status {}
         :selection-status (if error? {:errors data} {:success-msg "Success"})
         :selected-review (when-not error? data)))})

(def delete-review-submit
  {:r-before #(assoc % :loading? true :status {})
   :action
   (fn [{:keys [selected-review]} {:keys [delete-review!]}]
     (delete-review! (:id selected-review)))
   :r-after
   (fn [{:keys [selected-review deleted-reviews :as state]} _ {:keys [error? data]}]
     (assoc state
            :loading? false
            :status (if error? {:errors data} {:success-msg "Deleted!"})
            :selected-review (when error? selected-review)
            :deleted-reviews (if error?
                               deleted-reviews
                               (conj deleted-reviews selected-review))))})

(defn review-deletor
  [{:keys [get-review! delete-review!] :as props}]
  (let [state (r/atom {:status {} :selection-status {} :deleted-reviews []
                       :selected-review nil :selected-review-id nil
                       :loading? false})]
    (fn []
      [review-deletor-inner
       (assoc
        @state
        :on-selected-review-id-change #(swap! state assoc :selected-review-id %)
        :on-delete-review-submit
        (gen-handler state props delete-review-submit)
        :on-review-selection-submit
        (gen-handler state props review-selection-submit))])))
