(ns kti-web.components.modal-wrapper)

(defn active?->display
  "Transforms the active? prop of the modal into the display css value"
  [active?]
  (if active? "block" "none"))

(defn modal-wrapper
  "A modal wrapper that displays it's children in a modal."
  [{:keys [active?]} children]
  (let [display (active?->display active?)
        style {:display display}]
    [:div {:className "modal-wrapper-div" :style style}
     [:div {:className "modal-wrapper-content-div"}
      children]]))
