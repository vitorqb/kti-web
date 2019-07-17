(ns kti-web.components.header
  (:require
   [kti-web.components.utils :as component-utils :refer [input]]))

;; Token and host inputs
(defn host-input [{:keys [value on-change]}]
  [input {:value value
          :on-change on-change
          :className "host-input"
          :placeholder "Host"}])

(defn token-input [{:keys [value on-change]}]
  [input {:value value
          :on-change on-change
          :className "token-input"
          :placeholder "Token"
          :type "password"}])

;; Header
(defn header
  "The component providing the header for kti-web.
  `path-for-fn` must be a function returning the path for a key."
  [{:keys [path-for-fn host-value on-host-value-change token-value
           on-token-value-change]}]
  [:header
   [:div.header-content-box
    [:h1.header-h1 "Keep This Info - Web"]
    [host-input {:on-change on-host-value-change :value host-value}]
    [token-input {:on-change on-token-value-change :value token-value}]
    [:div.header-routing-options
     [:span [:a {:href (path-for-fn :index)} "Home"] " | "]
     [:span [:a {:href (path-for-fn :article)} "Articles"] " | "]
     [:span [:a {:href (path-for-fn :review)} "Reviews"] " | "]
     [:span [:a {:href (path-for-fn :about)} "About kti-web"]]]]])
