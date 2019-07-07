(ns kti-web.components.article-editor
  (:require
   [reagent.core :as r :refer [atom]]
   [kti-web.utils :as utils]
   [kti-web.models.articles :as articles]
   [kti-web.components.utils :as components-utils :refer [input]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]))

;; Helpers

;; Reducers
(defn reduce-on-article-id-change [state new-value]
  {:pre [(number? new-value)]}
  (assoc state :selected-article-id new-value))

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

(defn reduce-on-article-id-submit [state {:keys [error? data] :as http-resp}]
  (let [reducer (if error?
                  (set-state-on-id-submit-error data)
                  (set-state-on-id-submit-success data))]
    (reducer state)))

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

(defn reduce-on-edit-submit [state {:keys [error? data] :as http-resp}]
  (if error?
    ((set-state-on-edit-submit-error data) state)
    (set-state-on-edit-submit-success state)))

(defn reduce-on-raw-editted-article-change [state raw-editted-article]
  (assoc state :raw-editted-article raw-editted-article))

;; Handlers
(defn handle-on-article-id-change [state _]
  (fn [selected-article-id]
    (swap! state reduce-on-article-id-change selected-article-id)))

(defn handle-on-article-id-submit [state {:keys [get-article!]}]
  (fn []
    (let [{:keys [selected-article-id]} @state]
      (swap! state reset-state-for-id-submit)
      (let [resp-chan (get-article! selected-article-id)]
        (go (swap! state reduce-on-article-id-submit (<! resp-chan)))))))

(defn handle-on-raw-editted-article-change [state _]
  (fn [raw-editted-article]
    (swap! state reduce-on-raw-editted-article-change raw-editted-article)))

(defn handle-on-edit-article-submit [state {:keys [put-article!]}]
  (fn []
    (let [{:keys [raw-editted-article-id raw-editted-article]} @state]
      (swap! state reset-state-for-article-submit)
      (let [resp-chan (->> raw-editted-article
                           articles/serialize-article-spec
                           (put-article! raw-editted-article-id))]
        (go (swap! state reduce-on-edit-submit (<! resp-chan)))))))

;; State

;; Components
(defn article-editor-form
  "Pure form component for article editting"
  [{:keys [raw-editted-article-id
           raw-editted-article
           on-raw-editted-article-change
           on-edit-article-submit]}]
  (letfn [(handle-change [k]
            (fn [v]
              (on-raw-editted-article-change (assoc raw-editted-article k v))))]
    (utils/join-vecs
     [:form {:on-submit (utils/call-prevent-default #(on-edit-article-submit))}]
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
  [{:keys [selected-article-id
           on-article-id-submit
           on-article-id-change]}]
  [:form {:on-submit (utils/call-prevent-default #(on-article-id-submit))}
   [:span "Article Id: "]
   [:input {:value selected-article-id
            :on-change (utils/call-with-val #(on-article-id-change %))}]
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

(defn article-editor [props]
  (let [state (atom {:loading? false
                     :raw-editted-article-id nil
                     :raw-editted-article nil
                     :selected-article-id nil
                     :status {:id-selection {:errors nil :success-msg nil}
                              :edit-article {:errors nil :success-msg nil}}})]
    (fn []
      [article-editor--inner
       (merge
        props
        @state
        {:on-article-id-change (handle-on-article-id-change state props)
         :on-article-id-submit (handle-on-article-id-submit state props)
         :on-raw-editted-article-change (handle-on-raw-editted-article-change state props)
         :on-edit-article-submit (handle-on-edit-article-submit state props)})])))
