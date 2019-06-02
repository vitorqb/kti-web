(ns kti-web.components.captured-reference-deletor-modal
  (:require
   [kti-web.components.modal-wrapper :refer [modal-wrapper]]
   [kti-web.components.confirmation-box :refer [confirmation-box]]))

;; Helpers
(def title "Delete Captured Reference")
(defn confirmation-text [id] (str "Delete Captured Reference with id " id "?"))

;; Reductors and handlers
(defn reduce-on-modal-display-for-deletion
  "Reducer. Displays the modal for deleting a captured reference."
  [state _ delete-captured-ref-id]
  (assoc state
         :active? true
         :delete-captured-ref-id delete-captured-ref-id
         :status {}))

(defn reduce-on-abortion
  "Reducer. Aborts the display of the modal without any action."
  [state & _]
  (assoc state :active? false))

(def handler-fns-on-confirm-deletion
  "Handler. Confirms and requests the deletion of a captured reference."
  [(fn before [state _ _] (assoc state :loading? true :status {}))
   (fn action [{:keys [delete-captured-ref-id]} {:keys [delete-captured-reference!]} _]
     (delete-captured-reference! delete-captured-ref-id))
   (fn after [state _ _ {:keys [error? data]}]
     (let [status  (if error? {:errors data} {:success-msg "Success!"})
           active? (if error? (:active? state) false)]
       (assoc state :loading? false :status status :active? active?)))])

;; Components
(defn captured-reference-deletor-modal
  [{:keys [active? delete-captured-ref-id on-abortion on-confirm-deletion loading?
           status]}]
  (let [text (confirmation-text delete-captured-ref-id)
        modal-props {:active? active?}
        confirmation-box-props {:status status
                                :loading? loading?
                                :on-confirmation on-confirm-deletion
                                :on-abortion on-abortion
                                :title title
                                :text text}]
    [modal-wrapper modal-props [confirmation-box confirmation-box-props]]))
