(ns cljs.user
  (:require
   [cljs.test :as test :include-macros true]
   [kti-web.state]))

(defn set-token!
  "A shortcut to set the current token"
  [x]
  (reset! kti-web.state/token x))

(defn set-host!
  "A shortcut to set the current host"
  [x]
  (reset! kti-web.state/host x))

(defn set-token-and-host!
  "A shortcut to set the token and host"
  [token host]
  (set-token! token)
  (set-host! host))
