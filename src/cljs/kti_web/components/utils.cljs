(ns kti-web.components.utils
  (:require
   [kti-web.utils :as utils]
   [reagent.core :as r]
   ["react-select" :default Select]))

(defn select-wrapper
  "Wrapper around react-select providing a select component"
  [{:keys [value on-change options disabled]}]
  [(r/adapt-react-class Select)
   {:value      {:value value :label value}
    :options    (map (fn [x] {:value x :label x}) options)
    :on-change  #(-> % js->clj (get "value") on-change)
    :isDisabled (or disabled false)
    :styles {:container #(-> %1 js->clj (assoc :max-width 350) clj->js)}}])

(defn select
  "A select component. Options must be an array of strings."
  [{:keys [text options value on-change disabled]}]
  [:div.select-div
   [:span text]
   [select-wrapper
    {:options options
     :on-change on-change
     :value value
     :disabled (or disabled false)}]])

(defn input
  "A generic input component"
  [{:keys [text type disabled width value on-change placeholder className]}]
  [:div
   [:span text]
   [:input {:style {:width width}
            :value value
            :on-change (utils/call-with-val on-change)
            :type type
            :disabled (or disabled false)
            :placeholder placeholder
            :className className}]])

(defn textarea
  "A textarea component"
  [{:keys [text disabled rows cols value on-change]}]
  [:div
   [:span text]
   [:textarea {:rows (or rows 5)
               :cols (or cols 73)
               :disabled (or disabled false)
               :value value
               :on-change (utils/call-with-val on-change)}]])

(defn submit-button
  ([] (submit-button {:text "Submit!"}))
  ([{:keys [text disabled]}]
   [:button {:type "submit" :disabled (or disabled false)} (or text "Submit!")]))

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
  [{{errors :errors} :status}]
  [:div.errors-displayer
   (if (string? errors)
     errors
     (map errors-displayer-tree (into [] errors)))])

(defn success-message-displayer
  "Component responsible for rendering a success msg."
  [{{success-msg :success-msg} :status}]
  [:div.success-message {:style {:background-color "green"}} success-msg])
