(ns kti-web.navigation-subscription
  (:require
   [cljs.core.async :as async]))

(defn sub!
  "Calls async/sub. Used for testing."
  [pub topic chan]
  (async/sub pub topic chan))

(defn pub!
  "Calls async/pub. Used for testing."
  [chan topic-fn]
  (async/pub chan topic-fn))

(defn- navigated-route->route-name
  [{{name :name} :data}]
  {:pre [(keyword? name)]}
  name)

(defn create-page-navigation-subscription
  "Creates a subscription for page navigation."
  []
  (let [chan (async/chan)
        pub  (pub! chan navigated-route->route-name)
        subscribed (atom #{})]

    {:subscribe!
     (fn [unique-kw topic chan]
       (when-not (@subscribed unique-kw)
         (swap! subscribed conj unique-kw)
         (sub! pub topic chan)))

     :publish!
     (fn [route]
       (async/go (async/>! chan route)))}))

