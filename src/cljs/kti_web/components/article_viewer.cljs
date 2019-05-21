(ns kti-web.components.article-viewer
  (:require
   [reagent.core :as r]
   [cljs.core.async :refer [<! >!]]
   [kti-web.models.articles :as articles]
   [kti-web.utils :as utils :refer [join-vecs call-prevent-default]]
   [kti-web.components.utils :as components-utils :refer [input submit-button]]
   [kti-web.event-handlers :refer [gen-handler]]))

(defn article-viewer-inner
  "Pure component to edit an article"
  [{:keys [view-article selected-view-article-id on-selected-view-article-id-change
           on-selected-view-article-id-submit]
    :as props}]
  [:div
   [:form {:on-submit (call-prevent-default on-selected-view-article-id-submit)}
    [input {:text "ID: "
            :value selected-view-article-id
            :on-change on-selected-view-article-id-change
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

(def state (r/atom {:loading? false
                    :status {}
                    :view-article nil
                    :selected-view-article-id nil}))

(def view-article-id-selection
  {:r-before (fn [s] (assoc s :loading? true :status {} :view-article nil))
   :action (fn [{:keys [selected-view-article-id]} {:keys [get-article!]}]
             (get-article! selected-view-article-id))
   :r-after (fn [s _ {:keys [error? data]}]
              (as-> s it
                (assoc it :loading? false)
                (if error?
                  (assoc it :status {:errors data})
                  (assoc it :status {:success-msg "Success!"}
                         :view-article (articles/article->raw data)))))})

(defn article-viewer
  "An component to edit an article."
  [{:keys [get-article!]}]
  (fn []
    [article-viewer-inner
     (assoc
      @state
      :on-selected-view-article-id-change
      #(swap! state assoc :selected-view-article-id %)
      :on-selected-view-article-id-submit
      (gen-handler state {:get-article! get-article!} view-article-id-selection))]))

