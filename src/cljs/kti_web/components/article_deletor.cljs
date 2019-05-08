(ns kti-web.components.article-deletor
  (:require
   [reagent.core :refer [atom] :as r]
   [cljs.core.async :refer [go <! >!] :as async]
   [kti-web.utils :as utils]
   [kti-web.utilsc :refer-macros [go-with-done-chan]]
   [kti-web.components.utils :as components-utils :refer [input]]))

(def initial-state
  {:delete-article-id nil
   :status {:errors nil :success-msg nil}
   :loading? false})

(defn article-deletor--inner
  "Pure component for article deletion"
  [{:keys [delete-article-id on-delete-article-id-change
           on-delete-article-id-submit loading?]
    :as specs}]
  [:div {}
   [:h3 "Delete Article"]
   (if loading?
     [:div "Loading..."]
     [:form {:on-submit (utils/call-prevent-default #(on-delete-article-id-submit))}
      [input
       {:text "Article Id: "
        :width 100
        :value delete-article-id
        :on-change on-delete-article-id-change}]
      [components-utils/submit-button]])
     [components-utils/errors-displayer specs]
     [components-utils/success-message-displayer specs]])

(defn make-success-msg
  [id]
  (str "Deleted article with id " id))

(defn reduce-before-article-deletion
  "Prepares state for article deletion."
  [state]
  (assoc state :status {} :loading? true))

(defn reduce-after-article-deletion
  "Prepares state after article deletion, parsing the response."
  [{:keys [error? data] :as http-resp}]
  (fn [state]
    (assoc
     state
     :loading? false
     :status (if error?
               {:errors data} 
               {:success-msg (make-success-msg (:delete-article-id state))}))))

(defn article-deletor
  "Component and state manager for article deletor"
  [{:keys [delete-article!] :as props}]
  (let [state (atom initial-state)
        handle-article-deletion-submit
        (fn []
          (let [{:keys [delete-article-id]} @state]
            (swap! state reduce-before-article-deletion)
            (let [resp-chan (delete-article! delete-article-id)]
              (go-with-done-chan
               (swap! state (reduce-after-article-deletion (<! resp-chan)))))))]
    (fn []
      [article-deletor--inner
       (merge
        props
        @state
        {:on-delete-article-id-change #(swap! state assoc :delete-article-id %)
         :on-delete-article-id-submit handle-article-deletion-submit})])))
