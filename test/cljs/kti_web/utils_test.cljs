(ns kti-web.utils-test
  (:require [kti-web.utils :as rc]
            [cljs.test :as t :include-macros true]))


(t/deftest test-join-vecs
  (t/is (= (rc/join-vecs []) []))
  (t/is (= (rc/join-vecs [:a :b]) [:a :b]))
  (t/is (= (rc/join-vecs [:a :b] []) [:a :b]))
  (t/is (= (rc/join-vecs [:a :b] [:c :d]) [:a :b :c :d]))
  (t/is (= (rc/join-vecs [:a :b] [:c :d] [:e :f]) [:a :b :c :d :e :f])))
