(ns kti-web.components.article-editor
  (:require
   [reagent.core :as r :refer [atom]]
   [kti-web.utils :as utils]
   [kti-web.models.articles :as articles]
   [kti-web.components.utils :as components-utils]
   [cljs.core.async :refer [chan <! >! put! go] :as async]))

(def id-input (components-utils/make-input {:text "Id" :disabled true}))
(def id-cap-ref-input (components-utils/make-input {:text "Captured Ref. Id"}))
(def description-input (components-utils/make-input {:text "Description"}))
(def tags-input (components-utils/make-input {:text "Tags"}))
(def action-link (components-utils/make-input {:text "Action link"}))

(defn article-editor-form
  "Pure form component for article editting"
  [{:keys [raw-editted-article-id raw-editted-article
           on-raw-editted-article-change on-edit-article-submit]}]
  (letfn [(handle-change [k]
            (fn [v]
              (on-raw-editted-article-change (assoc raw-editted-article k v))))
          (make-props [k]
            {:value (k raw-editted-article)
             :on-change (handle-change k)})]
    [:form {:on-submit (utils/call-prevent-default #(on-edit-article-submit))}
     [id-input {:value raw-editted-article-id}]
     [id-cap-ref-input (make-props :id-captured-reference)]
     [description-input (make-props :description)]
     [tags-input (make-props :tags)]
     [action-link (make-props :action-link)]
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
   [:h3 "Edit Article"]
   (if loading?
     [:div "Loading..."]
     [:div
      [article-selector (select-keys props [:selected-article-id
                                            :on-article-id-change
                                            :on-article-id-submit
                                            :get-article!])]
      [components-utils/errors-displayer
       {:errors (get-in props [:status :id-selection :errors])}]])
   (cond
     loading? [:div "Loading..."]
     raw-editted-article
     [article-editor-form (select-keys props [:raw-editted-article
                                              :raw-editted-article-id
                                              :on-raw-editted-article-change
                                              :on-edit-article-submit])]
     true [:div])
   [components-utils/errors-displayer
    {:errors (get-in props [:status :edit-article :errors])}]
   [:div (get-in props [:status :edit-article :success-msg] "")]])

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
          (let [out-chan (async/timeout 3000)
                resp-chan (get-article! (:selected-article-id @state))]
            (go
              (let [{:keys [error? data]} (<! resp-chan)]
                (swap! state (if error?
                               (set-state-on-id-submit-error data)
                               (set-state-on-id-submit-success data)))
                (>! out-chan :done)))
            out-chan))
        handle-edit-article-submit
        (fn []
          (swap! state reset-state-for-article-submit)
          (let [out-chan (async/timeout 3000)
                resp-chan
                (let [{:keys [raw-editted-article-id raw-editted-article]} @state]
                  (put-article!
                   raw-editted-article-id
                   (articles/serialize-article-spec raw-editted-article)))]
            (go
              (let [{:keys [error? data]} (<! resp-chan)]
                (swap! state (if error?
                               (set-state-on-edit-submit-error data)
                               set-state-on-edit-submit-success))
                (>! out-chan :done)))
            out-chan))]
    (fn []
      [article-editor--inner
       (merge
        props
        @state
        {:on-article-id-change #(swap! state assoc :selected-article-id %)
         :on-article-id-submit handle-article-id-submit
         :on-raw-editted-article-change #(swap! state assoc :raw-editted-article %)
         :on-edit-article-submit handle-edit-article-submit})])))