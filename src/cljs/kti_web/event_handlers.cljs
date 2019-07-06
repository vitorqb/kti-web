(ns kti-web.event-handlers
  (:require [cljs.core.async :refer [<! go]]))

(defn gen-handler
  "Generates a handler for an event.
  The handler will:
  1) Reduce some state `state` before an action.
  2) Run an action and get a channel with it's results.
  3) Reduce `state` with the first item on the action result channel."
  [state extra-args {:keys [r-before action r-after]}]
  (fn handler! []
    (let [init-state @state]
      (swap! state r-before extra-args)
      (let [ctx-chan (action init-state extra-args)]
        (go (swap! state r-after extra-args (<! ctx-chan))
            :done)))))

(defn gen-handler-vec
  "Generates a handler for an event.
  Same as gen-handler, but takes functions from a vector of
  [before, action, after]"
  [state inject [before action after]]
  (fn handler! [event]
    (let [init-state @state]
      (swap! state before inject event)
      (let [ctx-chan (action init-state inject event)]
        (go (swap! state after inject event (<! ctx-chan))
            :done)))))

(defn handle!-vec
  "Generates a handle with `gen-handler-vec` and executes it."
  [event state inject [before action after]]
  (let [handler (gen-handler-vec state inject [before action after])]
    (handler event)))
