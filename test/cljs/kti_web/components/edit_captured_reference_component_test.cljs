(ns kti-web.components.edit-captured-reference-component-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put!]]
   [reagent.core :as reagent :refer [atom]]
   [kti-web.components.edit-captured-reference-component :as rc]
   [kti-web.test-utils :as utils]))

(deftest test-captured-ref-inputs--id
  (let [mount rc/captured-ref-inputs--id
        get-value #(get-in % [2 1 :value])
        get-disabled #(get-in % [2 1 :disabled])]
    (testing "Is disabled"
      (is (true? (get-disabled (mount)))))
    (testing "Shows value from id"
      (is (= (get-value (mount {:id 12})) 12)))))

(deftest test-captured-ref-inputs--created-at
  (let [mount rc/captured-ref-inputs--created-at
        get-value #(get-in % [2 1 :value])
        get-disabled #(get-in % [2 1 :disabled])]
    (testing "Is disabled"
      (is (true? (get-disabled (mount)))))
    (testing "Shows value from created-at"
      (is (= (get-value (mount {:created-at :bar})) :bar)))))

(deftest test-captured-ref-inputs--reference
  (let [mount rc/captured-ref-inputs--reference
        get-value #(get-in % [2 1 :value])
        get-on-change #(get-in % [2 1 :on-change])]
    (testing "Shows reference in value"
      (is (= (get-value (mount {:reference :foo})) :foo)))
    (testing "Calls on-reference-change"
      (let [[on-change-args on-change] (utils/args-saver)
            comp (mount {:on-reference-change on-change})]
        ((get-on-change comp) (clj->js {:target {:value "foo"}}))
        (is (= @on-change-args [["foo"]]))))))

(deftest test-captured-ref-inputs
  (let [mount rc/captured-ref-inputs]
    (testing "Passes value to reference input"
      (let [comp (mount {:value {:reference :foo}})]
        (is (= (get-in comp [3 0]) rc/captured-ref-inputs--reference))
        (is (= (get-in comp [3 1 :reference]) :foo))))
    (testing "Calls on-change if reference changes"
      (let [[on-change-args on-change] (utils/args-saver)
            value {:id 99 :reference "foo"}
            comp (mount {:value value :on-change on-change})]
        ((get-in comp [3 1 :on-reference-change]) "New Ref")
        (is (= [[{:id 99 :reference "New Ref"}]] @on-change-args))))
    (testing "Renders id input"
      (let [value {:foo "bar"} comp (rc/captured-ref-inputs {:value value})]
        (is (= (get-in comp [1]) [rc/captured-ref-inputs--id value]))))
    (testing "Renders created-at input"
      (let [value {:bar :baz} comp (rc/captured-ref-inputs {:value value})]
        (is (= (get-in comp [2]) [rc/captured-ref-inputs--created-at value]))))))

(deftest test-edit-captured-ref-form
  (let [get-hidden #(get-in % [1 :hidden])
        get-on-submit #(get-in % [2 1 :on-submit])
        get-inputs-value #(get-in % [2 2 1 :value])
        get-inputs-on-cap-ref-change #(get-in % [2 2 1 :on-change])
        mount rc/edit-captured-ref-comp--form]
    (testing "Is hidden unless editted-cap-ref is non nil"
      (is (true? (get-hidden (mount {:editted-cap-ref nil}))))
      (is (false? (get-hidden (mount {:editted-cap-ref {:a 1}})))))
    (testing "Calls on-submit on form submittion"
      (let [[on-submit-args on-submit] (utils/args-saver)
            event (utils/prevent-default-event)]
        ((get-on-submit (mount {:on-submit on-submit})) event)
        (is (= @on-submit-args [[event]]))))
    (testing "Value binding with captured-ref-inputs"
      (let [[on-editted-cap-ref-change-args  on-editted-cap-ref-change]
            (utils/args-saver)
            comp
            (mount {:on-editted-cap-ref-change on-editted-cap-ref-change
                    :editted-cap-ref {:a 1}})]
        (is (= (get-inputs-value comp) {:a 1}))
        ((get-inputs-on-cap-ref-change comp) {:b 2})
        (is (= [[{:b 2}]] @on-editted-cap-ref-change-args))))))

(deftest test-edit-captured-ref-comp
  (let [get-id-val #(get-in % [2 1 :id-value])
        get-on-id-change #(get-in % [2 1 :on-id-change])
        get-ref-form-on-selection #(get-in % [2 1 :on-selection])
        get-on-submit #(get-in % [3 1 :on-submit])
        cap-ref-form-hidden? #(get-in % [3 1 :hidden])
        get-edit-form-props #(get-in % [3 1])]
    (testing "Updates select-captured-ref current id"
      (let [comp-1 (rc/edit-captured-ref-comp)]
        (is (= nil (get-id-val (comp-1))))
        ((get-on-id-change (comp-1)) 921)
        (is (= 921 (get-id-val (comp-1))))))
    (testing "Initializes edit-captured-ref-comp--form"
      (let [cap-ref {:id 1 :reference "bar" :created-at "baz"}
            comp-1 (rc/edit-captured-ref-comp {})]
        ;; User selects some cap-ref
        ((get-ref-form-on-selection (comp-1)) cap-ref)
        ;; And edit-captured-ref-comp--form has the correct props
        (is (= (select-keys (get-edit-form-props (comp-1))
                            [:editted-cap-ref :status]))
               {:editted-cap-ref cap-ref :status nil})))
    (testing "Calls put! on submit"
      (let [[hput!-args save-hput!-args] (utils/args-saver)
            put-chan (chan 1)
            hput! (fn [id cap-ref] (save-hput!-args id cap-ref) put-chan)
            comp-1 (rc/edit-captured-ref-comp {:hput! hput!})]
        (put! put-chan {})
        ((get-on-id-change (comp-1)) 921)
        ((get-ref-form-on-selection (comp-1)) {:id 921 :reference "foo"})
        ((get-on-submit (comp-1)) (utils/prevent-default-event))
        (is (= [[921 {:id 921 :reference "foo"}]] @hput!-args))))))
