(ns kti-web.models.reviews-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [kti-web.models.reviews :as rc]))

(deftest test-raw-spec->spec
  (is (= ((rc/raw-spec->spec
           {:id-article 99 :feedback-text "Foo" :status "in-progress"})
         {:id-article 99 :feedback-text "Foo" :status :in-progress}))))
