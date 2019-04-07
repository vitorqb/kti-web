(ns kti-web.components.edit-captured-reference-component-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go]]
   [reagent.core :as reagent :refer [atom]]
   [kti-web.components.edit-captured-reference-component :as rc]
   [kti-web.components.select-captured-ref :refer [select-captured-ref]]
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
  (let [mount rc/edit-captured-ref-form]
    (testing "Renders captured-ref-inputs"
      (let [cap-ref :foo
            on-cap-ref-change :bar
            comp (mount {:cap-ref cap-ref :on-cap-ref-change on-cap-ref-change})]
        (is (= (get-in comp [2])
               [rc/captured-ref-inputs
                {:value cap-ref :on-change on-cap-ref-change}]))))
    (testing "Calls on-submit with no args when user submits."
      (let [[on-submit-args on-submit] (utils/args-saver)
            comp (mount {:on-submit on-submit})]
        ((get-in comp [1 :on-submit]) (utils/prevent-default-event))
        (is (= @on-submit-args [[]]))))))

(deftest test-captured-ref-comp--inner
  (let [mount rc/edit-captured-ref-comp--inner
        get-captured-ref-form #(get-in % [3])]
    (testing "Initializes select-captured-ref"
      (let [params {:get-captured-ref :a
                    :on-cap-ref-selection :b
                    :cap-ref-id-value :c
                    :on-cap-ref-id-change :d
                    :toggle-loading :e}]
        (is (= (get-in (mount params) [2])
               [select-captured-ref {:get-captured-ref :a
                                     :on-selection :b
                                     :id-value :c
                                     :on-id-change :d
                                     :toggle-loading :e}]))))
    (testing "Initializes edit-captured-ref-form"
      (let [params {:editted-cap-ref :a
                    :on-editted-cap-ref-change :b
                    :on-edit-cap-ref-submit :c
                    :status :d}]
        (is (= (get-captured-ref-form (mount params))
               [rc/edit-captured-ref-form {:cap-ref :a
                                           :on-cap-ref-change :b
                                           :on-submit :c}]))))
    (testing "Shows status"
      (is (= (get-in (mount {:status "FOO"}) [4]) [:div "FOO"])))
    (testing "Hides edit-captured-ref-form if editted-cap-ref is nil"
      (is (nil? (get-captured-ref-form (mount {}))))
      (is (nil? (get-captured-ref-form (mount {:editted-cap-ref nil}))))
      (is (not (nil? (get-captured-ref-form (mount {:editted-cap-ref :a}))))))
    (testing "Shows loading if loading?"
      (is (= (get-in (mount {:loading? true}) [3]) [:span "Loading..."]))
      (is (= (get-in (mount {:loading? false}) [3]) nil)))
    (testing "Calls toggle-loading when select-captured-ref calls toggle-loading"
      (let [[toggle-loading-args toggle-loading] (utils/args-saver)
            comp (mount {:toggle-loading toggle-loading})]
        ((get-in comp [2 1 :toggle-loading]) true)
        ((get-in comp [2 1 :toggle-loading]) false)
        (is (= @toggle-loading-args [[true] [false]]))))
    (testing "Shows error msg if cap-ref-selection-error is set"
      (is (= (get-in (mount {:cap-ref-selection-error "foo"}) [3])
             [:span "ERROR: foo"])))))

(deftest test-edit-captured-ref-comp
  (let [get-inner-prop (fn [c k] (get-in c (conj [1] k)))]
    (testing "Mounts edit-captured-ref-comp--inner"
      (let [comp ((rc/edit-captured-ref-comp {:hget! :a}))]
        (is (= (get-in comp [0]) rc/edit-captured-ref-comp--inner))
        (is (= (get-inner-prop comp :get-captured-ref) :a))
        (is (not (nil? (get-inner-prop comp :on-cap-ref-selection))))
        (is (nil? (get-inner-prop comp :cap-ref-id-value)))
        (is (nil? (get-inner-prop comp :cap-ref-selection-error)))
        (is (not (nil? (get-inner-prop comp :on-cap-ref-id-change))))
        (is (nil? (get-inner-prop comp :editted-cap-ref)))
        (is (not (nil? (get-inner-prop comp :on-editted-cap-ref-change))))
        (is (not (nil? (get-inner-prop comp :on-edit-cap-ref-submit))))
        (is (not (nil? (get-inner-prop comp :toggle-loading))))
        (is (nil? (get-inner-prop comp :status)))
        (is (false? (get-inner-prop comp :loading?)))))
    (testing "Updates cap-ref-id-value on cap-ref-id-change"
      (let [comp-1 (rc/edit-captured-ref-comp {})]
        (is (nil? (get-inner-prop (comp-1) :cap-ref-id-value)))
        ((get-inner-prop (comp-1) :on-cap-ref-id-change) 999)
        (is (= (get-inner-prop (comp-1) :cap-ref-id-value) 999))))
    (testing "Updates editted-cap-ref on editted-cap-ref-change"
      (let [comp-1 (rc/edit-captured-ref-comp {})]
        (is (nil? (get-inner-prop (comp-1) :editted-cap-ref)))
        ((get-inner-prop (comp-1) :on-editted-cap-ref-change) :foo)
        (is (= (get-inner-prop (comp-1) :editted-cap-ref) :foo))))
    (testing "Resets editted-cap-ref on selection"
      (let [comp-1 (rc/edit-captured-ref-comp {})]
        (is (nil? (get-inner-prop (comp-1) :editted-cap-ref)))
        ((get-inner-prop (comp-1) :on-cap-ref-selection) :foo)
        (is (= (get-inner-prop (comp-1) :editted-cap-ref) :foo))))
    (testing "Sets loading by toggle-loading"
      (let [comp-1 (rc/edit-captured-ref-comp {})]
        ((get-inner-prop (comp-1) :toggle-loading) true)
        (is (true? (get-inner-prop (comp-1) :loading?)))
        ((get-inner-prop (comp-1) :toggle-loading) false)
        (is (false? (get-inner-prop (comp-1) :loading?)))))
    (testing "Updated cap-ref-selection-error if selection fails"
      (let [comp-1 (rc/edit-captured-ref-comp {})
            ;; The http response
            http-response {:status 404}]
        ;; Calls on-cap-ref-selection with an errored response
        ((get-inner-prop (comp-1) :on-cap-ref-selection)
         {:error true :response http-response})
        ;; Expects cap-ref-selection-error to have been set
        (is (= (get-inner-prop (comp-1) :cap-ref-selection-error) "Not found!"))
        ;; Now simulates a selection that works just fine
        ((get-inner-prop (comp-1) :on-cap-ref-selection) {})
        ;; And the error should be nil again
        (is (nil? (get-inner-prop (comp-1) :cap-ref-selection-error)))))))

(deftest test-edit-captured-ref-comp--calls-hput-on-submit
  (let [[hput!-args update-hput!-args] (utils/args-saver)
        hput!-chan (chan)
        hput! (fn [a b]
                (update-hput!-args a b)
                hput!-chan)
        comp-1 (rc/edit-captured-ref-comp {:hput! hput!})
        cap-ref {:id 29 :reference "hola"}
        new-cap-ref {:id 29 :reference "adeu"}]
    ;; User selects a cap-ref-id
    ((get-in (comp-1) [1 :on-cap-ref-id-change]) (:id cap-ref))
    ;; And submits the selection to get a cap-ref
    ((get-in (comp-1) [1 :on-cap-ref-selection]) cap-ref)
    ;; It edits the cap-ref
    ((get-in (comp-1) [1 :on-editted-cap-ref-change]) new-cap-ref)
    ;; And submits
    ((get-in (comp-1) [1 :on-edit-cap-ref-submit]))
    ;; hput! was called with the id and the new cap ref
    (is (= @hput!-args [[(:id cap-ref) new-cap-ref]]))
    ;; hput! writes something into the channel and the status is Success!
    (async done
           (go (>! hput!-chan 1)
               (js/setTimeout
                (fn []
                  (is (= (get-in (comp-1) [1 :status]) "Success!"))
                  (done))
                100)))))
