(ns ^:figwheel-no-load kti-web.dev
  (:require
    [kti-web.core :as core]
    [devtools.core :as devtools]
    [cljs.user]))

(devtools/install!)

(enable-console-print!)

(core/init!)
