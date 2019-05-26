(ns kti-web.components.confirmation-box)

(defn confirmation-box
  "A confirmation (yes/no) modal."
  [{:keys [title text on-confirmation on-abortion]}]
  [:div {}
   [:h3 title]
   [:div text]
   [:button {:on-click #(on-confirmation)} "Yes"]
   [:button {:on-click #(on-abortion)} "No"]])
