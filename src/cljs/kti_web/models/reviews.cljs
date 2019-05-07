(ns kti-web.models.reviews
  (:require
   [kti-web.components.utils
    :as components-utils
    :refer [make-input make-select make-textarea]]))

(def raw-status ["in-progress" "completed" "discarded"])
(def status     [:in-progress  :completed  :discarded])

(def inputs
  "Opts for the inputs components for a review."
  {:id-article    {:fun make-input :text "Article Id" :type "number"}
   :feedback-text {:fun make-textarea :text "Feedback Text"}
   :status        {:fun make-select :text "Status" :options raw-status}})

(defn raw-status->status
  "Converts from a string to a status"
  [v]
  (loop [[raw & raw-rest] raw-status [key & key-rest] status]
    (cond
      (nil? raw) (throw (str "Unkown status " v))
      (= raw v)  key
      :else      (recur raw-rest key-rest))))

(defn raw-spec->spec
  "Converts from review raw-spec into a spec"
  [raw-spec]
  (into {} (map (fn [[k v]] [k (case k
                                 :status (raw-status->status v)
                                 :id-article (js/parseInt v)
                                 v)])
                raw-spec)))
