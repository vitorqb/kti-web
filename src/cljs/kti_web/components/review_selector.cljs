(ns kti-web.components.review-selector
  (:require [kti-web.utils :as utils]
            [kti-web.components.utils :as components-utils :refer [input]]))

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
