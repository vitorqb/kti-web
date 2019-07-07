(ns kti-web.components.capture-form-test
  (:require
   [cljs.core.async :as async :refer [go <! >!]]
   [kti-web.event-handlers :as event-handlers]
   [kti-web.components.capture-form :as rc]
   [kti-web.http :as http]
   [kti-web.test-factories :as factories]
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [kti-web.test-utils :as utils]))

(deftest test-on-submit-handle-fns
  (let [[before action after] rc/on-submit-handle-fns]

    (testing "Before"
      (is (= (before {} nil nil)
             {:loading? true :result nil})))

    (testing "Action"
      (let [[args saver] (utils/args-saver)
            value 9870
            state {:value value}
            props {:post! saver}]
        (action state props nil)
        (is (= @args [[value]]))))

    (testing "After"
      (let [response {:error? true :data "foo"}]
        (is (= (after {} {} nil response)
               {:loading? false :result (rc/response->result response)}))))))

(deftest test-handle-on-submit
  (testing "Handles with on-submit-handle-fns"
    (let [[args saver] (utils/args-saver)]
      (with-redefs [event-handlers/handle!-vec saver]
        (let [state (atom {::foo ::bar})
              props {::bar ::baz}
              handler (rc/handle-on-submit state props)]
          (handler)
          (is (= @args [[nil state props rc/on-submit-handle-fns]])))))))

(deftest test-capture-input
  (testing "Calls callback on change"
    (let [[callback-args callback] (utils/args-saver)
          comp          (rc/capture-input {:on-change callback :value ""})
          on-change     (get-in comp [2 1 :on-change])]
      (on-change (clj->js (assoc-in {} [:target :value] "new-input")))
      (is (= @callback-args [["new-input"]]))))

  (testing "Sets value from prop"
    (let [comp    (rc/capture-input {:on-change (constantly nil) :value "hola"})]
      (is (= (get-in comp [2 1 :value]) "hola")))))

(deftest test-capture-form
  (testing "Updates capture-input value on change"
    (let [comp-1      (rc/capture-form {})
          comp        (comp-1)
          on-change   (get-in comp [2 2 1 :on-change])]
      (on-change "new-input")
      (is (= (get-in (comp-1) [2 2 1 :value]) "new-input")))))

(deftest test-capture-form--sets-error
  (let [get-on-submit #(get-in % [2 1 :on-submit])
        get-result-div #(get-in % [2 4])
        post-chan (async/chan 1)
        comp-1 (rc/capture-form {:post! (constantly post-chan)})]
    ;; Submits
    (let [done-chan ((get-on-submit (comp-1)) (utils/prevent-default-event))]
      ;; Result is nil
      (is (= [:div nil] (get-result-div (comp-1))))
      (async done
             (go
               ;; Simulates errored response
               (>! post-chan (http/parse-response factories/http-response-error-msg))
               ;; Waits for completion
               (<! done-chan)
               ;; Result is "Error!"
               (is (= [:div "Error!"] (get-result-div (comp-1))))
               (done))))))

(deftest test-capture-form--sets-result
  (let [post-chan (async/chan 1)
        comp-1 (rc/capture-form {:post! (constantly post-chan)})
        get-on-submit #(get-in (comp-1) [2 1 :on-submit])
        get-result-div #(get-in (comp-1) [2 4])]
    ;; Submits
    (let [done-chan ((get-on-submit) (utils/prevent-default-event))]
       (async done
             (go
               ;; Simulates okay response
               (>! post-chan (factories/parsed-ok-response factories/captured-ref))
               ;; Waits
               (<! done-chan)
               ;; Results is corrects
               (is (= [:div "Created with id 49 and ref Foobarbaz"] (get-result-div)))
               (done))))))
