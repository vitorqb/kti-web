(ns kti-web.components.utils)

(defn submit-button
  ([] (submit-button {:text "Submit!"}))
  ([{:keys [text]}] [:button {:type "Submit"} text]))
