(ns kti-web.components.article-creator
  (:require
   [reagent.core :as r :refer [atom]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.utils
    :refer [call-with-val call-prevent-default]
    :refer-macros [go-with-done-chan]
    :as utils]
   [kti-web.models.articles :as articles]
   [kti-web.components.utils :refer [submit-button] :as components-utils]))

(def article-creator-inputs--id-captured-reference
  (components-utils/make-input {:text "Id Captued Reference" :type "number"}))
(def article-creator-inputs--description
  (components-utils/make-input {:text "Description"}))
(def article-creator-inputs--tags
  (components-utils/make-input {:text "Tags"}))
(def article-creator-inputs--action-link
  (components-utils/make-input {:text "Action Link"}))

(defn make-success-msg [{:keys [id]}] (str "Created article with id " id))

(defn article-creator-form
  [{:keys [article-spec on-article-spec-update on-article-creation-submit]}]
  (letfn [(change-handler [k] #(on-article-spec-update (assoc article-spec k %)))]
    [:form {:on-submit (call-prevent-default #(on-article-creation-submit))}
     [:h3 "Create Article"]
     [article-creator-inputs--id-captured-reference
      {:value (:id-captured-reference article-spec)
       :on-change (change-handler :id-captured-reference)}]
     [article-creator-inputs--description
      {:value (:description article-spec)
       :on-change (change-handler :description)}]
     [article-creator-inputs--tags
      {:value (:tags article-spec)
       :on-change (change-handler :tags)}]
     [article-creator-inputs--action-link
      {:value (:action-link article-spec)
       :on-change (change-handler :action-link)}]
     [submit-button]]))

(defn article-creator--inner
  [{:keys [article-spec on-article-spec-update on-article-creation-submit] :as props}]
  [:div
   [article-creator-form
    {:article-spec article-spec
     :on-article-spec-update on-article-spec-update
     :on-article-creation-submit on-article-creation-submit}]
   [components-utils/errors-displayer props]
   [components-utils/success-message-displayer props]])

(defn article-creator [{:keys [hpost!]}]
  (let [state (r/atom {:article-spec {} :status {}})
        handle-article-creation-submit
        (fn []
          (let [resp-chan (-> @state
                              :article-spec
                              articles/serialize-article-spec
                              hpost!)]
            (go-with-done-chan
             (swap! state assoc :status {})
             (let [{:keys [error? data]} (<! resp-chan)]
               (swap! state assoc :status
                      (if error?
                        {:errors data}
                        {:success-msg (make-success-msg data)}))))))]
    (fn []
      (let [{:keys [article-spec status]} @state]
        [article-creator--inner
         {:article-spec article-spec
          :status       status
          :on-article-spec-update #(swap! state assoc :article-spec %)
          :on-article-creation-submit handle-article-creation-submit}]))))
