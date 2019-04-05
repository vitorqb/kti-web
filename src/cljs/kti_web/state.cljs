(ns kti-web.state
  (:require
   [reagent.core :as r]
   [kti-web.local-storage :as local-storage]))

(def token (r/atom nil))
(add-watch token :save-token (fn [_ _ _ new] (local-storage/set-item! "TOKEN" new)))
(def host (r/atom "http://localhost:3333"))
(add-watch host :save-host (fn [_ _ _ new] (local-storage/set-item! "HOST" new)))
(defn api-url [x] (str @host "/api/" x))
(defn init-state []
  (some->> (local-storage/get-item "TOKEN") (reset! token))
  (some->> (local-storage/get-item "HOST") (reset! host)))
