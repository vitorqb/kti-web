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
        (is (= @args [["foo"]]))))
    (testing "Passes perm-disabled option"
      (let [disabled-comp (mount {:disabled true})
            normal-comp   (mount {:disabled false})]
        (is (true? (get-in disabled-comp [1 :isDisabled])))
        (is (false? (get-in normal-comp [1 :isDisabled])))))))

(deftest test-make-select
  (let [get-disabled-prop #(get-in % [2 1 :disabled])]
  (testing "Permanently disabled"
    (are [f props] (f (get-disabled-prop ((rc/make-select props))))
      false? {}
      false? {:perm-disabled false}
      true?  {:perm-disabled true}))
  (testing "Temporarily disabled"
    (are [f props] (f (get-disabled-prop ((rc/make-select {}) props)))
      false? {}
      false? {:temp-disabled false}
      true?  {:temp-disabled true}))))

(deftest test-make-input
  (let [foo-input (rc/make-input {:text "Foo"})
        get-disabled-prop #(get-in % [2 1 :disabled])]
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
      (is (false? (get-disabled-prop ((rc/make-input {})))))
      (is (true? (get-disabled-prop ((rc/make-input {:perm-disabled true})))))
      (is (true? (get-disabled-prop ((rc/make-input {}) {:temp-disabled true}))))
      (is (true? (get-disabled-prop
                  ((rc/make-input {:perm-disabled true}) {:temp-disabled true})))))))

(deftest test-make-textarea
  (let [get-disabled #(get-in % [2 1 :disabled])]
    (testing "Renders components"
      (let [comp ((rc/make-textarea {:text "foo"}) {})]
        (is (= (get comp 0) :<>))
        (is (= (get comp 1) [:span "foo"]))
        (is (= (get-in comp [2 0]) :textarea))))
    (testing "Passes props"
      (are [k] (= (get-in ((rc/make-textarea {}) {k ::foo}) [2 1 k]) ::foo)
        :value
        :rows
        :cols))
    (testing "Calls on-change"
      (let [[args fun] (utils/args-saver)]
        (-> ((rc/make-textarea {}) {:on-change fun})
            (get-in [2 1 :on-change])
            (apply [(utils/target-value-event "foo")]))
        (is (= @args [["foo"]]))))
    (testing "Disabled"
      (are [f props] (f (get-disabled ((rc/make-textarea {}) props)))
        false? {}
        false? {:temp-disabled false}
        true?  {:temp-disabled true}))))

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

(deftest test-success-message-displayer
  (let [mount rc/success-message-displayer]
    (is (= (mount {:status {:success-msg "foo"}})
           [:div.success-message {:style {:background-color "green"}} "foo"]))))
