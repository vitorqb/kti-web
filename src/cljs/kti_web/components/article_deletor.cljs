(ns kti-web.components.article-deletor
  (:require
   [reagent.core :refer [atom] :as r]
   [cljs.core.async :refer [go <! >!] :as async]
   [kti-web.utils :as utils]
   [kti-web.components.utils :as components-utils :refer [input]]))

;; Helpers
(defn make-success-msg [id]
  (str "Deleted article with id " id))

(defn ->status [state {:keys [error? data]}]
  (if error?
    {:errors data}
    {:success-msg (make-success-msg (:delete-article-id state))}))

;; Reducers
(defn reduce-before-article-deletion [state]
  (assoc state :status {} :loading? true))

(defn reduce-after-article-deletion
  "Prepares state after article deletion, parsing the response."
  [state http-resp]
  (assoc state :loading? false :status (->status state http-resp)))

(defn reduce-on-delete-article-id-change [state new-value]
  {:pre [(and new-value (number? new-value))]}
  (assoc state :delete-article-id new-value))

;; Handlers
(defn handle-on-delete-article-id-submit [state {:keys [delete-article!]}]
  (fn []
    (let [{:keys [delete-article-id]} @state]
      (swap! state reduce-before-article-deletion)
      (let [resp-chan (delete-article! delete-article-id)]
        (go (swap! state reduce-after-article-deletion (<! resp-chan)))))))

(defn handle-on-delete-article-id-change [state _]
  (fn [new-value]
    (swap! state reduce-on-delete-article-id-change new-value)))

;; State
(def initial-state
  {:delete-article-id nil
   :status {:errors nil :success-msg nil}
   :loading? false})

;; Components
(defn article-deletor--inner
  "Pure component for article deletion"
  [{:keys [delete-article-id
           on-delete-article-id-submit
           on-delete-article-id-change
           loading?]
    :as specs}]
  [:div {}
   [:h4 "Delete Article"]
   (if loading?
     [:div "Loading..."]
     [:form {:on-submit (utils/call-prevent-default #(on-delete-article-id-submit))}
      [input
       {:text "Article Id: "
        :width 100
        :value delete-article-id
        :on-change #(on-delete-article-id-change %)}]
      [components-utils/submit-button]])
   [components-utils/errors-displayer specs]
   [components-utils/success-message-displayer specs]])

(defn article-deletor
  "Component and state manager for article deletor"
  [{:keys [delete-article!] :as props}]
  (let [state (atom initial-state)]
    (fn []
      [article-deletor--inner
       (merge
        props
        @state
        {:on-delete-article-id-change (handle-on-delete-article-id-change state props)
         :on-delete-article-id-submit (handle-on-delete-article-id-submit state props)})])))
