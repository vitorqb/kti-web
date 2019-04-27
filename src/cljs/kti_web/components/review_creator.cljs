(ns kti-web.components.review-creator
  (:require
   [kti-web.utils :refer-macros [go-with-done-chan]]
   [kti-web.models.reviews :as review-models]
   [reagent.core :as r :refer [atom]]
   [cljs.core.async :as async :refer [>! <! go]]))

(defn review-creator-inner
  "Pure component for review creation."
  [specs]
  [:span "NOT IMPLEMENTED"])

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
