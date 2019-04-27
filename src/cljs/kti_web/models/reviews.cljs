(ns kti-web.models.reviews)

(defn raw-status->status
  "Converts from a string to a status"
  [v]
  (case v
    "in-progress" :in-progress
    "completed"   :completed
    "discarded"   :discarded
    (throw (str "Unkown status " v))))

(defn raw-spec->spec
  "Converts from review raw-spec into a spec"
  [raw-spec]
  (into {} (map (fn [[k v]] [k (case k
                                 :status (raw-status->status v)
                                 v)])
                raw-spec)))
