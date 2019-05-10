(ns kti-web.models.reviews
  (:require
   [kti-web.components.utils
    :as components-utils
    :refer [input select textarea]]))

(def raw-status ["in-progress" "completed" "discarded"])
(def status     [:in-progress  :completed  :discarded])

(def inputs
  "Opts for the inputs components for a review."
  {:id-article [input {:text "Article Id" :type "number"}]
   :feedback-text [textarea {:text "Feedback Text"}]
   :status [select {:text "Status" :options raw-status}]})

(defn raw-status->status
  "Converts from a string to a status"
  [v]
  (loop [[raw & raw-rest] raw-status [key & key-rest] status]
    (cond
      (nil? raw) (throw (str "Unkown status " v))
      (= raw v)  key
      :else      (recur raw-rest key-rest))))

(defn status->raw-status
  "Converts a status into raw-status"
  [x]
  (first (filter #(= (raw-status->status %) x) raw-status)))

(defn raw-spec->spec
  "Converts from review raw-spec into a spec"
  [raw-spec]
  (into {} (map (fn [[k v]] [k (case k
                                 :status (raw-status->status v)
                                 :id-article (js/parseInt v)
                                 v)])
                raw-spec)))

(defn review->raw-spec
  "Converts from a review into a raw spec"
  [{:keys [status id-article id] :as review}]
  (-> review
      (update :id str)
      (update :id-article str)
      (update :status status->raw-status)))

(defn server-resp->review
  "Deserializes the response from the server into a review."
  [{:keys [status] :as review}]
  (update review :status raw-status->status))
