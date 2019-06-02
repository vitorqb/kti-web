(ns kti-web.components.confirmation-box
  (:require
   [kti-web.components.utils :as comp-utils]))

(defn confirmation-box
  "A confirmation (yes/no) modal."
  [{:keys [title text on-confirmation on-abortion loading? status]}]
  (let [common-props {:disabled loading?}
        assoc-props  #(apply assoc common-props %&)]
    [:div {}
     [:h3 title]
     [:div text]
     [:button (assoc-props :on-click #(on-confirmation)) "Yes"]
     [:button (assoc-props :on-click #(on-abortion)) "No"]
     [comp-utils/errors-displayer {:status status}]]))
