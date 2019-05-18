(ns kti-web.event-handlers
  (:require [cljs.core.async :refer [<!]]
            [kti-web.utilsc :refer-macros [go-with-done-chan]]))

(defn gen-handler
  "Generates a handler for an event.
  The handler will:
  1) Reduce some state `state` before an action.
  2) Run an action and get a channel with it's results.
  3) Reduce `state` with the first item on the action result channel."
  [state extra-args {:keys [r-before action r-after]}]
  (fn []
    (let [init-state @state]
      (swap! state r-before extra-args)
      (let [ctx-chan (action init-state extra-args)]
        (go-with-done-chan (swap! state r-after extra-args (<! ctx-chan)))))))
