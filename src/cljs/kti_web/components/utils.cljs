(ns kti-web.components.utils
  (:require
   [kti-web.utils :as utils]))

(defn make-input [{:keys [text type disabled width]}]
  "Makes an input component"
  (fn [{:keys [value on-change]}]
    [:<>
     [:span text]
     [:input {:style {:width (or width 600)}
              :value value
              :on-change (utils/call-with-val on-change)
              :type type
              :disabled (or disabled false)}]]))

(defn submit-button
  ([] (submit-button {:text "Submit!"}))
  ([{:keys [text]}] [:button {:type "Submit"} text]))

(defn- errors-displayer-tree
  "Recursively renders a <li> and <ul> tree of errors
  Receives an array with key value, where key is a symbol and value is either
  a map or a string."
  [[k v]]
  [:ul {:key (name k)}
   [:li (name k)]
   (if (string? v)
     [:ul [:li v]]
     (map errors-displayer-tree (into [] v)))])

(defn errors-displayer
  "Component responsible for rendering a map of errors."
  [{:keys [errors]}]
  [:div.errors-displayer
   (map errors-displayer-tree (into [] errors))])
