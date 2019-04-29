(ns kti-web.components.utils-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [kti-web.test-utils :as utils]
   [kti-web.components.utils :as rc]))

(deftest test-select
  (let [mount rc/select]
    (testing "Initializes with correct options"
      (let [options ["foo" "bar"] comp (mount {:options options})]
        ;; Select accepts {:label x :value x}
        (is (= (get-in comp [1 :options])
               (map #(hash-map :value % :label %) options)))))
    (testing "Initializes with correct value"
      (let [comp (mount {:value "foo"})]
        (is (= (get-in comp [1 :value]) {:value "foo" :label "foo"}))))
    (testing "Calls on-change on change"
      (let [[args fun] (utils/args-saver)
            comp (mount {:options ["foo"] :on-change fun})]
        ((get-in comp [1 :on-change]) {"value" "foo" "label" "foo"})
        (is (= @args [["foo"]]))))))

(deftest test-make-input
  (let [foo-input (rc/make-input {:text "Foo"})]
    (testing "Contains span with text"
      (is (= (get-in (foo-input {}) [1]) [:span "Foo"])))
    (testing "Binds value to input"
      (is (= (get-in (foo-input {:value :a}) [2 1 :value]) :a)))
    (testing "Calls on-change on change"
      (let [[on-change-args on-change] (utils/args-saver)
            comp (foo-input {:on-change on-change})]
        ((get-in comp [2 1 :on-change]) (utils/target-value-event "foo"))
        (is (= @on-change-args [["foo"]]))))
    (testing "Disabled"
      (is (true? (get-in ((rc/make-input {:disabled true})) [2 1 :disabled]))))))

(deftest test-errors-displayer
  (let [mount rc/errors-displayer]
    (testing "Don't show if errors is nil or {}"
      (are [errors]
          (is (= [:div.errors-displayer ()] (mount {:status {:errors errors}})))
        nil {}))
    (testing "Shows errors otherwise"
      (let [errors {:ROOT "Foo" :one "Bar" :two {:three "Baz"}}]
        (is (= (mount {:status {:errors errors}})
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
