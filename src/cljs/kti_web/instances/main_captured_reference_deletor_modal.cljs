(ns kti-web.instances.main-captured-reference-deletor-modal
  (:require
   [reagent.core :as r]
   [kti-web.event-handlers :refer [gen-handler-vec]]
   [kti-web.components.captured-reference-deletor-modal
    :as captured-reference-deletor-modal
    :refer [reduce-on-abortion
            reduce-on-modal-display-for-deletion
            handler-fns-on-confirm-deletion]]
   [kti-web.http :refer [delete-captured-reference!]]))

(defonce state (r/atom {:active? false :delete-captured-ref-id nil}))

(def handlers
  {:on-abortion
   (fn on-abortion []
     (swap! state reduce-on-abortion nil nil))

   :on-modal-display-for-deletion
   (fn on-modal-display-for-deletion [event]
     (swap! state reduce-on-modal-display-for-deletion nil event))

   :on-confirm-deletion
   (gen-handler-vec
    state
    {:delete-captured-reference! delete-captured-reference!}
    handler-fns-on-confirm-deletion)})

(defn instance []
  (let [props (merge @state handlers)]
    [captured-reference-deletor-modal/captured-reference-deletor-modal props]))
