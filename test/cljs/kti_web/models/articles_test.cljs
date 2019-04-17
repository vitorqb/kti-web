(ns kti-web.models.articles-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [kti-web.models.articles :as rc]))

(deftest test-parse-article-spec
  (let [article-spec {:id-captured-reference "22"
                      :tags "foo,   bar"
                      :description "baz"
                      :action-link ""}
        parsed-article-spec {:id-captured-reference 22
                             :tags [:foo :bar]
                             :description "baz"
                             :action-link nil}]
    (testing "Base"
      (is (= (rc/parse-article-spec article-spec) parsed-article-spec)))
    (testing "Missing action-link"
      ;; If an action-link is missing, it should be nil
      (is (= (rc/parse-article-spec (dissoc article-spec :action-link))
             parsed-article-spec)))))
