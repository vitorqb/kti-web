(ns kti-web.components.article-viewer
  (:require
   [reagent.core :as r]
   [cljs.core.async :refer [<! >!]]
   [kti-web.models.articles :as articles]
   [kti-web.utils :as utils :refer [join-vecs call-prevent-default]]
   [kti-web.components.utils :as components-utils :refer [input submit-button]]
   [kti-web.event-handlers :refer [handle!-vec]]
   [kti-web.event-listeners :as event-listeners]))

;; Helpers
(defn- http-resp->status [{:keys [error? data]}]
  (if error?
    {:errors data}
    {:success-msg "Success!"}))

(defn- http-resp->view-article [{:keys [error? data]}]
  (when-not error?
    (articles/article->raw data)))

;; Reducers
(defn reduce-on-selected-view-article-id-change [state new-value]
  (assoc state :selected-view-article-id new-value))

;; Handlers
(def view-article-id-selection
  [(fn before [s _ _]
     (assoc s :loading? true :status {} :view-article nil))

   (fn action [{:keys [selected-view-article-id]} {:keys [get-article!]} _]
     (get-article! selected-view-article-id))

   (fn after [s _ _ http-resp]
     (assoc s
            :loading? false
            :status (http-resp->status http-resp)
            :view-article (http-resp->view-article http-resp)))])

(defn handle-on-selected-view-article-id-change [state _]
  (fn [new-value]
    (swap! state reduce-on-selected-view-article-id-change new-value)))

(defn handle-on-selected-view-article-id-submit [state props]
  (fn []
    (handle!-vec nil state props view-article-id-selection)))

(defn handle-on-show-article [state props]
  "This event forces to component to show a specific article by id."
  (event-listeners/as-public
   (fn [article-id]
     ((handle-on-selected-view-article-id-change state props) article-id)
     ((handle-on-selected-view-article-id-submit state props)))))

;; State
(def state (r/atom {:loading? false
                    :status {}
                    :view-article nil
                    :selected-view-article-id nil}))

(defn- article-viewer-id-input
  "The id input when selecting an article by it's id."
  [{:keys [selected-view-article-id on-selected-view-article-id-change]}]
  [input {:text "ID: "
          :value selected-view-article-id
          :on-change #(on-selected-view-article-id-change %)
          :style {:width "100px"}
          :div-style {:display "inline"}
          :type "number"}])

(defn- wrap-disable-input
  "Wraps an input component, disabling it so it is read-only."
  [[compon old-props]]
  [compon (assoc old-props :disabled true :className "invalid-input")])

;; Components
(defn article-viewer-inner
  "Pure component to edit an article"
  [{:keys [view-article
           selected-view-article-id
           on-selected-view-article-id-submit
           on-selected-view-article-id-change]
    :as props}]
  [:div
   [:form {:on-submit (call-prevent-default #(on-selected-view-article-id-submit))}
    (article-viewer-id-input props)
    [submit-button]]
   (join-vecs
    [:div {:className "article-viewer-inputs-div"}]
    (for [k [:id :id-captured-reference :description :tags :action-link]
          :let [[compon old-props] (get articles/inputs k)
                value (get view-article k)
                props (assoc old-props :key k :value value)]]
      (wrap-disable-input [compon props])))
   [components-utils/errors-displayer props]])

(defn article-viewer
  "An component to edit an article."
  [{:keys [events-chan] :as props}]
  (let [handlers {:on-selected-view-article-id-change
                  (handle-on-selected-view-article-id-change state props)
                  :on-selected-view-article-id-submit
                  (handle-on-selected-view-article-id-submit state props)
                  :on-show-article
                  (handle-on-show-article state props)}]
    (when events-chan
      (event-listeners/listen! events-chan handlers))
    (fn []
      [article-viewer-inner (merge @state props handlers)])))
