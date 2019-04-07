(ns kti-web.test-utils
  (:require [reagent.core :as reagent :refer [atom]]))

(defn args-saver []
  "Returns an array atom and a function that conj's into the atom
   value all arguments it receives."
  (let [args-atom (atom [])
        save-args (fn [& args] (swap! args-atom conj (into [] args)))]
    [args-atom save-args]))

(defn prevent-default-event
  ([] (prevent-default-event (constantly nil)))
  ([f] (clj->js {:preventDefault f})))

(defn target-value-event [x] (clj->js {:target {:value x}}))
