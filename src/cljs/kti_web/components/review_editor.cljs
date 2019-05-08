(ns kti-web.components.review-editor
  (:require
   [kti-web.models.reviews :as reviews-models]
   [kti-web.utils :as utils]
   [kti-web.components.utils :as components-utils :refer [input]]))

(def inputs
  (-> reviews-models/inputs
      (assoc :id [input {:disabled true :text "Id"}])))

;; !!!! TODO -> Disable all during loading
(defn review-editor-form
  "A form to edit reviews"
  [{:keys [edited-review on-edited-review-change on-edited-article-submit]}]
  (letfn [(on-change [k v] (on-edited-review-change (assoc edited-review k v)))
          (make-input [k]
            (let [[comp props] (get inputs k)]
              [comp (assoc props
                           :key k
                           :value (get edited-review k)
                           :on-change #(on-change k %))]))]
    (into
     [:form {:on-submit (utils/call-prevent-default #(on-edited-article-submit))}]
     (doall (map make-input [:id :id-article :feedback-text :status])))))

(defn review-selector
  "Selector for a review"
  [{:keys [on-review-selection-submit selected-review-id
           on-selected-review-id-change selection-status]}]
  [:form {:on-submit (utils/call-prevent-default #(on-review-selection-submit))}
   [input {:text "Id"
           :type "number"
           :value selected-review-id
           :on-change on-selected-review-id-change}]
   [components-utils/errors-displayer {:status selection-status}]])

(defn review-editor--inner
  "Pure component for editing review"
  [props]
  [:div
   [review-selector props]
   (when (:edited-review props) [review-editor-form props])
   [components-utils/errors-displayer props]
   [components-utils/success-message-displayer props]])

(defn review-editor
  [specs]
  [:span "NOT IMPLEMENTED"])
