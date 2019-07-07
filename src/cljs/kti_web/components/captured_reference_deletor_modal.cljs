(ns kti-web.components.captured-reference-deletor-modal
  (:require
   [reagent.core :as r]
   [cljs.core.async :as async]
   [kti-web.components.modal-wrapper :refer [modal-wrapper]]
   [kti-web.event-handlers :refer [handle!-vec]]
   [kti-web.components.confirmation-box :refer [confirmation-box]]
   [kti-web.event-listeners :as event-listeners]))

;; Helpers
(def title "Delete Captured Reference")
(defn confirmation-text [id] (str "Delete Captured Reference with id " id "?"))
(defn props->modal-props
  [{:keys [active?]}]
  {:active? active?})
(defn props->confirmation-box-props
  [{:keys [delete-captured-ref-id
           status
           loading?
           on-confirm-deletion
           on-abortion]}]
  {:status status
   :loading? loading?
   :on-confirmation on-confirm-deletion
   :on-abortion on-abortion
   :title title
   :text (confirmation-text delete-captured-ref-id)})

;; Reductors
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

;; Handlers
(def handler-fns-on-confirm-deletion
  "Handler. Confirms and requests the deletion of a captured reference."
  [(fn before [state _ _] (assoc state :loading? true :status {}))
   (fn action [{:keys [delete-captured-ref-id]} {:keys [delete-captured-reference!]} _]
     (delete-captured-reference! delete-captured-ref-id))
   (fn after [state _ _ {:keys [error? data]}]
     (let [status  (if error? {:errors data} {:success-msg "Success!"})
           active? (if error? (:active? state) false)]
       (assoc state :loading? false :status status :active? active?)))])

(defn handle-on-abortion [state props]
  (fn []
    (swap! state reduce-on-abortion nil nil)))

(defn handle-on-modal-display-for-deletion [state props]
  (event-listeners/as-public
   (fn [event]
     (swap! state reduce-on-modal-display-for-deletion nil event))))

(defn handle-on-confirm-deletion [state props]
  (fn [] 
    (handle!-vec nil state props handler-fns-on-confirm-deletion)))

;; State
(defonce state (r/atom {:active? false :delete-captured-ref-id nil}))

;; Components
(defn captured-reference-deletor-modal--inner
  [props]
  (let [modal-props (props->modal-props props)
        confirmation-box-props (props->confirmation-box-props props)]
    [modal-wrapper modal-props [confirmation-box confirmation-box-props]]))

(defn captured-reference-deletor-modal
  [{:keys [events-chan] :as props}]
  (let [handlers
        {:on-confirm-deletion
         (handle-on-confirm-deletion state props)
         :on-abortion
         (handle-on-abortion state props)
         :on-modal-display-for-deletion
         (handle-on-modal-display-for-deletion state props)}]
    (when events-chan
      (event-listeners/listen! events-chan handlers))
    (fn []
      [captured-reference-deletor-modal--inner
       (merge @state props handlers)])))
