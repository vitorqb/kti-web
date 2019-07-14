(ns ^:dev/once kti-web.dev
  (:require
    [kti-web.core :as core]
    [devtools.core :as devtools]
    [cljs.user]))

(.log js/console "Loading Development entrypoint...")

(devtools/install!)

(enable-console-print!)

(core/init!)

