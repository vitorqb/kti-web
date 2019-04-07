(ns kti-web.components.article-creator
  (:require
   [reagent.core :as r :refer [atom]]
   [cljs.core.async :refer [chan <! >! put! go]]))

(defn article-creator-inputs--id-captured-reference
  [{:keys [value on-update]}]
  [:div [:span "Id Captured Reference"] [:input]])

(defn article-creator-inputs--description
  [{:keys [value on-update]}]
  [:div [:span "Description"] [:input]])

(defn article-creator-form
  [{:keys [article-spec on-article-spec-update on-submit]}]
  (letfn [(change-handler [k] #(on-article-spec-update (assoc article-spec k %)))]
    [:form {}
     [:h3 "Create Article"]
     [article-creator-inputs--id-captured-reference
      {:value (:id-captured-reference article-spec)
       :on-change (change-handler :id-captured-reference)}]
     [article-creator-inputs--description
      {:value (:description article-spec)
       :on-change (change-handler :description)}]]))

(defn article-creator--inner
  [{:keys [article-spec on-article-spec-update on-article-creation-submit]}]
  [:div
   [article-creator-form
    {:article-spec article-spec
     :on-article-spec-update on-article-spec-update
     :on-article-creation-submit on-article-creation-submit}]])

(defn article-creator [{:keys [hpost!]}]
  (let [article-spec (atom {})
        handle-article-creation-submit
        (fn []
          (let [out-chan (chan) resp-chan (hpost! @article-spec)]
            (go
              (let [{:keys [error]} (<! resp-chan)]
                (assert (nil? error) "Something went wrong on post call")
                (>! out-chan 1)))
            out-chan))]
    (fn []
      [article-creator--inner
       {:article-spec @article-spec
        :on-article-spec-update #(reset! article-spec %)
        :on-article-creation-submit handle-article-creation-submit}])))
