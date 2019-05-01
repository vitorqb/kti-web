(ns kti-web.components.review-creator-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.http :as http]
   [kti-web.components.review-creator :as rc]
   [kti-web.models.reviews :as review-models]
   [kti-web.components.utils :as components-utils]
   [kti-web.models.articles :as articles]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))

;; Helpers
(defn get-prop-review-creator-inner
  [c k]
  (get-in c [1 k]))

;; Tests
(deftest test-review-creator-form
  (let [mount rc/review-creator-form
        get-submit-button #(get-in % [5])]
    (testing "Changing id-article"
      (let [id 9
            new-id 8
            [args fun] (utils/args-saver)
            comp (mount {:review-raw-spec {:id-article id}
                         :on-review-raw-spec-change fun})]
        (is (= (get-in comp [2 1 :value]) id))
        ((get-in comp [2 1 :on-change]) new-id)
        (is (= @args [[{:id-article new-id}]]))))
    (testing "Changing feedback-text"
      (let [old-text "Foo"
            new-text "Bar"
            [args fun] (utils/args-saver)
            comp (mount {:review-raw-spec {::a ::b :feedback-text old-text}
                         :on-review-raw-spec-change fun})]
        (is (= (get-in comp [3 1 :value]) old-text))
        ((get-in comp [3 1 :on-change]) new-text)
        (is (= @args [[{::a ::b :feedback-text new-text}]]))))
    (testing "Changing status"
      (let [old-status "in-progress"
            new-status "completed"
            [args fun] (utils/args-saver)
            comp (mount {:review-raw-spec {:status old-status}
                         :on-review-raw-spec-change fun})]
        (is (= (get-in comp [4 1 :value]) old-status))
        ((get-in comp [4 1 :on-change]) new-status)
        (is (= @args [[{:status new-status}]]))))
    (testing "Submitting"
      (let [[args fun] (utils/args-saver)
            comp (mount {:on-review-creation-submit fun})]
        ((get-in comp [1 :on-submit]) (utils/prevent-default-event))
        (is (= @args [[]]))))
    (let [normal-comp (mount {:loading false})
          loading-comp (mount {:loading? true})]
      (testing "Disables inputs when loading"
        (are [i] (true? (get-in loading-comp [i 1 :temp-disabled])) 2 3 4)
        (are [i] (nil? (get-in normal-comp [i 1 :temp-disabled])) 2 3 4))
      (testing "Disables button when loading"
        (is (true? (->  loading-comp get-submit-button (get-in [1 :disabled]))))
        (is (nil? (->  normal-comp get-submit-button (get-in [1 :disabled]))))))))

(deftest test-review-creator-inner
  (let [mount rc/review-creator-inner
        get-err-comp #(get % 3)
        get-success-msg-comp #(get % 4)]
    (testing "Set's error msg"
      (let [status {:status {:errors ::a}}]
      (is (= (-> status mount get-err-comp)
             [components-utils/errors-displayer status]))))
    (testing "Set's success msg"
      (let [specs {:status {:success-msg ::a}}]
      (is (= (-> specs mount get-success-msg-comp)
             [components-utils/success-message-displayer specs]))))))

(deftest test-reduce-before-review-creation-submit
  (is (= (rc/reduce-before-review-creation-submit {})
         {:status {} :loading? true})))

(deftest test-reduce-review-creation-submit-response
  (is (= (rc/reduce-review-creation-submit-response
          {}
          {:error? true :data {::a ::b}})
         {:loading? false :status {:errors {::a ::b}}}))
  (is (= (rc/reduce-review-creation-submit-response
          {:review-raw-spec ::a :loading? true :status {:errors {}}}
          {:error? false :data {:id 99}})
         {:review-raw-spec ::a
          :loading? false
          :status {:success-msg (rc/make-success-msg {:id 99})}})))

(deftest test-review-creator
  (let [mount rc/review-creator
        get-prop get-prop-review-creator-inner]
    (testing "Updates review-raw-spec"
      (let [comp1 (mount {})]
        (is (= (get-prop (comp1) :review-raw-spec) {}))
        ((get-prop (comp1) :on-review-raw-spec-change) factories/review-raw-spec)
        (is (= (get-prop (comp1) :review-raw-spec) factories/review-raw-spec))))))

(deftest test-review-creator--submit
  (let [get-prop get-prop-review-creator-inner
        [post!-args post!-args-saver] (utils/args-saver)
        post!-chan (async/timeout 4000)
        post! #(do (apply post!-args-saver %&) post!-chan)
        comp1 (rc/review-creator {:post-review! post!})]
    ;; State is not loading
    (is (false? (get-prop (comp1) :loading?)))
    ;; User enters the values for the review
    ((get-prop (comp1) :on-review-raw-spec-change) factories/review-raw-spec)
    ;; And submits
    (let [done-chan ((get-prop (comp1) :on-review-creation-submit))]
      ;; We are now loading
      (is (true? (get-prop (comp1) :loading?)))
      (async
       done
       (go
         ;; And we see a call to post!
         (is (= @post!-args
                [[(review-models/raw-spec->spec factories/review-raw-spec)]]))
         ;; We answer it
         (>! post!-chan {:data factories/review})
         (is (= :done (<! done-chan)))
         ;; We are no longer loading
         (is (false? (get-prop (comp1) :loading?)))
         ;; And we see the success-msg
         (is (= (get-prop (comp1) :status)
                {:success-msg (rc/make-success-msg factories/review)}))
         (done))))))

(deftest test-review-creator--submit-with-error
  (let [[args args-saver] (utils/args-saver)
        post!-chan (async/timeout 3000)
        post! #(do (args-saver %&) post!-chan)
        comp1 (rc/review-creator {:post-review! post!})
        get-prop #(get-in (comp1) [1 %])]
    (async
     done
     (go
       ;; Makes a request
       (let [ret-chan ((get-prop :on-review-creation-submit))]
         ;; Request returns an error
         (>! post!-chan {:error? true :data {::a ::b}})
         (is (= (<! ret-chan) :done))
         ;; Error is passed to review-creator-inner
         (is (= (get-prop :status) {:errors {::a ::b}}))
         (done))))))
