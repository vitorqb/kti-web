(ns kti-web.components.article-deletor
  (:require
   [reagent.core :refer [atom] :as r]
   [cljs.core.async :refer [go <! >!] :as async]
   [kti-web.utils :as utils]
   [kti-web.components.utils :as components-utils :refer [input]]))

(defprotocol ArticleDeletorEvents
  "Events for article deletor"
  (on-delete-article-id-submit [this]
    "Handles confirmation of deletion for the article.")

  (on-delete-article-id-change [this new-value]
    "Handles a chanve of value for the id to be deleted"))

(defn make-success-msg [id] (str "Deleted article with id " id))

(defn reduce-before-article-deletion [state] (assoc state :status {} :loading? true))

(defn ->status [state {:keys [error? data]}]
  (if error?
    {:errors data}
    {:success-msg (make-success-msg (:delete-article-id state))}))

(defn reduce-after-article-deletion
  "Prepares state after article deletion, parsing the response."
  [state http-resp]
  (assoc state :loading? false :status (->status state http-resp)))

(defn new-handler [state {:keys [delete-article!]}]
  (reify ArticleDeletorEvents

    (on-delete-article-id-submit [_]
      (let [{:keys [delete-article-id]} @state]
        (swap! state reduce-before-article-deletion)
        (let [resp-chan (delete-article! delete-article-id)]
          (go (swap! state reduce-after-article-deletion (<! resp-chan))
              :done))))

    (on-delete-article-id-change [_ new-value]
      (swap! state assoc :delete-article-id new-value))))

(def initial-state
  {:delete-article-id nil
   :status {:errors nil :success-msg nil}
   :loading? false})

(defn article-deletor--inner
  "Pure component for article deletion"
  [{:keys [delete-article-id handler loading?] :as specs}]
  [:div {}
   [:h4 "Delete Article"]
   (if loading?
     [:div "Loading..."]
     [:form {:on-submit (utils/call-prevent-default
                         #(on-delete-article-id-submit handler))}
      [input
       {:text "Article Id: "
        :width 100
        :value delete-article-id
        :on-change #(on-delete-article-id-change handler %)}]
      [components-utils/submit-button]])
   [components-utils/errors-displayer specs]
   [components-utils/success-message-displayer specs]])

(defn article-deletor
  "Component and state manager for article deletor"
  [{:keys [delete-article!] :as props}]
  (let [state (atom initial-state) handler (new-handler state props)]
    (fn []
      [article-deletor--inner
       (merge props @state {:handler handler})])))
