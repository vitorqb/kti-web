(ns kti-web.components.utils-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [kti-web.components.utils :as rc]))

(deftest test-errors-displayer
  (let [mount rc/errors-displayer]
    (testing "Don't show if errors is nil or {}"
      (are [errors] (is (= [:div.errors-displayer ()] (mount {:errors errors})))
        nil {}))
    (testing "Shows errors otherwise"
      (let [errors {:ROOT "Foo" :one "Bar" :two {:three "Baz"}}]
        (is (= (mount {:errors errors})
               [:div.errors-displayer
                '([:ul {:key "ROOT"}
                   [:li "ROOT"]
                   [:ul [:li "Foo"]]]
                  [:ul {:key "one"}
                   [:li "one"]
                   [:ul [:li "Bar"]]]
                  [:ul {:key "two"}
                   [:li "two"]
                   ([:ul {:key "three"}
                     [:li "three"]
                     [:ul [:li "Baz"]]])])]))))))

(deftest test-errors-displayer-tree
  (let [mount #'kti-web.components.utils/errors-displayer-tree]
    (testing "One long"
      (is (= (mount [:foo "Bar"])
             [:ul {:key "foo"} [:li "foo"] [:ul [:li "Bar"]]])))
    (testing "Three long"
      (is (= (mount [:b {:c "Bar" :d "Baz"}])
             [:ul {:key "b"}
              [:li "b"]
              '([:ul {:key "c"}
                 [:li "c"]
                 [:ul [:li "Bar"]]]
                [:ul {:key "d"}
                 [:li "d"]
                 [:ul [:li "Baz"]]])])))))
