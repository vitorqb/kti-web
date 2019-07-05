(ns kti-web.components.article-creator
  (:require
   [reagent.core :as r :refer [atom]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.utils
    :refer [call-with-val call-prevent-default]
    :as utils]
   [kti-web.models.articles :as articles]
   [kti-web.components.utils :refer [input submit-button] :as components-utils]))

(defprotocol ArticleCreatorEvents
  "Events for article-creator."
  (on-article-spec-update [this new-spec]
    "Handles the change on the current specification for the article.")
  (on-article-creation-submit [this]
    "Handles the submission of the current specification for the article."))

(defn make-success-msg [{:keys [id]}] (str "Created article with id " id))

(defn article-creator-form
  [{:keys [article-spec handler]}]
  (letfn [(change-handler [k]
            #(on-article-spec-update handler (assoc article-spec k %)))]
    [:form {:on-submit (call-prevent-default #(on-article-creation-submit handler))}
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
  [{:keys [article-spec handler] :as props}]
  [:div
   [article-creator-form {:article-spec article-spec :handler handler}]
   [components-utils/errors-displayer props]
   [components-utils/success-message-displayer props]])

(defn- state->serialized-article-spec [{:keys [article-spec]}]
  (articles/serialize-article-spec article-spec))

(defn- post-response->status [{:keys [error? data]}]
  (if error?
    {:errors data}
    {:success-msg (make-success-msg data)}))

(defn new-event-handler [state {:keys [hpost!]}]
  "Returns a reified ArticleCreatorEvents for the component."
  (reify ArticleCreatorEvents

    (on-article-spec-update [_ new-spec]
      (swap! state assoc :article-spec new-spec))

    (on-article-creation-submit [_]
      (let [resp-chan (-> @state state->serialized-article-spec hpost!)]
        (swap! state assoc :status {})
        (go (let [status (-> resp-chan <! post-response->status)]
              (swap! state assoc :status status))
            :done)))))

(defn article-creator [props]
  (let [state (r/atom {:article-spec {} :status {}})
        handler (new-event-handler state props)]
    (fn []
      (let [{:keys [article-spec status]} @state]
        [article-creator--inner
         (merge @state props {:handler handler})]))))
