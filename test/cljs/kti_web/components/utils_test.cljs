(ns kti-web.components.utils-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [kti-web.test-utils :as utils]
   [kti-web.components.utils :as rc]))

(deftest test-select-wrapper
  (let [mount rc/select-wrapper]
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

(deftest test-select
  (let [mount rc/select
        get-inner-select-props #(get-in % [2 1])
        get-text-span #(get % 1)]
    (testing "Text span"
      (is (= (-> {:text "foo"} mount get-text-span) [:span "foo"])))
    (testing "Set's options"
      (is (= (-> {:options ["a"]} mount get-inner-select-props :options) ["a"])))
    (testing "Set's value"
      (is (= (-> {:value "foo"} mount get-inner-select-props :value) "foo")))
    (testing "Disabled defeaults to false"
      (is (false? (-> {} mount get-inner-select-props :disabled))))
    (testing "Set's disabled"
      (is (true? (-> {:disabled true} mount get-inner-select-props :disabled))))))

(deftest test-input
  (let [mount rc/input
        get-inner-input-props #(get-in % [2 1])
        get-text-span #(get % 1)]
    (testing "Text value"
      (is (= (-> {:text "foo"} mount get-text-span) [:span "foo"])))
    (testing "No text span if no text"
      (nil? (= (-> {} mount get-text-span))))
    (testing "Set's type"
      (is (= (-> {:type ::a} mount get-inner-input-props :type) ::a)))
    (testing "Set's disabled"
      (is (true? (-> {:disabled true} mount get-inner-input-props :disabled))))
    (testing "Disabled defaults to false"
      (is (false? (-> {} mount get-inner-input-props :disabled))))
    (testing "Set's width"
      (is (= (-> {:width 9} mount get-inner-input-props :style :width) 9)))
    (testing "Set's value"
      (is (= (-> {:value "baz"} mount get-inner-input-props :value) "baz")))
    (testing "Calls on-change with change value"
      (let [[args fun] (utils/args-saver)
            comp (mount {:on-change fun})]
        ((-> comp get-inner-input-props :on-change) (utils/target-value-event "foo"))
        (is (= @args [["foo"]]))))
    (testing "Set's placeholder"
      (is (= (-> {:placeholder ::foo} mount get-inner-input-props :placeholder)
             ::foo)))
    (testing "Set's className"
      (is (= (-> {:className ::cls} mount get-inner-input-props :className)
             ::cls)))))
    
(deftest test-textarea
  (let [mount rc/textarea
        get-inner-textarea-props #(get-in % [2 1])]
    (testing "Rows default to 5"
      (is (= (-> {} mount get-inner-textarea-props :rows) 5)))
    (testing "Set's rows"
      (is (= (-> {:rows 10} mount get-inner-textarea-props :rows) 10)))
    (testing "cols default to 73"
      (is (= (-> {} mount get-inner-textarea-props :cols) 73)))
    (testing "Set's cols"
      (is (= (-> {:cols 10} mount get-inner-textarea-props :cols) 10)))
    (testing "Disabled defaults to false"
      (is (false? (-> {} mount get-inner-textarea-props :disabled))))
    (testing "Set's Disabled"
      (is (true? (-> {:disabled true} mount get-inner-textarea-props :disabled))))
    (testing "Set's value"
      (is (= (-> {:value "foo"} mount get-inner-textarea-props :value) "foo")))
    (testing "Calls on-change"
      (let [[args fun] (utils/args-saver)
            comp (mount {:on-change fun})]
        ((-> comp get-inner-textarea-props :on-change) (utils/target-value-event "foo"))
        (is (= @args [["foo"]]))))))

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
                     [:ul [:li "Baz"]]])])]))))
    (testing "Shows raw texts"
      (is (= (mount {:status {:errors "FOO"}})
             [:div.errors-displayer "FOO"])))))

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
