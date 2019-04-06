(ns kti-web.doo-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [kti-web.core-test]
            [kti-web.http-test]
            [kti-web.components.edit-captured-reference-component-test]
            [kti-web.components.select-captured-ref-test]
            [kti-web.components.utils-test]))

(doo-tests 'kti-web.core-test
           'kti-web.http-test
           'kti-web.components.edit-captured-reference-component-test
           'kti-web.components.select-captured-ref-test
           'kti-web.components.utils-test)
