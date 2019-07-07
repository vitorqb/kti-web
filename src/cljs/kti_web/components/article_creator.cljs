(ns kti-web.components.article-creator
  (:require
   [reagent.core :as r :refer [atom]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.utils
    :refer [call-with-val call-prevent-default]
    :as utils]
   [kti-web.models.articles :as articles]
   [kti-web.components.utils :refer [input submit-button] :as components-utils]))

;; Helpers
(defn make-success-msg [{:keys [id]}] (str "Created article with id " id))

(defn- state->serialized-article-spec [{:keys [article-spec]}]
  (articles/serialize-article-spec article-spec))

(defn- post-response->status [{:keys [error? data]}]
  (if error?
    {:errors data}
    {:success-msg (make-success-msg data)}))

;; Reducers
(defn reduce-on-article-spec-update [state new-spec]
  (assoc state :article-spec new-spec))

(defn reduce-before-article-creation-submit [state]
  (assoc state :status {}))

(defn reduce-after-article-creation-submit [state http-resp]
  (assoc state :status (post-response->status http-resp)))

;; Event Handlers
(defn handle-on-article-spec-update [state _]
  (fn [new-spec]
    (swap! state reduce-on-article-spec-update new-spec)))

(defn handle-on-article-creation-submit [state {:keys [hpost!]}]
  (fn []
    (let [resp-chan (-> @state state->serialized-article-spec hpost!)]
      (swap! state reduce-before-article-creation-submit)
      (go (swap! state reduce-after-article-creation-submit (<! resp-chan))))))

;; Components
(defn article-creator-form
  [{:keys [article-spec on-article-creation-submit on-article-spec-update]}]
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
  [{:keys [article-spec] :as props}]
  [:div
   [article-creator-form props]
   [components-utils/errors-displayer props]
   [components-utils/success-message-displayer props]])

(defn article-creator [props]
  (let [state (r/atom {:article-spec {} :status {}})]
    (fn []
      [article-creator--inner
       (merge
        @state
        props
        {:on-article-spec-update (handle-on-article-spec-update state props)
         :on-article-creation-submit (handle-on-article-creation-submit state props)})])))
