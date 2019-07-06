(ns kti-web.components.article-viewer
  (:require
   [reagent.core :as r]
   [cljs.core.async :refer [<! >!]]
   [kti-web.models.articles :as articles]
   [kti-web.utils :as utils :refer [join-vecs call-prevent-default]]
   [kti-web.components.utils :as components-utils :refer [input submit-button]]
   [kti-web.event-handlers :refer [handle!-vec]]))

(defprotocol ArticleViewerEvents
  "Events for article viewer."

  (on-selected-view-article-id-change [this new-value]
    "Handles change of the value for the selected article id")

  (on-selected-view-article-id-submit [this]
    "Handles submission of the selected article id for viewing"))

(defn- http-resp->status [{:keys [error? data]}]
  (if error?
    {:errors data}
    {:success-msg "Success!"}))

(defn- http-resp->view-article [{:keys [error? data]}]
  (when-not error?
    (articles/article->raw data)))

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

(defn new-handler [state props]
  (reify ArticleViewerEvents

    (on-selected-view-article-id-change [_ new-value]
      (swap! state assoc :selected-view-article-id new-value))

    (on-selected-view-article-id-submit [_]
      (handle!-vec nil state props view-article-id-selection))))

(def state (r/atom {:loading? false
                    :status {}
                    :view-article nil
                    :selected-view-article-id nil}))

(defn article-viewer-inner
  "Pure component to edit an article"
  [{:keys [view-article selected-view-article-id handler] :as props}]
  [:div
   [:form {:on-submit (call-prevent-default
                       #(on-selected-view-article-id-submit handler))}
    [input {:text "ID: "
            :value selected-view-article-id
            :on-change #(on-selected-view-article-id-change handler %)
            :width "100px"
            :type "number"}]
    [submit-button]]
   (join-vecs
    [:div {:className "article-viewer-inputs-div"}]
    (for [k [:id :id-captured-reference :description :tags :action-link]
          :let [[compon old-props] (get articles/inputs k)
                value (get view-article k)
                new-props (assoc old-props :key k :disabled true :value value)]]
      [compon new-props]))
   [components-utils/errors-displayer props]])

(defn article-viewer
  "An component to edit an article."
  [props]
  (fn []
    [article-viewer-inner
     (merge @state props {:handler (new-handler state props)})]))

