(ns kti-web.components.article-creator
  (:require
   [reagent.core :as r :refer [atom]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.utils
    :refer [call-with-val call-prevent-default]
    :as utils]
   [kti-web.models.articles :as articles]
   [kti-web.components.utils :refer [input submit-button] :as components-utils]))

(defn make-success-msg [{:keys [id]}] (str "Created article with id " id))

(defn article-creator-form
  [{:keys [article-spec on-article-spec-update on-article-creation-submit]}]
  (letfn [(change-handler [k] #(on-article-spec-update (assoc article-spec k %)))]
    [:form {:on-submit (call-prevent-default #(on-article-creation-submit))}
     [:h4 "Create Article"]
     [input
      {:text "Id Captued Reference"
       :type "number"
       :value (:id-captured-reference article-spec)
       :on-change (change-handler :id-captured-reference)}]
     [input
      {:value (:description article-spec)
       :on-change (change-handler :description)
       :text "Description"}]
     [input
      {:value (:tags article-spec)
       :on-change (change-handler :tags)
       :text "Tags"}]
     [input
      {:text "Action Link"
       :value (:action-link article-spec)
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
            (go
             (swap! state assoc :status {})
             (let [{:keys [error? data]} (<! resp-chan)]
               (swap! state assoc :status
                      (if error?
                        {:errors data}
                        {:success-msg (make-success-msg data)})))
             :done)))]
    (fn []
      (let [{:keys [article-spec status]} @state]
        [article-creator--inner
         {:article-spec article-spec
          :status       status
          :on-article-spec-update #(swap! state assoc :article-spec %)
          :on-article-creation-submit handle-article-creation-submit}]))))
