(ns kti-web.components.article-editor
  (:require
   [reagent.core :as r :refer [atom]]
   [kti-web.models.articles :as articles]
   [cljs.core.async :refer [chan <! >! put! go] :as async]))

(defn article-editor--inner
  "Pure component for editting an article"
  [props]
  [:div "ARTICLE EDITOR <NOT IMPLEMENTED>"])

(defn article-editor [{:keys [get-article! put-article!] :as props}]
  (let [state
        (atom {:loading? false
               :raw-editted-article-id nil
               :raw-editted-article nil
               :selected-article-id nil
               :status {:id-selection {:errors nil :success-msg nil}
                        :edit-article {:errors nil :success-msg nil}}})
        reset-for-id-submit
        #(-> %
             (assoc :raw-editted-article nil
                    :raw-editted-article-id nil
                    :loading? true)
             (assoc-in [:status :id-selection] {}))
        set-id-submit-error
        (fn [data]
          #(-> %
               (assoc :loading? false)
               (assoc-in [:status :id-selection] {:errors data})))
        set-id-submit-value
        (fn [data]
          #(-> %
               (assoc :loading? false
                      :raw-editted-article (articles/article->raw data)
                      :raw-editted-article-id (:id data))
               (assoc-in [:status :id-selection] {:success-msg "Success!"})))
        handle-article-id-submit
        (fn []
          (swap! state reset-for-id-submit)
          (let [out-chan (async/timeout 3000)
                resp-chan (get-article! (:selected-article-id @state))]
            (go
              (let [{:keys [error? data]} (<! resp-chan)]
                (swap! state (if error?
                               (set-id-submit-error data)
                               (set-id-submit-value data)))
                (>! out-chan :done)))
            out-chan))
        handle-edit-article-submit
        (fn []
          (let [out-chan (async/timeout 3000)
                resp-chan
                (let [{:keys [raw-editted-article-id raw-editted-article]} @state]
                  (put-article! raw-editted-article-id raw-editted-article))]
            (go
              (let [{:keys [error? data]} (<! resp-chan)]
                (if error?
                  (swap! state assoc-in [:status :edit-article :errors]
                         data)
                  (swap! state assoc-in [:status :edit-article :success-msg]
                         "Success!"))
                (>! out-chan :done)))
            out-chan))]
    (fn []
      [article-editor--inner
       (merge
        props
        @state
        {:on-article-id-change #(swap! state assoc :selected-article-id %)
         :on-article-id-submit handle-article-id-submit
         :on-raw-editted-article-change #(swap! state assoc :raw-editted-article %)
         :on-edit-article-submit handle-edit-article-submit})])))
