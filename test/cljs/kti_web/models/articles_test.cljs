(ns kti-web.models.articles-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [kti-web.models.articles :as rc]
   [kti-web.test-factories :as factories]))

(deftest test-serialize-article-spec
  (let [article-spec {:id-captured-reference "22"
                      :tags "foo,   bar"
                      :description "baz"
                      :action-link ""}
        serialized-article-spec {:id-captured-reference 22
                             :tags [:foo :bar]
                             :description "baz"
                             :action-link nil}]
    (testing "Base"
      (is (= (rc/serialize-article-spec article-spec) serialized-article-spec)))
    (testing "Missing action-link"
      ;; If an action-link is missing, it should be nil
      (is (= (rc/serialize-article-spec (dissoc article-spec :action-link))
             serialized-article-spec)))))

(deftest test-article->raw
  (testing "Base"
    (let [article {:id 8
                   :id-captured-reference 18
                   :tags ["foo" "bar"]
                   :description "Baz"
                   :action-link "Bum"}]
      (is (= (rc/article->raw article)
             {:id-captured-reference "18"
              :tags "foo, bar"
              :description "Baz"
              :action-link "Bum"}))))
  (testing "Null action-link"
    (is (nil? (-> factories/article
                  (assoc :action-link nil)
                  rc/article->raw
                  :action-link)))))
