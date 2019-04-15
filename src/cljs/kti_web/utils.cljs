(ns kti-web.utils
  (:require [cljs.pprint]))

;; -------------------------
;; Utils
(defn call-with-val [f] #(-> % .-target .-value f))
(defn call-prevent-default [f] #(do (.preventDefault %) (f %)))
(defn js-alert [x] (js/alert x))
(defn to-str [x] (with-out-str (cljs.pprint/pprint x)))
