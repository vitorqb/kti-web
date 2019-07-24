(ns kti-web.components.captured-reference-table.helpers)

(def empty-filter {:name nil :value nil})

(defn remove-empty-filters [coll] (remove #{empty-filter} coll))

(defn filter->hash-map [{:keys [name value]}] {name value})

(defn refs->data
  "Converts refs into rtable data."
  [refs]
  (sort-by :captured-at refs))

(defn calc-page-size [{:keys [total-items page-size]}]
  (.ceil js/Math (/ total-items page-size)))

(defn handler-wrapper-avoid-useless-fetching
  "Wraps a handler, and only calls it if the event :page or :pageSize has
  changed compared to the props :page and :pageSize"
  [handler {{props-page :page props-pageSize :pageSize} :table}]
  {:pre [(fn? handler) (number? props-page) (number? props-pageSize)]}
  (fn wrapped-handler [{event-page :page event-pageSize :pageSize :as event}]
    {:pre [(number? event-page) (number? event-pageSize)]}
    (when-not (= [props-page props-pageSize] [event-page event-pageSize])
      (handler event))))
