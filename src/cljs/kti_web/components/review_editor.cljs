(ns kti-web.components.review-editor
  (:require
   [reagent.core :as r]
   [cljs.core.async :refer [<! >!]]
   [kti-web.models.reviews :as reviews-models]
   [kti-web.utils :as utils :refer [join-vecs]]
   [kti-web.utilsc :refer-macros [go-with-done-chan]]
   [kti-web.components.utils :as components-utils :refer [input]]))

(def inputs (assoc reviews-models/inputs :id [input {:disabled true :text "Id"}]))

(defn review-editor-form
  "A form to edit reviews"
  [{:keys [edited-review on-edited-review-change on-edited-review-submit loading?]}]
  (letfn [(on-change [k v] (on-edited-review-change (assoc edited-review k v)))]
    (join-vecs
     [:form {:on-submit (utils/call-prevent-default #(on-edited-review-submit))}]
     (for [k [:id :id-article :feedback-text :status]
           :let [[comp props] (get inputs k)
                 value (get edited-review k)
                 new-props {:key k :value value :on-change #(on-change k %)}]]
       [comp (merge props new-props (when loading? {:disabled true}))])
     [[components-utils/submit-button]])))

(defn review-selector
  "Selector for a review"
  [{:keys [on-review-selection-submit selected-review-id
           on-selected-review-id-change selection-status]}]
  [:form {:on-submit (utils/call-prevent-default #(on-review-selection-submit))}
   [input {:text "Review Id: "
           :type "number"
           :value selected-review-id
           :on-change on-selected-review-id-change
           :width 120}]
   [components-utils/submit-button]
   [components-utils/errors-displayer {:status selection-status}]])

(defn reduce-on-selection-submit
  "Handles when an user submits a selection"
  [state {:keys [error? data] :as response}]
  (assoc state
         :loading? false
         :selection-status (if error? {:errors data} {:success-msg "SUCCESS"})
         :edited-review (when-not error? (-> data
                                             ;; !!!! TODO This should happen at req level
                                             ;; https://trello.com/c/oSHUa1xU
                                             reviews-models/server-resp->review
                                             reviews-models/review->raw-spec))))

(defn reduce-on-edited-review-submit
  "Handles when an user submits an edited review"
  [{:keys [edited-review] :as state} {:keys [error? data] :as response}]
  (assoc state
         :loading? false
         :status (if error? {:errors data} {:success-msg "SUCCESS"})
         :edited-review (if error?
                          edited-review
                          (-> data
                              ;; !!!! TODO This should happen at req level
                              ;; https://trello.com/c/oSHUa1xU
                              reviews-models/server-resp->review
                              reviews-models/review->raw-spec))))

(defn review-editor--inner
  "Pure component for editing review"
  [props]
  [:div
   [:h3 "Edit Review"]
   [review-selector props]
   (when (:edited-review props) [review-editor-form props])
   [components-utils/errors-displayer props]
   [components-utils/success-message-displayer props]])

(defn review-editor
  [{:keys [get-review! put-review!]}]
  (let [state (r/atom {:status {}
                       :selection-status {}
                       :selected-review-id nil
                       :edited-review nil
                       :loading? false})
        handle-review-selection-submit
        (fn []
          (swap! state assoc :loading? true :edited-review nil :selection-status {})
          (let [get-chan (get-review! (:selected-review-id @state))]
            (go-with-done-chan
             (swap! state reduce-on-selection-submit (<! get-chan)))))
        handle-edited-review-submit
        (fn []
          (swap! state assoc :loading? true :status {})
          (let [{:keys [selected-review-id edited-review]} @state
                put-chan (put-review!
                          selected-review-id
                          (-> edited-review
                              (dissoc :id)
                              reviews-models/raw-spec->spec))]
            (go-with-done-chan
             (swap! state reduce-on-edited-review-submit (<! put-chan)))))]
    (fn []
      [review-editor--inner
       (assoc @state
              :on-selected-review-id-change #(swap! state assoc :selected-review-id %)
              :on-review-selection-submit handle-review-selection-submit
              :on-edited-review-change #(swap! state assoc :edited-review %)
              :on-edited-review-submit handle-edited-review-submit)])))
