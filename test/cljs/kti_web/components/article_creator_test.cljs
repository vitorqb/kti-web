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

(defn new-test-handler [calls-atom]
  (let [save-call! (fn [kw args] (swap! calls-atom update kw #(conj % args)))]
    (reify rc/ArticleCreatorEvents

      (on-article-spec-update [_ new-spec]
        (save-call! :on-article-spec-update [new-spec]))

      (on-article-creation-submit [_]
        (save-call! :on-article-creation-submit [])))))
(def handler-calls (atom {}))
(def test-handler (new-test-handler handler-calls))

(deftest test-article-creator-form
  (let [mount rc/article-creator-form]

    (testing "Two-way binding with id-captured-reference"
      (reset! handler-calls {})
      (let [comp (mount {:article-spec {:id-captured-reference 99}
                         :handler test-handler})]
        ;; Has the value we passed
        (is (= (get-in comp [3 1 :value]) 99))
        ;; Responds to an change from the input
        ((get-in comp [3 1 :on-change]) 88)
        (is (= (@handler-calls :on-article-spec-update)
               [[{:id-captured-reference 88}]]))))

    (testing "Two-way binding with description"
      (reset! handler-calls {})
      (let [comp (mount {:article-spec {:description "foo"}
                         :handler test-handler})]
        (is (= (get-in comp [4 1 :value]) "foo"))
        ((get-in comp [4 1 :on-change]) "bar")
        (is (= (@handler-calls :on-article-spec-update)
               [[{:description "bar"}]]))))

    (testing "Two-way binding with tags"
      (reset! handler-calls {})
      (let [comp (mount {:article-spec {:tags []}
                         :handler test-handler})]
        (is (= (get-in comp [5 1 :value] [])))
        ((get-in comp [5 1 :on-change]) "foo, bar")
        (is (= (@handler-calls :on-article-spec-update)
               [[{:tags "foo, bar"}]]))))

    (testing "Two-way binding with action-link"
      (reset! handler-calls {})
      (let [comp (mount {:article-spec {:action-link nil}
                         :handler test-handler})]
        (is (= (get-in comp [6 1 :value] nil)))
        ((get-in comp [6 1 :on-change]) "www.google.com")
        (is (= (@handler-calls :on-article-spec-update)
               [[{:action-link "www.google.com"}]]))))

    (testing "Calls on-article-creation-submit on submition"
      (reset! handler-calls {})
      (let [comp (mount {:handler test-handler})]
        ((get-in comp [1 :on-submit]) (utils/prevent-default-event))
        (is (= (@handler-calls :on-article-creation-submit) [[]]))))))

(deftest test-article-creator--inner
  (let [mount rc/article-creator--inner]

    (testing "Mounts article-creator-form with correct args"
      (let [params {:article-spec :a :handler test-handler}
            comp (mount params)]
        (is (= (get-in comp [1])
               [rc/article-creator-form {:article-spec :a :handler test-handler}]))))

    (testing "Renders error component"
      (let [props {:status {:errors ::foo}}]
        (is (= [components-utils/errors-displayer props] (get (mount props) 2)))))

    (testing "Renders success-message component"
      (let [props {:status {:success-msg ::foo}}]
        (is (= (get (mount props) 3)
               [components-utils/success-message-displayer props]))))))

(deftest test-new-event-handler--on-article-spec-update
  (testing "Assocs to state"
    (let [state (atom {:article-spec nil})
          props {}
          handler (rc/new-event-handler state props)]
      (rc/on-article-spec-update handler ::foo)
      (is (= @state {:article-spec ::foo})))))

(deftest test-new-event-handler--on-article-creation-submit
  (let [post-chan (timeout 3000)
        article-spec factories/article-raw-spec
        state (atom {:article-spec article-spec :status ::baz})
        props {:hpost! (constantly post-chan)}
        handler (rc/new-event-handler state props)]
    (async
     done
     (go
       ;; Calls submit event
       (let [resp-chan (rc/on-article-creation-submit handler)]
         ;; State is reseted to {}]
         (is (= (:status @state) {}))
         ;; hpost returns
         (>! post-chan {:data (assoc article-spec :id 8)})
         (is (= (<! resp-chan) :done)))
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
            handler (get-inner-prop (comp-1) :handler)]
        (rc/on-article-spec-update handler {:a :b})
        (is (= (get-inner-prop (comp-1) :article-spec) {:a :b}))))))

(deftest test-article-creator--calls-hpost-on-submit
  (let [[hpost!-args hpost!-save-args] (utils/args-saver)
        hpost!-chan (chan)
        hpost! (fn [x] (hpost!-save-args x) hpost!-chan)
        comp-1 (rc/article-creator {:hpost! hpost!})
        handler (get-in (comp-1) [1 :handler])
        article-spec factories/article-raw-spec]
    ;; User fills the form with an article spec
    (rc/on-article-spec-update handler article-spec)
    ;; And submits
    (let [out-chan (rc/on-article-creation-submit handler)]
      ;; hpost! is called with the serialized new article-spec
      (is (= @hpost!-args [[(articles/serialize-article-spec article-spec)]]))
      (async done
             (go
               ;; Simulates the response from the server
               (>! hpost!-chan {:data (assoc article-spec :id 9)})
               ;; And sees that post! is done
               (is (= (<! out-chan) :done))
               ;; And the success-msg is set
               (is (= (get-in (comp-1) [1 :status])
                      {:success-msg (rc/make-success-msg {:id 9})}))
               (done))))))

(deftest test-article-creator--sets-errors
  (let [hpost!-chan (chan)
        comp-1 (rc/article-creator {:hpost! (constantly hpost!-chan)})
        handler (get-in (comp-1) [1 :handler])
        errors {:foo "Bar"}
        http-resp-err
        (assoc-in factories/http-response-schema-error [:body :errors] errors)]
    ;; User fills article spec
    (rc/on-article-spec-update handler factories/article-raw-spec)
    ;; Submits
    (let [out-chan (rc/on-article-creation-submit handler)]
      (async done
             (go
               ;; Request returns an error
               (>! hpost!-chan (http/parse-response http-resp-err))
               (is (= :done (<! out-chan)))
               ;; The error is set on the inner
               (is (= {:errors {:foo "Bar"}} (get-in (comp-1) [1 :status])))
               (done))))))
