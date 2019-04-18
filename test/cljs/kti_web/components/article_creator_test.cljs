(ns kti-web.components.article-creator-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go]]
   [kti-web.http :as http]
   [kti-web.components.article-creator :as rc]
   [kti-web.components.utils :as components-utils]
   [kti-web.models.articles :as articles]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))

(deftest test-article-creator-form
  (let [mount rc/article-creator-form]
    (testing "Two-way binding with id-captured-reference"
      (let [[on-article-spec-update-args on-article-spec-update] (utils/args-saver)
            comp (mount {:article-spec {:id-captured-reference 99}
                         :on-article-spec-update on-article-spec-update})]
        ;; Has the value we passed
        (is (= (get-in comp [3 1 :value]) 99))
        ;; Responds to an change from the input
        ((get-in comp [3 1 :on-change]) 88)
        (is (= @on-article-spec-update-args [[{:id-captured-reference 88}]]))))
    (testing "Two-way binding with description"
      (let [[on-article-spec-update-args on-article-spec-update] (utils/args-saver)
            comp (mount {:article-spec {:description "foo"}
                         :on-article-spec-update on-article-spec-update})]
        (is (= (get-in comp [4 1 :value]) "foo"))
        ((get-in comp [4 1 :on-change]) "bar")
        (is (= @on-article-spec-update-args [[{:description "bar"}]]))))
    (testing "Two-way binding with tags"
      (let [[on-article-spec-update-args on-article-spec-update] (utils/args-saver)
            comp (mount {:article-spec {:tags []}
                         :on-article-spec-update on-article-spec-update})]
        (is (= (get-in comp [5 1 :value] [])))
        ((get-in comp [5 1 :on-change]) "foo, bar")
        (is (= @on-article-spec-update-args [[{:tags "foo, bar"}]]))))
    (testing "Two-way binding with action-link"
      (let [[on-article-spec-update-args on-article-spec-update] (utils/args-saver)
            comp (mount {:article-spec {:action-link nil}
                         :on-article-spec-update on-article-spec-update})]
        (is (= (get-in comp [6 1 :value] nil)))
        ((get-in comp [6 1 :on-change]) "www.google.com")
        (is (= @on-article-spec-update-args [[{:action-link "www.google.com"}]]))))
    (testing "Calls on-article-creation-submit on submition"
      (let [[on-submit-args on-submit] (utils/args-saver)
            comp (mount {:on-article-creation-submit on-submit})]
        ((get-in comp [1 :on-submit]) (utils/prevent-default-event))
        (is (= @on-submit-args [[]]))))))

(deftest test-article-creator--inner
  (let [mount rc/article-creator--inner]
    (testing "Mounts article-creator-form with correct args"
      (let [params {:article-spec :a
                    :on-article-spec-update :b
                    :on-article-creation-submit :c}
            comp (mount params)]
        (is (= (get-in comp [1])
               [rc/article-creator-form {:article-spec :a
                                         :on-article-spec-update :b
                                         :on-article-creation-submit :c}]))))
    (testing "Renders error component"
      (is (= [components-utils/errors-displayer {:errors ::foo}]
             (get (mount {:errors ::foo}) 2))))))

(deftest test-article-creator
  (let [mount rc/article-creator
        get-inner-prop #(get-in %1 [1 %2])]
    (testing "Initializes article-creator--inner"
      (let [comp ((mount))]
        (is (= (get-in comp [0]) rc/article-creator--inner))
        (is (= (get-inner-prop comp :article-spec) {}))))
    (testing "Updates article-spec at on-article-spec-update"
      (let [comp-1 (mount)]
        ((get-inner-prop (comp-1) :on-article-spec-update) {:a :b})
        (is (= (get-inner-prop (comp-1) :article-spec) {:a :b}))))))

(deftest test-article-creator--calls-hpost-on-submit
  (let [[hpost!-args hpost!-save-args] (utils/args-saver)
        hpost!-chan (chan)
        hpost! (fn [x] (hpost!-save-args x) hpost!-chan)
        comp-1 (rc/article-creator {:hpost! hpost!})
        article-spec factories/article-raw-spec]
    ;; User fills the form with an article spec
    ((get-in (comp-1) [1 :on-article-spec-update]) article-spec)
    ;; And submits
    (let [out-chan ((get-in (comp-1) [1 :on-article-creation-submit]))]
      ;; hpost! is called with the serialized new article-spec
      (is (= @hpost!-args [[(articles/serialize-article-spec article-spec)]]))
      (async done
             (go
               ;; Simulates the response from the server
               (>! hpost!-chan (assoc article-spec :id 9))
               ;; And sees that post! is done
               (is (= (<! out-chan) :done))
               (done))))))

(deftest test-article-creator--sets-errors
  (let [hpost!-chan (chan)
        comp-1 (rc/article-creator {:hpost! (constantly hpost!-chan)})
        errors {:foo "Bar"}
        http-resp-err
        (assoc-in factories/http-response-schema-error [:body :errors] errors)]
    ;; User fills article spec
    ((get-in (comp-1) [1 :on-article-spec-update]) factories/article-raw-spec)
    ;; Submits
    (let [out-chan ((get-in (comp-1) [1 :on-article-creation-submit]))]
      (async done
             (go
               ;; Request returns an error
               (>! hpost!-chan (http/parse-response http-resp-err))
               (is (= :done (<! out-chan)))
               ;; The error is set on the inner
               (is (= {:foo "Bar"} (get-in (comp-1) [1 :errors])))
               (done))))))