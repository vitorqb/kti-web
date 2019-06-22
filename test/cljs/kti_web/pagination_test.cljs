(ns kti-web.pagination-test
  (:require [kti-web.pagination :as sut]
            [cljs.test :refer-macros [is are deftest testing use-fixtures async]]))

(deftest test-is-paginated?
  (are [x] (is (sut/is-paginated? x))
    {:page 1 :page-size 2 :total-items 20 :items [1 2 3]}
    {:page 1 :page-size 2 :total-items 20 :items []}
    {:page 1 :page-size 2 :total-items 20 :items '()})
  (are [x] (is (not (sut/is-paginated? x)))
    {:page-size 2 :total-items 20 :items nil}
    {:page 1 :total-items 20 :items []}
    {:page 1 :page-size 2 :items '()}
    {:page 1 :page-size 2 :total-items 20}
    {:page 1 :page-size 2 :total-items 20 :items 2}))
