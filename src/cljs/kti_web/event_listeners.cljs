(ns kti-web.event-listeners
  (:require
   [cljs.core.async :as async]
   [cljs.core.async.impl.protocols :refer [Channel]]))

(defn public?
  "Returns true if handler is private."
  [handler]
  (get (meta handler) ::public))

(def private? #(not (apply public? %&)))

(defn as-public
  "Assocs metadata to the handler so that it is considered public."
  [handler]
  (with-meta handler {::public true}))

(defn- throw-unkown-event [event-kw]
  (throw (str "Unkown event " event-kw)))

(defn- throw-private-event [event-kw]
  (throw (str "Event " event-kw " is private")))

(defn listen! [chan handlers]
  [{:pre [(satisfies? Channel chan)
          (fn? handlers)]}]
  (async/go-loop []
    (let [[event-kw payload] (<! chan)
          handler (get handlers event-kw)]
      (cond
        (nil? handler) (throw-unkown-event event-kw)
        (private? handler) (throw-private-event event-kw)
        :else (handler payload))
      (recur))))
