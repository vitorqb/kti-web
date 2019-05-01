(ns kti-web.components.utils
  (:require
   [kti-web.utils :as utils]
   [reagent.core :as r]
   [cljsjs.react-select]))

(defn select
  "Wrapper around react-select providing a select component"
  [{:keys [value on-change options disabled]}]
  [(r/adapt-react-class js/Select)
   {:value      {:value value :label value}
    :options    (map (fn [x] {:value x :label x}) options)
    :on-change  #(-> % js->clj (get "value") on-change)
    :isDisabled (or disabled false)}])

(defn make-select
  "Makes a select component. Options must be an array of strings."
  [{:keys [text options perm-disabled]}]
  (fn [{:keys [value on-change temp-disabled]}]
    [:<>
     [:span text]
     [select {:options options
              :on-change on-change
              :value value
              :disabled (or perm-disabled temp-disabled false)}]]))

(defn make-input
  "Makes an input component"
  [{:keys [text type perm-disabled width]}]
  (fn [{:keys [value on-change temp-disabled]}]
    [:<>
     [:span text]
     [:input {:style {:width (or width 600)}
              :value value
              :on-change (utils/call-with-val on-change)
              :type type
              :disabled (or perm-disabled temp-disabled false)}]]))

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
   (map errors-displayer-tree (into [] errors))])

(defn success-message-displayer
  "Component responsible for rendering a success msg."
  [{{success-msg :success-msg} :status}]
  [:div.success-message {:style {:background-color "green"}} success-msg])
