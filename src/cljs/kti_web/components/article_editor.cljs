(ns kti-web.components.article-editor
  (:require
   [reagent.core :as r :refer [atom]]
   [kti-web.utils :as utils]
   [kti-web.models.articles :as articles]
   [kti-web.components.utils :as components-utils :refer [input]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]))

(defprotocol ArticleEditorEvents
  "Events declaration for article-editor."
  (on-article-id-change [this selected-article-id]
    "Handles the change of the currently selected article id for edition.")
  (on-article-id-submit [this]
    "Handles the submition of the currently selected article id for edition")
  (on-raw-editted-article-change [this raw-editted-article]
    "Handles when the raw article editted by the user changes.")
  (on-edit-article-submit [this]
    "Handles the submition of the currently raw editted article."))

(defn article-editor-form
  "Pure form component for article editting"
  [{:keys [raw-editted-article-id raw-editted-article handler]}]
  (letfn [(handle-change [k]
            (fn [v]
              (on-raw-editted-article-change handler (assoc raw-editted-article k v))))]
    (utils/join-vecs
     [:form {:on-submit (utils/call-prevent-default #(on-edit-article-submit handler))}]
     (for [k [:id :id-captured-reference :description :tags :action-link]
           :let [[comp props] (get articles/inputs k)
                 value (case k
                         :id raw-editted-article-id
                         (get raw-editted-article k))
                 new-props (assoc props :value value :on-change (handle-change k))]]
       [comp new-props])
     [[components-utils/submit-button]])))

(defn article-selector
  "Pure form component for selecting an article."
  [{:keys [selected-article-id handler get-article!]}]
  [:form {:on-submit (utils/call-prevent-default #(on-article-id-submit handler))}
   [:span "Article Id: "]
   [:input {:value selected-article-id
            :on-change (utils/call-with-val #(on-article-id-change handler %))}]
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
               :raw-editted-article (-> data articles/article->raw (dissoc :id))
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

(defn new-event-handler [state {:keys [get-article! put-article!]}]
  (reify ArticleEditorEvents
    (on-article-id-change [_ selected-article-id]
      (swap! state assoc :selected-article-id selected-article-id))

    (on-article-id-submit [_]
      (let [{:keys [selected-article-id]} @state]
        (swap! state reset-state-for-id-submit)
        (let [resp-chan (get-article! selected-article-id)]
          (go
            (let [{:keys [error? data]} (<! resp-chan)
                  reducer (if error?
                            (set-state-on-id-submit-error data)
                            (set-state-on-id-submit-success data))]
              (swap! state reducer))
            :done))))

    (on-raw-editted-article-change [_ raw-editted-article]
      (swap! state assoc :raw-editted-article raw-editted-article))

    (on-edit-article-submit [_]
      (let [{:keys [raw-editted-article-id raw-editted-article]} @state]
        (swap! state reset-state-for-article-submit)
        (let [serialized-article-spec
              (articles/serialize-article-spec raw-editted-article)
              resp-chan
              (put-article! raw-editted-article-id serialized-article-spec)]
          (go
            (let [{:keys [error? data]} (<! resp-chan)
                  reducer (if error?
                            (set-state-on-edit-submit-error data)
                            set-state-on-edit-submit-success)]
              (swap! state reducer))
            :done))))))

(defn article-editor [{:keys [get-article! put-article!] :as props}]
  (let [state (atom {:loading? false
                     :raw-editted-article-id nil
                     :raw-editted-article nil
                     :selected-article-id nil
                     :status {:id-selection {:errors nil :success-msg nil}
                              :edit-article {:errors nil :success-msg nil}}})
        handler (new-event-handler state props)]
    (fn [] [article-editor--inner (merge props @state {:handler handler})])))
