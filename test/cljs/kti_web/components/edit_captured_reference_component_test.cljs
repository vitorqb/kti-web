(ns kti-web.components.edit-captured-reference-component-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async
    :refer [chan <! >! put! go]
    :as async]
   [reagent.core :as reagent :refer [atom]]
   [kti-web.http :as http]
   [kti-web.components.edit-captured-reference-component :as rc]
   [kti-web.components.select-captured-ref :refer [select-captured-ref]]
   [kti-web.components.utils :as components-utils]
   [kti-web.utils :refer [to-str]]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))

(deftest test-captured-ref-inputs
  (let [mount rc/captured-ref-inputs]
    (let [comp (mount {:value {:id ::id
                               :created-at ::created-at
                               :reference ::reference}})]
      (testing "Renders id input"
        (is (= (get-in comp [1 1 :value]) ::id)))
      (testing "Renders created-at input"
        (is (= (get-in comp [2 1 :value]) ::created-at)))
      (testing "Renders reference input"
        (is (= (get-in comp [3 1 :value]) ::reference))))
    (testing "Calls on-change for reference"
      (let [[args fun] (utils/args-saver)
            comp (mount {:value {:id ::id} :on-change fun})]
        ((get-in comp [3 1 :on-change]) ::reference)
        (is (= @args [[{:id ::id :reference ::reference}]]))))))

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
        get-captured-ref-form #(get-in % [3 1])
        get-error-component #(get-in % [3 2])
        get-success-msg-component #(get-in % [3 3])]
    (testing "Initializes select-captured-ref"
      (let [params {:get-captured-ref :a
                    :on-cap-ref-selection :b
                    :selected-cap-ref-id-value :c
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
                    :edit-cap-ref-status :d}]
        (is (= (get-captured-ref-form (mount params))
               [rc/edit-captured-ref-form {:cap-ref :a
                                           :on-cap-ref-change :b
                                           :on-submit :c}]))))
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
    (testing "Displayes errors for select-cap-ref"
      (is (= (get-in (mount {:status {:select-cap-ref {:errors "foo"}}}) [3])
             [components-utils/errors-displayer {:status {:errors "foo"}}])))
    (testing "Displays error msg"
      (let [errors-map {:errors "foo"}
            comp (mount {:status {:edit-cap-ref errors-map} :editted-cap-ref {}})]
        (is (= (get-error-component comp)
               [components-utils/errors-displayer {:status errors-map}]))))
    (testing "Displayers success-msg"
      (let [success-map {:success-msg "bar"}
            comp (mount {:status {:edit-cap-ref success-map} :editted-cap-ref {}})]
        (is (= (get-success-msg-component comp)
               [components-utils/success-message-displayer {:status success-map}]))))))

(deftest test-edit-captured-ref-comp
  (let [get-inner-prop (fn [c k] (get-in c (conj [1] k)))]
    (testing "Mounts edit-captured-ref-comp--inner"
      (let [comp ((rc/edit-captured-ref-comp {:hget! :a}))]
        (is (= (get-in comp [0]) rc/edit-captured-ref-comp--inner))
        (is (= (get-inner-prop comp :get-captured-ref) :a))
        (is (= (get-inner-prop comp :status) {:edit-cap-ref {} :select-cap-ref {}}))
        (is (not (nil? (get-inner-prop comp :on-cap-ref-selection))))
        (is (nil? (get-inner-prop comp :selected-cap-ref-id-value)))
        (is (not (nil? (get-inner-prop comp :on-cap-ref-id-change))))
        (is (nil? (get-inner-prop comp :editted-cap-ref)))
        (is (not (nil? (get-inner-prop comp :on-editted-cap-ref-change))))
        (is (not (nil? (get-inner-prop comp :on-edit-cap-ref-submit))))
        (is (not (nil? (get-inner-prop comp :toggle-loading))))
        (is (nil? (get-inner-prop comp :edit-cap-ref-status)))
        (is (false? (get-inner-prop comp :loading?)))))
    (testing "Updates selected-cap-ref-id-value on cap-ref-id-change"
      (let [comp-1 (rc/edit-captured-ref-comp {})]
        (is (nil? (get-inner-prop (comp-1) :selected-cap-ref-id-value)))
        ((get-inner-prop (comp-1) :on-cap-ref-id-change) 999)
        (is (= (get-inner-prop (comp-1) :selected-cap-ref-id-value) 999))))
    (testing "Updates editted-cap-ref on editted-cap-ref-change"
      (let [comp-1 (rc/edit-captured-ref-comp {})]
        (is (nil? (get-inner-prop (comp-1) :editted-cap-ref)))
        ((get-inner-prop (comp-1) :on-editted-cap-ref-change) :foo)
        (is (= (get-inner-prop (comp-1) :editted-cap-ref) :foo))))
    (testing "Resets editted-cap-ref on selection"
      (let [comp-1 (rc/edit-captured-ref-comp {})]
        (is (nil? (get-inner-prop (comp-1) :editted-cap-ref)))
        ((get-inner-prop (comp-1) :on-cap-ref-selection) {:data :foo})
        (is (= (get-inner-prop (comp-1) :editted-cap-ref) :foo))))
    (testing "Sets loading by toggle-loading"
      (let [comp-1 (rc/edit-captured-ref-comp {})]
        ((get-inner-prop (comp-1) :toggle-loading) true)
        (is (true? (get-inner-prop (comp-1) :loading?)))
        ((get-inner-prop (comp-1) :toggle-loading) false)
        (is (false? (get-inner-prop (comp-1) :loading?)))))
    (testing "Updated status errors if selection fails"
      (let [comp-1 (rc/edit-captured-ref-comp {})
            ;; The http response has an error
            http-response factories/http-response-error-msg]
        ;; Calls on-cap-ref-selection with an errored response
        ((get-inner-prop (comp-1) :on-cap-ref-selection)
         (http/parse-response http-response))
        ;; Expects status to be set
        (is (= (get-inner-prop (comp-1) :status)
               {:select-cap-ref
                {:errors (:data (http/parse-response http-response))}}))
        ;; Now simulates a selection that works just fine
        ((get-inner-prop (comp-1) :on-cap-ref-selection) {::data {::a ::b}})
        ;; And the error should be nil again
        (is (= (get-inner-prop (comp-1) :status) {:select-cap-ref nil}))))))

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
    (let [ret-chan ((get-in (comp-1) [1 :on-edit-cap-ref-submit]))]
      ;; We see it loading
      (is (true? (get-in (comp-1) [1 :loading?])))
      ;; hput! was called with the id and the new cap ref
      (is (= @hput!-args [[(:id cap-ref) new-cap-ref]]))
      (async done
             (go
               ;; hput! writes something into the channel
               (>! hput!-chan 1)
               (is (= (<! ret-chan) :done))
               ;; We see it loading not more
               (is (false? (get-in (comp-1) [1 :loading?])))
               ;; The success msg is set
               (is (= (get-in (comp-1) [1 :status :edit-cap-ref])
                      {:success-msg "Success!"}))
               (done))))))

(deftest test-edit-captured-ref-comp--sets-error
  (let [parsed-error (http/parse-response factories/http-response-schema-error)
        hput!-chan (chan)
        comp-1 (rc/edit-captured-ref-comp {:hput! (constantly hput!-chan)})]
    ;; User selects cap-ref-id
    ((get-in (comp-1) [1 :on-cap-ref-id-change]) (:id factories/captured-ref))
    ;; And submits the selection
    ((get-in (comp-1) [1 :on-cap-ref-selection]) factories/captured-ref)
    ;; Changes the reference
    ((get-in (comp-1) [1 :on-editted-cap-ref-change])
     (assoc factories/captured-ref :reference "Baz"))
    ;; And submits
    (let [ret-chan ((get-in (comp-1) [1 :on-edit-cap-ref-submit]))]
      (async done
             (go
               ;; An error is returned
               (>! hput!-chan parsed-error)
               (is (= (<! ret-chan) :done))
               ;; And the error shows up in status
               (is (= (get-in (comp-1) [1 :status :edit-cap-ref])
                      {:errors (parsed-error :data)}))
               (done))))))

(deftest test-edit-captured-ref-comp--resets-state-between-submits
  (let [http-error {:error? true :data {::a ::b}}
        http-success {:data {::c ::d}}
        hput!-chan (async/timeout 2000)
        comp1 (rc/edit-captured-ref-comp {:hput! (constantly hput!-chan)})]
    ;; User submits
    (let [ret-chan ((get-in (comp1) [1 :on-edit-cap-ref-submit]))]
      (async
       done
       (go
         ;; An error is returned
         (>! hput!-chan http-error)
         (is (= (<! ret-chan) :done))
         ;; And the error is seen
         (is (= (get-in (comp1) [1 :status :edit-cap-ref])
                {:errors {::a ::b}}))
         ;; He submits again
         (let [ret-chan ((get-in (comp1) [1 :on-edit-cap-ref-submit]))]
           ;; A success msg is returned
           (>! hput!-chan http-success)
           (is (= (<! ret-chan) :done))
           ;; and the status is set
           (is (= (get-in (comp1) [1 :status :edit-cap-ref])
                  {:success-msg "Success!"}))
           (done)))))))
