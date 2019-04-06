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

(defn edit-captured-ref-comp--form
  [{:keys [editted-cap-ref on-editted-cap-ref-change on-submit status]}]
  [:div {:hidden (nil? editted-cap-ref)}
   [:form {:on-submit (call-prevent-default on-submit)}
    [captured-ref-inputs {:value editted-cap-ref
                          :on-change on-editted-cap-ref-change}]
    [submit-button]]
   [:div status]])

(defn edit-captured-ref-comp [{:keys [hput!]}]
  "A form to edit a captured reference."
  (let [selected-id-value (r/atom nil)
        selected-cap-ref (r/atom nil)
        editted-cap-ref (r/atom nil)
        status (r/atom nil)
        handle-submit
        (fn []
          (let [resp-chan (hput! @selected-id-value @editted-cap-ref)]
            (go (let [{:keys [error]} (<! resp-chan)]
                  (reset! status (if error "Error!" "Success!"))))))]
    (fn []
      [:div
       [:h3 "Edit Captured Reference Form"]
       [select-captured-ref
        ;; !!!! TODO -> Change for hget!
        {:get-captured-ref http/get-captured-reference!
         :on-selection #(do (reset! selected-cap-ref %) (reset! editted-cap-ref %))
         :id-value @selected-id-value
         :on-id-change #(reset! selected-id-value %)}]
       [edit-captured-ref-comp--form
        {:editted-cap-ref @editted-cap-ref
         :on-editted-cap-ref-change #(reset! editted-cap-ref %)
         :on-submit (call-prevent-default handle-submit)
         :status @status}]])))
