(ns kti-web.components.capture-form
  (:require
   [cljs.core.async :refer [go <! >!]]
   [reagent.core :as r]
   [kti-web.utils :as utils]
   [kti-web.components.utils :as components-utils]
   [kti-web.event-handlers :refer [handle!-vec]]))

;; Helpers
(defn response->result [{:keys [error? data]}]
  (if error?
    "Error!"
    (str "Created with id " (data :id) " and ref " (data :reference))))

;; Reducers

;; Handlers
(def on-submit-handle-fns
  [(fn before [state _ _]
     (assoc state :loading? true :result nil))

   (fn action [{:keys [value]} {:keys [post!]} _]
     (post! value))

   (fn after [state _ _ http-resp]
     (assoc state :loading? false :result (response->result http-resp)))])

(defn handle-on-submit [state props]
  (fn []
    (handle!-vec nil state props on-submit-handle-fns)))

;; State
(def state (r/atom {:value nil :loading? false :result nil}))

;; Components
(defn capture-input [{:keys [on-change value]}]
  [:div
   [:span "Capture: "]
   [:input {:type "text" :value value
            :on-change (utils/call-with-val on-change)
            :style {:width "60%" :min-width "10cm"}}]
   [:div [:i "(current value: " value ")"]]])

(defn capture-form-inner [{:keys [loading? value result on-submit on-change]}]
  [:div
   [:h4 "Capture Form"]
   [:form
    {:on-submit (utils/call-prevent-default on-submit)}
    [capture-input {:value value :on-change on-change}]
    [components-utils/submit-button {:text "Capture!"}]
    [:div result]
    (if loading? [:div "Loading..."])]])

(defn capture-form [props]
  (let [on-submit (handle-on-submit state props)]
    (fn []
      (-> @state
          (assoc :on-submit on-submit
                 :on-change #(swap! state assoc :value %))
          capture-form-inner))))
