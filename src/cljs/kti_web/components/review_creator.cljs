(ns kti-web.components.review-creator
  (:require
   [kti-web.utils :as utils]
   [kti-web.utilsc :refer-macros [go-with-done-chan]]
   [kti-web.components.utils :as components-utils]
   [kti-web.models.reviews :as review-models]
   [reagent.core :as r :refer [atom]]
   [cljs.core.async :as async :refer [>! <! go]]))

(defn review-creator-form
  "Pure form component for creation of a review."
  [{:keys [review-raw-spec on-review-raw-spec-change on-review-creation-submit
           loading?]}]
  (let [handle-change
        (fn [k v] (on-review-raw-spec-change (assoc review-raw-spec k v)))
        render-input
        (fn [k]
          (let [[comp props] (get review-models/inputs k)]
            [comp (assoc props
                         :value (k review-raw-spec)
                         :on-change #(handle-change k %)
                         :disabled loading?)]))]
    [:form {:on-submit (utils/call-prevent-default #(on-review-creation-submit))}
     (render-input :id-article)
     (render-input :feedback-text)
     (render-input :status)
     [components-utils/submit-button {:disabled loading?}]]))

(defn review-creator-inner
  "Pure component for review creation."
  [specs]
  [:div
   [:h4 "Create Review"]
   [review-creator-form specs]
   [components-utils/errors-displayer specs]
   [components-utils/success-message-displayer specs]])

(def initial-state
  {:loading? false
   :status {}
   :review-raw-spec {}})

(defn make-success-msg [{:keys [id]}] (str "Created review with id" id))

(defn reduce-before-review-creation-submit
  "Prepares state before a review creation submit"
  [state]
  (assoc state :loading? true :status {}))

(defn reduce-review-creation-submit-response
  "Sets state with the response of submitting for review creation"
  [state {:keys [error? data] :as resp}]
  (assoc state
         :loading? false
         :status (if error?
                   {:errors data}
                   {:success-msg (make-success-msg data)})))

(defn review-creator
  [{:keys [post-review!]}]
  (let [state (atom initial-state)]
    (letfn [(handle-review-creation-submit []
              (swap! state reduce-before-review-creation-submit)
              (go-with-done-chan
               (->> @state
                    :review-raw-spec
                    review-models/raw-spec->spec
                    post-review!
                    <!
                    (swap! state reduce-review-creation-submit-response))))]
      (fn []
        [review-creator-inner
         (assoc @state
                :on-review-raw-spec-change #(swap! state assoc :review-raw-spec %)
                :on-review-creation-submit handle-review-creation-submit)]))))
