(ns kti-web.components.captured-reference-table
  (:require [cljs.core.async :refer [<! >! go]]
            [kti-web.utils :as utils :refer [js-alert]]
            [reagent.core :as r]))

(def columns
  "An array of maps describing the table columns."
  [{:head-text "id"          :fn-get :id}
   {:head-text "ref"         :fn-get :reference}
   {:head-text "created at"  :fn-get :created-at}
   {:head-text "classified?" :fn-get (comp str :classified)}
   {:head-text "article id"  :fn-get :article-id}])

(defn- make-thead
  "Prepares a thead for the table, given an array of columns."
  [cols]
  (->> cols
       (map (fn [{:keys [head-text]}] [:th {:key head-text} head-text]))
       (apply vector :tr)
       (vector :thead)))

(defn- make-tbody
  "prepares a tbody for the table, given an array of columns and rows."
  [cols rows]
  (->> rows
       (map (fn [row]
              (->> cols
                   (map (fn [{:keys [fn-get]}] [:td (fn-get row)]))
                   (apply vector :tr))))
       (apply vector :tbody)))

(defn captured-refs-table-inner
  "Pure component for a table of captured references."
  [{:keys [loading? refs fn-refresh!]}]
  (let [headers ["id" "ref" "created at" "classified?"]]
    [:div
     [:h3 "Captured References Table"]
     [:button {:on-click #(fn-refresh!)} "Update"]
     (if loading?
       [:div "LOADING..."]
       [:table
        (make-thead columns)
        (->> refs (sort-by :created-at) reverse (make-tbody columns))])]))

(def initial-state
  "The initial state for the captured reference table."
  {:loading? true :refs nil})

(defn- reduce-before-get-all-captured-references
  [state]
  (assoc state :loading? true :refs nil))

(defn- reduce-get-all-captured-references-response
  [{:keys [error? data] :as resp}]
  (fn [state]
    (if error?
      (do
        (js-alert (str "Error during get: " (:ROOT data)))
        (assoc state :loading? false :refs []))
      (assoc state :loading? false :refs data))))

(defn captured-refs-table [{:keys [get! c-done]}]
  (let [state (r/atom initial-state)
        run-get!
        (fn []
          (swap! state reduce-before-get-all-captured-references)
          (let [get-chan (get!)]
            (go
              (->> get-chan
                   <!
                   reduce-get-all-captured-references-response
                   (swap! state))
              (and c-done (>! c-done 1)))))]
    (run-get!)
    (fn []
      [captured-refs-table-inner (assoc @state :fn-refresh! run-get!)])))
