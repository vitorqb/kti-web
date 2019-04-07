(ns kti-web.components.edit-captured-reference-component
  (:require
   [cljs.core.async :refer [go <!]]
   [reagent.core :as r]
   [kti-web.components.utils :refer [submit-button call-prevent-default]]
   [kti-web.components.select-captured-ref :refer [select-captured-ref]]
   [kti-web.utils :refer [call-prevent-default call-with-val]]))

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
           cap-ref-selection-error on-cap-ref-id-change editted-cap-ref
           on-editted-cap-ref-change on-edit-cap-ref-submit loading?
           toggle-loading status]}]
  [:div
   [:h3 "Edit Captured Reference Form"]
   [select-captured-ref
    {:get-captured-ref get-captured-ref
     :on-selection on-cap-ref-selection
     :id-value cap-ref-id-value
     :on-id-change on-cap-ref-id-change
     :toggle-loading toggle-loading}]
   (cond
     loading? [:span "Loading..."]
     cap-ref-selection-error [:span (str "ERROR: " cap-ref-selection-error)]
     editted-cap-ref
     [edit-captured-ref-form
      {:cap-ref editted-cap-ref
       :on-cap-ref-change on-editted-cap-ref-change
       :on-submit on-edit-cap-ref-submit}])
   [:div status]])

(defn edit-captured-ref-comp [{:keys [hput! hget!]}]
  "A form to edit a captured reference."
  (let [selected-id-value (r/atom nil)
        cap-ref-selection-error (r/atom nil)
        editted-cap-ref (r/atom nil)
        status (r/atom nil)
        loading? (r/atom false)
        handle-submit
        (fn []
          (let [resp-chan (hput! @selected-id-value @editted-cap-ref)]
            (go (let [{:keys [error]} (<! resp-chan)]
                  (reset! status (if error "Error!" "Success!"))))))
        handle-cap-ref-selection
        (fn [{:keys [error response] :as result}]
          (reset! editted-cap-ref nil)
          (if error
            (reset! cap-ref-selection-error
                    (if (= (:status response) 404) "Not found!" "Unkown error!"))
            (do
              (reset! editted-cap-ref result)
              (reset! cap-ref-selection-error nil))))]
    (fn []
      [edit-captured-ref-comp--inner
       {:get-captured-ref hget!
        :on-cap-ref-selection handle-cap-ref-selection
        :cap-ref-selection-error @cap-ref-selection-error
        :cap-ref-id-value @selected-id-value
        :on-cap-ref-id-change #(reset! selected-id-value %)
        :editted-cap-ref @editted-cap-ref
        :on-editted-cap-ref-change #(reset! editted-cap-ref %)
        :on-edit-cap-ref-submit handle-submit
        :status @status
        :loading? @loading?
        :toggle-loading #(reset! loading? %)}])))
