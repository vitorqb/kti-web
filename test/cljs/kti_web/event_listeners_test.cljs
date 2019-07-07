(ns kti-web.event-listeners-test
  (:require
   [cljs.core.async :as async]
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [kti-web.event-listeners :as rc]))

(deftest test-private?
  (is (true?  (rc/private? (fn [] nil))))
  (is (false? (rc/private? ^::rc/public (fn [] nil)))))

(deftest test-as-public
  (is (true? (rc/public? (rc/as-public (fn [] nil))))))
