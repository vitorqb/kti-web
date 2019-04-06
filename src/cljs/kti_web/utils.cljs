(ns kti-web.utils)

;; -------------------------
;; Utils
(defn call-with-val [f] #(-> % .-target .-value f))
(defn call-prevent-default [f] #(do (.preventDefault %) (f %)))

