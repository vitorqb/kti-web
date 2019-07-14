(ns kti-web.components.rtable
  (:require
   ["react-table" :default ReactTable]
   [reagent.core :as r]
   [clojure.string :as str]))

(def default-react-table-props
  {:showPagination true
   :showPaginationTop true
   :showPaginationBottom true
   :defaultPageSize 50})

(defn wrap-on-fetch-data
  "Wraps a function that will be passed to OnFetchData prop."
  [f]
  (fn on-fetch-data-wrapper [instance-state-js _]
    (let [instance-state (js->clj instance-state-js :keywordize-keys true)
          {page :page page-size :pageSize} instance-state]
      (f {:page page :pageSize page-size}))))

(defn wrap-cell-fn
  "Wraps cell-fn to be used in the ReactTable as the Cell prop."
  [cell-fn]
  {:pre [(fn? cell-fn)] :post (fn? %)}
  (fn wrapped-cell-fn [row]
    {:pre [(.hasOwnProperty row "value")]}
    (let [value (-> row .-value (js->clj :keywordize-keys true))
          el-hiccup (cell-fn value)
          el-react (r/as-element el-hiccup)]
      el-react)))

(defn parse-column
  "Parses a single column for the rtable props, converting to the format expected
  by ReactTable."
  [{:keys [header cell-fn id width] :as column}]
  {:pre [(or (nil? cell-fn) (fn? cell-fn))
         (or (nil? width) (number? width))
         (not (nil? header))]}
  (-> column
      (cond-> header
        (-> (dissoc :header)
            (assoc :Header header)))
      (cond-> cell-fn
        (-> (dissoc :cell-fn)
            (assoc :Cell (wrap-cell-fn cell-fn))))
      (cond-> (and (nil? id) header)
        (assoc :id (str/lower-case header)))))

(defn parse-prop
  "Parse a single prop for the rtable component into what ReactTable expects"
  [[k v]]
  (case k
    :columns [k (map parse-column v)]
    :on-fetch-data [:onFetchData (wrap-on-fetch-data v)]
    [k v]))

(defn parse-props
  "Parses the props for the rtable component into what ReactTable expects."
  [props]
  {:pre [(map? props)]}
  (into default-react-table-props (map parse-prop props)))

(defn rtable
  "A table component wrapping ReactTable."
  [{:keys [data columns cell] :as props}]
  {:pre [(sequential? data)
         (sequential? columns)
         (> (count columns) 0)
         (or (nil? cell) (fn? cell))]}
  (let [parsedProps (parse-props props)]
    [(r/adapt-react-class ReactTable) parsedProps]))

