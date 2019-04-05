(ns kti-web.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [kti-web.core-test]
            [kti-web.http-test]))

(doo-tests 'kti-web.core-test
           'kti-web.http-test)
