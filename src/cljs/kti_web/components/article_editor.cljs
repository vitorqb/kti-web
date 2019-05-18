(ns kti-web.components.article-editor
  (:require
   [reagent.core :as r :refer [atom]]
   [kti-web.utils :as utils]
   [kti-web.utilsc :refer-macros [go-with-done-chan]]
   [kti-web.models.articles :as articles]
   [kti-web.components.utils :as components-utils :refer [input]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]))

(def inputs
  {:id [input {:text "Id" :disabled true}]
   :id-captured-reference [input {:text "Captured Ref. Id"}]
   :description [input {:text "Description"}]
   :tags [input {:text "Tags"}]
   :action-link [input {:text "Action link"}]})

(defn article-editor-form
  "Pure form component for article editting"
  [{:keys [raw-editted-article-id raw-editted-article
           on-raw-editted-article-change on-edit-article-submit]}]
  (letfn [(handle-change [k]
            (fn [v]
              (on-raw-editted-article-change (assoc raw-editted-article k v))))
          (make-input [k]
            (let [[comp props] (get inputs k)]
              [comp (assoc props
                           :value (k raw-editted-article)
                           :on-change (handle-change k))]))]
    [:form {:on-submit (utils/call-prevent-default #(on-edit-article-submit))}
     (-> (make-input :id) (assoc-in [1 :value] raw-editted-article-id))
     (make-input :id-captured-reference)
     (make-input :description)
     (make-input :tags)
     (make-input :action-link)
     [components-utils/submit-button]]))

(defn article-selector
  "Pure form component for selecting an article."
  [{:keys [selected-article-id on-article-id-change on-article-id-submit
           get-article!]}]
  [:form {:on-submit (utils/call-prevent-default #(on-article-id-submit))}
   [:span "Article Id: "]
   [:input {:value selected-article-id
            :on-change (utils/call-with-val on-article-id-change)}]
   [components-utils/submit-button]])

(defn article-editor--inner
  "Pure component for editting an article"
  [{:keys [loading? raw-editted-article] :as props}]
  [:div
   [:h4 "Edit Article"]
   (if loading?
     [:div "Loading..."]
     [:div
      [article-selector props]
      [components-utils/errors-displayer
       {:status (get-in props [:status :id-selection])}]])
   (cond
     loading?            [:div "Loading..."]
     raw-editted-article [article-editor-form props]
     true                [:div])
   [components-utils/errors-displayer
    {:status (get-in props [:status :edit-article])}]
   [components-utils/success-message-displayer
    {:status (get-in props [:status :edit-article])}]])

(defn reset-state-for-id-submit
  "Resets an state map for an id submit"
  [state]
  (-> state
      (assoc :raw-editted-article nil :raw-editted-article-id nil :loading? true)
      (assoc-in [:status :id-selection] {})
      (assoc-in [:status :edit-article] {})))

(defn set-state-on-id-submit-error
  "Set's an state map after an id submit error.
  Curried function that first accepts the error data."
  [data]
  (fn [state]
    (-> state
        (assoc :loading? false)
        (assoc-in [:status :id-selection] {:errors data}))))

(defn set-state-on-id-submit-success
  "Set's an state map after an id submit is successfull.
  Curried function that first accepts the response data."
  [data]
  (fn [state]
    (-> state
        (assoc :loading? false
               :raw-editted-article (articles/article->raw data)
               :raw-editted-article-id (:id data))
        (assoc-in [:status :id-selection] {:success-msg "Success!"}))))

(defn reset-state-for-article-submit
  "Set's an state map on an article submit action."
  [state]
  (-> state
      (assoc :loading? true)
      (assoc-in [:status :edit-article] {:errors nil :success-msg nil})))

(defn set-state-on-edit-submit-success
  "Set's the state map after an edit is sucessfully submitted"
  [state]
  (-> state
      (assoc-in [:status :edit-article] {:success-msg "Success!"})
      (assoc :loading? false)))

(defn set-state-on-edit-submit-error
  "Set's the state map after an edit returns an error.
  Curried to accept the errors first."
  [errors]
  (fn [state]
    (-> state
        (assoc-in [:status :edit-article] {:errors errors})
        (assoc :loading? false))))

(defn article-editor [{:keys [get-article! put-article!] :as props}]
  (let [state
        (atom {:loading? false
               :raw-editted-article-id nil
               :raw-editted-article nil
               :selected-article-id nil
               :status {:id-selection {:errors nil :success-msg nil}
                        :edit-article {:errors nil :success-msg nil}}})
        handle-article-id-submit
        (fn []
          (swap! state reset-state-for-id-submit)
          (let [resp-chan (get-article! (:selected-article-id @state))]
            (go-with-done-chan
             (let [{:keys [error? data]} (<! resp-chan)]
               (swap! state (if error?
                              (set-state-on-id-submit-error data)
                              (set-state-on-id-submit-success data)))))))
        handle-edit-article-submit
        (fn []
          (swap! state reset-state-for-article-submit)
          (let [resp-chan
                (let [{:keys [raw-editted-article-id raw-editted-article]} @state]
                  (put-article!
                   raw-editted-article-id
                   (articles/serialize-article-spec raw-editted-article)))]
            (go-with-done-chan
             (let [{:keys [error? data]} (<! resp-chan)]
               (swap! state (if error?
                              (set-state-on-edit-submit-error data)
                              set-state-on-edit-submit-success))))))]
    (fn []
      [article-editor--inner
       (merge
        props
        @state
        {:on-article-id-change #(swap! state assoc :selected-article-id %)
         :on-article-id-submit handle-article-id-submit
         :on-raw-editted-article-change #(swap! state assoc :raw-editted-article %)
         :on-edit-article-submit handle-edit-article-submit})])))
