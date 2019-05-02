(ns kti-web.utilsc)

(defmacro go-with-done-chan
  "Executes body in a go block, returning a channel in which :done is written
   as the last step of the go block."
  {:style/indent 0}
  [& body]
  `(let [resp-chan# (cljs.core.async/chan)]
     (cljs.core.async/go
       ~@body
       (cljs.core.async/>! resp-chan# :done))
     resp-chan#))
