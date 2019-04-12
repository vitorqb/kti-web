(ns cljs.user)

(defn set-token!
  "A shortcut to set the current token"
  [x]
  (reset! kti-web.state/token x))

(defn set-host!
  "A shortcut to set the current host"
  [x]
  (reset! kti-web.state/host x))
