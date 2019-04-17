(ns kti-web.components.article-editor
  (:require
   [reagent.core :as r :refer [atom]]
   [kti-web.models.articles :as articles]
   [cljs.core.async :refer [chan <! >! put! go] :as async]))

(defn article-editor--inner
  "Pure component for editting an article"
  [props]
  [:div "ARTICLE EDITOR <NOT IMPLEMENTED>"])

(defn reset-state-for-id-submit
  "Resets an state map for an id submit"
  [state]
  (-> state
      (assoc :raw-editted-article nil :raw-editted-article-id nil :loading? true)
      (assoc-in [:status :id-selection] {})))

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
  (assoc-in state [:status :edit-article] {:success-msg "Success!"}))

(defn set-state-on-edit-submit-error
  "Set's the state map after an edit returns an error.
  Curried to accept the errors first."
  [errors]
  (fn [state]
    (assoc-in state [:status :edit-article] {:errors errors})))

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
                  (put-article! raw-editted-article-id raw-editted-article))]
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
