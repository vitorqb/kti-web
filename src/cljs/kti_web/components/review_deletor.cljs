(ns kti-web.components.review-deletor
  (:require
   [reagent.core :as r]
   [cljs.core.async :as async :refer [<! >!]]
   [kti-web.components.review-selector :refer [review-selector]]
   [kti-web.components.utils :as components-utils :refer [input]]
   [kti-web.utilsc :refer-macros [go-with-done-chan]]
   [kti-web.models.reviews :as models-reviews]))

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

(defn reduce-before-delete-review-submit [state]
  (assoc state :loading? true :status {}))

(defn reduce-on-delete-review-submit
  [{:keys [selected-review deleted-reviews :as state]} {:keys [error? data]}]
  (-> state
      (assoc :loading? false
             :status (if error? {:errors data} {:success-msg "Deleted!"})
             :selected-review (when error? selected-review)
             :deleted-reviews (if error?
                                deleted-reviews
                                (conj deleted-reviews selected-review)))))

(defn handle-on-delete-review-submit [{:keys [selected-review]} delete-review!]
  (delete-review! (:id selected-review)))

(defn reduce-before-review-selection-submit [state]
  (assoc state :loading? true :status {} :selection-status {} :selected-review nil))

(defn reduce-on-review-selection-submit [state {:keys [error? data]}]
  (assoc state
         :loading? false
         :status {}
         :selection-status (if error? {:errors data} {:success-msg "Success"})
         :selected-review (when-not error? data)))

(defn handle-on-review-selection-submit [{:keys [selected-review-id]} get-review!]
  (get-review! selected-review-id))

(defn review-deletor
  [{:keys [get-review! delete-review!]}]
  (let [state (r/atom {:status {} :selection-status {} :deleted-reviews []
                       :selected-review nil :selected-review-id nil
                       :loading? false})]
    (fn []
      [review-deletor-inner
       (assoc
        @state
        :on-selected-review-id-change #(swap! state assoc :selected-review-id %)
        :on-delete-review-submit
        (fn []
          (let [ctx-chan (handle-on-delete-review-submit @state delete-review!)]
            (swap! state reduce-before-delete-review-submit)
            (go-with-done-chan
             (swap! state reduce-on-delete-review-submit (<! ctx-chan)))))
        :on-review-selection-submit
        (fn []
          (let [ctx-chan (handle-on-review-selection-submit @state get-review!)]
            (swap! state reduce-before-review-selection-submit)
            (go-with-done-chan
             (swap! state reduce-on-review-selection-submit (<! ctx-chan))))))])))
