(ns kti-web.components.review-editor
  (:require
   [reagent.core :as r]
   [cljs.core.async :refer [<! >!]]
   [kti-web.models.reviews :as reviews-models]
   [kti-web.utils :as utils :refer [join-vecs]]
   [kti-web.utilsc :refer-macros [go-with-done-chan]]
   [kti-web.components.utils :as components-utils :refer [input]]
   [kti-web.components.review-selector :refer [review-selector]]
   [kti-web.event-handlers :refer [gen-handler]]))

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

(defn review-editor--inner
  "Pure component for editing review"
  [props]
  [:div
   [:h4 "Edit Review"]
   [review-selector props]
   (when (:edited-review props) [review-editor-form props])
   [components-utils/errors-displayer props]
   [components-utils/success-message-displayer props]])

(def edited-review-submit
  {:r-before #(assoc % :loading? true :status {})
   :action
   (fn [{:keys [selected-review-id edited-review]} {:keys [put-review!]}]
     (-> edited-review
         (dissoc :id)
         reviews-models/raw-spec->spec
         (->> (put-review! selected-review-id))))
   :r-after
   (fn [{:keys [edited-review] :as state} _ {:keys [error? data] :as response}]
     (assoc state
            :loading? false
            :status (if error? {:errors data} {:success-msg "SUCCESS"})
            :edited-review (if error?
                             edited-review
                             (reviews-models/review->raw-spec data))))})

(def review-selection-submit
  {:r-before
   #(assoc % :loading? true :edited-review nil :status {} :selection-status {})
   :action
   (fn [{:keys [selected-review-id]} {:keys [get-review!]}]
     (get-review! selected-review-id))
   :r-after
   (fn [state _ {:keys [error? data]}]
     (assoc state
            :loading? false
            :selection-status (if error? {:errors data} {:success-msg "SUCCESS"})
            :edited-review (when-not error?
                             (reviews-models/review->raw-spec data))))})

(defn review-editor
  [{:keys [get-review! put-review!] :as props}]
  (let [state (r/atom {:status {} :selection-status {} :selected-review-id nil
                       :edited-review nil :loading? false})]
    (fn []
      [review-editor--inner
       (assoc
        @state
        :on-selected-review-id-change
        #(swap! state assoc :selected-review-id % :edited-review nil :status nil)
        :on-review-selection-submit
        (gen-handler state props review-selection-submit)
        :on-edited-review-change #(swap! state assoc :edited-review %)
        :on-edited-review-submit
        (gen-handler state props edited-review-submit))])))
