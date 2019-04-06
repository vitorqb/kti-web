(ns kti-web.components.edit-captured-reference-component
  (:require
   [cljs.core.async :refer [go <!]]
   [reagent.core :as r]
   [kti-web.components.utils :refer [submit-button call-prevent-default]]
   [kti-web.components.select-captured-ref :refer [select-captured-ref]]
   [kti-web.utils :refer [call-prevent-default call-with-val]]
   [kti-web.http :as http]))

(defn captured-ref-inputs--id [{:keys [id]}]
  [:div [:span "Id"] [:input {:value (or id "") :disabled true}]])

(defn captured-ref-inputs--created-at [{:keys [created-at]}]
  [:div
   [:span "Captured at"]
   [:input {:value (or created-at "") :disabled true}]])

(defn captured-ref-inputs--reference [{:keys [reference on-reference-change]}]
  [:div 
   [:span "Reference"]
   [:input {:value (or reference "")
            :on-change (call-with-val on-reference-change)}]])

(defn captured-ref-inputs [{:keys [value on-change]}]
  [:div
   [captured-ref-inputs--id value]
   [captured-ref-inputs--created-at value]
   [captured-ref-inputs--reference
    {:reference (:reference value)
     :on-reference-change #(on-change (assoc value :reference %))}]])

(defn edit-captured-ref-form
  [{:keys [cap-ref on-cap-ref-change on-submit]}]
  "A form to edit a captured reference"
  [:form {:on-submit (call-prevent-default #(on-submit))}
   [captured-ref-inputs {:value cap-ref :on-change on-cap-ref-change}]
   [submit-button]])

(defn edit-captured-ref-comp--inner
  [{:keys [get-captured-ref on-cap-ref-selection cap-ref-id-value
           on-cap-ref-id-change editted-cap-ref on-editted-cap-ref-change
           on-edit-cap-ref-submit status]}]
  [:div
   [:h3 "Edit Captured Reference Form"]
   [select-captured-ref
    {:get-captured-ref get-captured-ref
     :on-selection on-cap-ref-selection
     :id-value cap-ref-id-value
     :on-id-change on-cap-ref-id-change}]
   (when editted-cap-ref
     [edit-captured-ref-form
      {:cap-ref editted-cap-ref
       :on-cap-ref-change on-editted-cap-ref-change
       :on-submit on-edit-cap-ref-submit}])
   [:div status]])

(defn edit-captured-ref-comp [{:keys [hput!]}]
  "A form to edit a captured reference."
  ;; !!!! TODO -> Change get-captured-ref for hget!
  (let [selected-id-value (r/atom nil)
        editted-cap-ref (r/atom nil)
        status (r/atom nil)
        handle-submit
        (fn []
          (let [resp-chan (hput! @selected-id-value @editted-cap-ref)]
            (go (let [{:keys [error]} (<! resp-chan)]
                  (reset! status (if error "Error!" "Success!"))))))]
    (fn []
      [edit-captured-ref-comp--inner
       {:get-captured-ref http/get-captured-reference!
        :on-cap-ref-selection #(reset! editted-cap-ref %)
        :cap-ref-id-value @selected-id-value
        :on-cap-ref-id-change #(reset! selected-id-value %)
        :editted-cap-ref @editted-cap-ref
        :on-editted-cap-ref-change #(reset! editted-cap-ref %)
        :on-edit-cap-ref-submit handle-submit
        :status @status}])))
