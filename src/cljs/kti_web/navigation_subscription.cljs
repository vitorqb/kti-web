(ns kti-web.navigation-subscription
  (:require
   [cljs.core.async :as async :include-macros true]))

(defn- navigated-route->route-name
  [{{name :name} :data}]
  {:pre [(keyword? name)]}
  name)

(defn create-page-navigation-subscription
  "Creates a subscription for page navigation."
  []
  (let [chan (async/chan)
        pub  (async/pub chan navigated-route->route-name)
        subscribed (atom #{})]
    {:subscribe!
     (fn [topic chan]
       (async/sub pub topic chan))

     :publish!
     (fn [route]
       (async/go (async/>! chan route)))}))

