(ns kti-web.components.edit-captured-reference-component
  (:require
   [cljs.core.async :refer [go <! >! timeout]]
   [reagent.core :as r]
   [kti-web.components.utils
    :refer [submit-button call-prevent-default input]
    :as components-utils]
   [kti-web.components.select-captured-ref :refer [select-captured-ref]]
   [kti-web.utils :refer [call-prevent-default call-with-val to-str]]
   [kti-web.utilsc :refer-macros [go-with-done-chan]]))

(def inputs
  {:id         [input {:text "Id" :disabled true :type "number"}]
   :created-at [input {:text "Created at" :disabled true}]
   :reference  [input {:text "Reference"}]})

(defn captured-ref-inputs [{:keys [value on-change]}]
  (letfn [(make-input [k]
            (let [[comp props] (get inputs k)]
              [comp (assoc props
                           :value (get value k)
                           :on-change #(on-change (assoc value k %)))]))]
    [:div
     (make-input :id)
     (make-input :created-at)
     (make-input :reference)]))

(defn edit-captured-ref-form
  [{:keys [cap-ref on-cap-ref-change on-submit]}]
  "A form to edit a captured reference"
  [:form {:on-submit (call-prevent-default #(on-submit))}
   [captured-ref-inputs {:value cap-ref :on-change on-cap-ref-change}]
   [submit-button]])

(defn edit-captured-ref-comp--inner
  [{:keys [get-captured-ref on-cap-ref-selection selected-cap-ref-id-value
           on-cap-ref-id-change editted-cap-ref on-editted-cap-ref-change
           on-edit-cap-ref-submit loading? toggle-loading]
    :as props}]
  [:div
   [:h4 "Edit Captured Reference Form"]
   [select-captured-ref
    {:get-captured-ref get-captured-ref
     :on-selection on-cap-ref-selection
     :id-value selected-cap-ref-id-value
     :on-id-change on-cap-ref-id-change
     :toggle-loading toggle-loading}]
   (cond
     loading?
     [:span "Loading..."]
     (get-in props [:status :select-cap-ref :errors])
     [components-utils/errors-displayer
      {:status (get-in props [:status :select-cap-ref])}]
     editted-cap-ref
     [:div
      [edit-captured-ref-form
       {:cap-ref editted-cap-ref
        :on-cap-ref-change on-editted-cap-ref-change
        :on-submit on-edit-cap-ref-submit}]
      [components-utils/errors-displayer
       {:status (get-in props [:status :edit-cap-ref])}]
      [components-utils/success-message-displayer
       {:status (get-in props [:status :edit-cap-ref])}]])])

(defn edit-captured-ref-comp [{:keys [hput! hget!]}]
  "A form to edit a captured reference."
  (let [state (r/atom {:selected-cap-ref-id-value nil
                       :editted-cap-ref nil
                       :loading? false
                       :status {:edit-cap-ref {} :select-cap-ref {}}})
        handle-submit
        (fn []
          (swap! state assoc
                 :loading? true
                 :status {:edit-cap-ref {} :select-cap-ref {}})
          (let [{:keys [selected-cap-ref-id-value editted-cap-ref]} @state
                resp-chan (hput! selected-cap-ref-id-value editted-cap-ref)]
            (go-with-done-chan
             (let [{:keys [error? data]} (<! resp-chan)]
               (swap! state assoc
                      :loading? false
                      :status {:edit-cap-ref (if error?
                                               {:errors data}
                                               {:success-msg "Success!"})})))))
        handle-cap-ref-selection
        (fn [{:keys [error? data]}]
          (swap! state assoc
                 :status {:select-cap-ref (and error? {:errors data})}
                 :editted-cap-ref (or error? data)))]
    (fn []
      [edit-captured-ref-comp--inner
       (assoc @state
              :get-captured-ref hget!
              :on-cap-ref-selection handle-cap-ref-selection
              :on-cap-ref-id-change #(swap! state assoc :selected-cap-ref-id-value %)
              :on-editted-cap-ref-change #(swap! state assoc :editted-cap-ref %)
              :on-edit-cap-ref-submit handle-submit
              :toggle-loading #(swap! state assoc :loading? %))])))
