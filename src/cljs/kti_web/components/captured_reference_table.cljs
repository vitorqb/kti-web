(ns kti-web.components.captured-reference-table
  (:require [cljs.core.async :refer [<! >! go]]
            [kti-web.utils :as utils :refer [js-alert]]
            [reagent.core :as r]))

(defn- captured-reference->tr
  "Makes a <tr> from a captured reference."
  [{:keys [id reference created-at classified]}]
  [:tr {:key id}
   [:td id]
   [:td reference]
   [:td created-at]
   [:td (str classified)]])

(defn captured-refs-table-inner
  "Pure component for a table of captured references."
  [{:keys [loading? refs fn-refresh!]}]
  (let [headers ["id" "ref" "created at" "classified?"]]
    [:div
     [:h3 "Captured References Table"]
     [:button {:on-click fn-refresh!} "Update"]
     (if loading?
       [:div "LOADING..."]
       [:table
        [:thead [:tr (map (fn [x] [:th {:key x} x]) headers)]]
        [:tbody (->> refs
                     (sort-by :created-at)
                     reverse
                     (map captured-reference->tr))]])]))

(def initial-state
  "The initial state for the captured reference table."
  {:loading true :refs nil})

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
    #(captured-refs-table-inner (assoc @state :fn-refresh! run-get!))))
