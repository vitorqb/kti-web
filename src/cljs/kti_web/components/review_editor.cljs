(ns kti-web.components.review-editor
  (:require
   [kti-web.models.reviews :as reviews-models]
   [kti-web.utils :as utils]
   [kti-web.components.utils :as components-utils :refer [input]]))

(def inputs
  (-> reviews-models/inputs
      (assoc :id [input {:disabled true :text "Id"}])))

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

(defn review-editor
  [specs]
  [:span "NOT IMPLEMENTED"])
