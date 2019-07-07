(ns kti-web.components.article-creator-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go timeout]]
   [kti-web.http :as http]
   [kti-web.components.article-creator :as rc]
   [kti-web.components.utils :as components-utils]
   [kti-web.models.articles :as articles]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))

(deftest test-article-creator-form
  (let [mount rc/article-creator-form]

    (testing "Two-way binding with id-captured-reference"
      (let [[args saver] (utils/args-saver)
            comp (mount {:article-spec {:id-captured-reference 99}
                         :on-article-spec-update saver})]
        ;; Has the value we passed
        (is (= (get-in comp [3 1 :value]) 99))
        ;; Responds to an change from the input
        ((get-in comp [3 1 :on-change]) 88)
        (is (= @args [[{:id-captured-reference 88}]]))))

    (testing "Two-way binding with description"
      (let [[args saver] (utils/args-saver)
            comp (mount {:article-spec {:description "foo"}
                         :on-article-spec-update saver})]
        (is (= (get-in comp [4 1 :value]) "foo"))
        ((get-in comp [4 1 :on-change]) "bar")
        (is (= @args [[{:description "bar"}]]))))

    (testing "Two-way binding with tags"
      (let [[args saver] (utils/args-saver)
            comp (mount {:article-spec {:tags []}
                         :on-article-spec-update saver})]
        (is (= (get-in comp [5 1 :value] [])))
        ((get-in comp [5 1 :on-change]) "foo, bar")
        (is (= @args [[{:tags "foo, bar"}]]))))

    (testing "Two-way binding with action-link"
      (let [[args saver] (utils/args-saver)
            comp (mount {:article-spec {:action-link nil}
                         :on-article-spec-update saver})]
        (is (= (get-in comp [6 1 :value] nil)))
        ((get-in comp [6 1 :on-change]) "www.google.com")
        (is (= @args [[{:action-link "www.google.com"}]]))))

    (testing "Calls on-article-creation-submit on submition"
      (let [[args saver] (utils/args-saver)
            comp (mount {:on-article-creation-submit saver})]
        ((get-in comp [1 :on-submit]) (utils/prevent-default-event))
        (is (= @args [[]]))))))

(deftest test-article-creator--inner
  (let [mount rc/article-creator--inner]
    
    (testing "Renders error component"
      (let [props {:status {:errors ::foo}}]
        (is (= [components-utils/errors-displayer props] (get (mount props) 2)))))

    (testing "Renders success-message component"
      (let [props {:status {:success-msg ::foo}}]
        (is (= (get (mount props) 3)
               [components-utils/success-message-displayer props]))))))

(deftest test-handle-on-article-spec-update
  (testing "Reduces state"
    (let [initial-state {:article-spec nil}
          state (atom initial-state)
          props {}
          handler (rc/handle-on-article-spec-update state props)]
      (handler ::foo)
      (is (= @state (rc/reduce-on-article-spec-update initial-state ::foo))))))

(deftest test-handle-on-article-creation-submit
  (let [post-chan (timeout 3000)
        article-spec factories/article-raw-spec
        initial-state {:article-spec article-spec :status ::baz}
        state (atom initial-state)
        props {:hpost! (constantly post-chan)}
        handler (rc/handle-on-article-creation-submit state props)]
    (async
     done
     (go
       ;; Calls submit event
       (let [resp-chan (handler)]
         ;; State is reseted to {}]
         (is (= @state (rc/reduce-before-article-creation-submit initial-state)))
         ;; hpost returns
         (>! post-chan {:data (assoc article-spec :id 8)})
         (<! resp-chan))
       ;; Success msg is now there
       (is (= (:status @state)) {:success-msg (rc/make-success-msg {:id 8})})
       (done)))))

(deftest test-article-creator
  (let [mount rc/article-creator
        get-inner-prop #(get-in %1 [1 %2])]

    (testing "Initializes article-creator--inner"
      (let [comp ((mount))]
        (is (= (get-in comp [0]) rc/article-creator--inner))
        (is (= (get-inner-prop comp :article-spec) {}))))

    (testing "Updates article-spec at on-article-spec-update"
      (let [comp-1 (mount)
            on-article-spec-update (get-in (comp-1) [1 :on-article-spec-update])]
        (on-article-spec-update {:a :b})
        (is (= (get-inner-prop (comp-1) :article-spec) {:a :b}))))))

(deftest test-article-creator--calls-hpost-on-submit
  (let [[hpost!-args hpost!-save-args] (utils/args-saver)
        hpost!-chan (chan)
        hpost! (fn [x] (hpost!-save-args x) hpost!-chan)
        comp-1 (rc/article-creator {:hpost! hpost!})
        on-article-spec-update (get-in (comp-1) [1 :on-article-spec-update])
        on-article-creation-submit (get-in (comp-1) [1 :on-article-creation-submit])
        article-spec factories/article-raw-spec]
    ;; User fills the form with an article spec
    (on-article-spec-update article-spec)
    ;; And submits
    (let [out-chan (on-article-creation-submit)]
      ;; hpost! is called with the serialized new article-spec
      (is (= @hpost!-args [[(articles/serialize-article-spec article-spec)]]))
      (async done
             (go
               ;; Simulates the response from the server
               (>! hpost!-chan {:data (assoc article-spec :id 9)})
               ;; And sees that post! is done
               (<! out-chan)
               ;; And the success-msg is set
               (is (= (get-in (comp-1) [1 :status])
                      {:success-msg (rc/make-success-msg {:id 9})}))
               (done))))))

(deftest test-article-creator--sets-errors
  (let [hpost!-chan (chan)
        comp-1 (rc/article-creator {:hpost! (constantly hpost!-chan)})
        on-article-spec-update (get-in (comp-1) [1 :on-article-spec-update])
        on-article-creation-submit (get-in (comp-1) [1 :on-article-creation-submit])
        errors {:foo "Bar"}
        http-resp-err
        (assoc-in factories/http-response-schema-error [:body :errors] errors)]
    ;; User fills article spec
    (on-article-spec-update factories/article-raw-spec)
    ;; Submits
    (let [out-chan (on-article-creation-submit)]
      (async done
             (go
               ;; Request returns an error
               (>! hpost!-chan (http/parse-response http-resp-err))
               (<! out-chan)
               ;; The error is set on the inner
               (is (= {:errors {:foo "Bar"}} (get-in (comp-1) [1 :status])))
               (done))))))
