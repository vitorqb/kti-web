(ns kti-web.components.article-editor-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.components.article-editor :as rc]
   [kti-web.components.utils :as components-utils]
   [kti-web.models.articles :as articles]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))

(deftest test-article-editor-form
  (let [mount rc/article-editor-form]

    (testing "Disabled id input"
      (let [comp (mount {:raw-editted-article-id 87})]
        ;; Mounts correct input
        (is (= (get-in comp [2 0]) components-utils/input))
        ;; Has correct value
        (is (= (get-in comp [2 1 :value]) 87))
        ;; Is permanently disabled
        (is (= (get-in comp [2 1 :perm-disabled] true)))))

    (testing "id-cap-ref-input"
      (let [[args saver] (utils/args-saver)
            comp (mount {:raw-editted-article factories/article-raw-spec
                         :on-raw-editted-article-change saver})]
        ;; Mounts correct value
        (is (= (get-in comp [3 0]) components-utils/input))
        (is (= (get-in comp [3 1 :value])
               (:id-captured-reference factories/article-raw-spec)))
        ;; Calls on-change
        ((get-in comp [3 1 :on-change]) 312)
        (is (= @args
               [[(assoc factories/article-raw-spec :id-captured-reference 312)]]))))

    (testing "description"
      (let [[args saver] (utils/args-saver)
            comp (mount {:raw-editted-article factories/article-raw-spec
                         :on-raw-editted-article-change saver})]
        ;; Mounts correct value
        (is (= (get-in comp [4 0]) components-utils/input))
        (is (= (get-in comp [4 1 :value])
               (:description factories/article-raw-spec)))
        ;; Calls on-change
        ((get-in comp [4 1 :on-change]) "foo")
        (is (= @args
               [[(assoc factories/article-raw-spec :description "foo")]]))))

    (testing "tags"
      (let [[args saver] (utils/args-saver)
            comp (mount {:raw-editted-article factories/article-raw-spec
                         :on-raw-editted-article-change saver})]
        ;; Mounts correct value
        (is (= (get-in comp [5 0]) components-utils/input))
        (is (= (get-in comp [5 1 :value]) (:tags factories/article-raw-spec)))
        ;; Calls on-change
        ((get-in comp [5 1 :on-change]) "foo, bar, baz")
        (is (= @args
               [[(assoc factories/article-raw-spec :tags "foo, bar, baz")]]))))

    (testing "submitting"
      (let [[args saver] (utils/args-saver)
            comp (mount {:on-edit-article-submit saver})]
        ((get-in comp [1 :on-submit]) (utils/prevent-default-event))
        (is (= @args [[]]))))))

(deftest test-article-selector
  (let [mount rc/article-selector]

    (testing "Two-way binding with selected-article-id"
      (let [[args saver] (utils/args-saver)
            comp (mount {:on-article-id-change saver})]
        ((get-in comp [3 1 :on-change]) (utils/target-value-event "foo"))
        (is (= @args [["foo"]]))))

    (testing "Calls on-article-id-submit on submit"
      (let [[args saver] (utils/args-saver)
            comp (mount {:on-article-id-submit saver})]
        ((get-in comp [1 :on-submit]) (utils/prevent-default-event))
        (is (= @args [[]]))))))

(deftest test-article-editor--inner
  (let [mount rc/article-editor--inner]

    (testing "Passes props to article-editor-form"
      (let [props {::a ::b :raw-editted-article {}}
            comp (mount props)]
        (is (= (get-in comp [3 1]) props))))

    (testing "Shows error-displayer for article editting"
      (let [errors {:errors {::foo ::bar}}
            comp (mount {:status {:edit-article errors}})]
        (is (= (get comp 4)
               [components-utils/errors-displayer {:status errors}]))))

    (testing "Don't show article-editor-form if loading"
      (let [comp (mount {:loading? true :raw-editted-article {::a ::b}})]
        (is (= (get comp 3) [:div "Loading..."]))))

    (testing "Don't show article-editor-form if no raw-editted-article"
      (let [comp (mount {:loading? false :raw-editted-article nil})]
        (is (= (get comp 3) [:div]))))

    (testing "Shows success msg for article edit submit"
      (let [comp (mount {:status {:edit-article {:success-msg ::a}}})]
        (is (= (get comp 5)
               [components-utils/success-message-displayer
                {:status {:success-msg ::a}}]))))

    (testing "Renders article-selector with correct vars"
      (let [props {::a ::b}]
        (is (= (get-in (mount props) [2 1])
               [rc/article-selector props]))))

    (testing "Don't render article-selector if loading"
      (is (= (get (mount {:loading? true}) 2) [:div "Loading..."])))

    (testing "renders errors for article-selector"
      (let [status {:errors {::a ::b}}
            comp (mount {:status {:id-selection status}})]
        (is (= (get-in comp [2 2])
               [components-utils/errors-displayer {:status status}]))))))

(deftest test-article-editor
  (let [mount rc/article-editor]
    (testing "Initializes article-editor--inner with correct args"
      (let [comp ((mount {:get-article! ::get :put-article! ::put}))]
        (are [k v] (= (get-in comp [1 k]) v)
          :selected-article-id nil
          :raw-editted-article nil
          :loading? false
          :get-article! ::get
          :put-article! ::put
          :status {:id-selection {:errors nil :success-msg nil}
                   :edit-article {:errors nil :success-msg nil}})))))

(deftest test-article-editor--sets-article-on-selection
  (let [[get-article!-args save-get-article!-args] (utils/args-saver)
        get-article!-chan (async/timeout 3000)
        get-article! #(do (save-get-article!-args %1) get-article!-chan)
        comp-1 (rc/article-editor {:get-article! get-article!})
        on-article-id-change (get-in (comp-1) [1 :on-article-id-change])
        on-article-id-submit (get-in (comp-1) [1 :on-article-id-submit])
        get-raw-editted-article #(get-in (comp-1) [1 :raw-editted-article])]
    ;; The article is nil
    (is (nil? (get-raw-editted-article)))
    ;; User selects an article id and submits
    (on-article-id-change (:id factories/article))
    (let [out-chan (on-article-id-submit)]
      ;; get-article! was called with id as arg
      (is (= @get-article!-args [[(:id factories/article)]]))
      (async done
             (go
               ;; The request returns
               (>! get-article!-chan {:data factories/article})
               (<! out-chan)
               ;; And the article is now set (as its raw version)
                (is (= (get-raw-editted-article)
                      (-> factories/article articles/article->raw (dissoc :id))))
               ;; And the status is Success!
               (is (= (get-in (comp-1) [1 :status :id-selection])
                      {:success-msg "Success!"}))
               (done))))))

(deftest test-article-editor--sets-error-from-article-id-submit
  (let [error-resp {:error? true :data {::foo ::bar}}
        article-resp {:data factories/article}
        comp-1
        (rc/article-editor
         {:get-article! (constantly (async/to-chan [error-resp article-resp]))})
        on-article-id-submit (get-in (comp-1) [1 :on-article-id-submit])]
    ;; No error is set
    (is (= (get-in (comp-1) [1 :status :id-selection])
           {:errors nil :success-msg nil}))
    (async
     done
     (go
       ;; The user submits once
       (<! (on-article-id-submit))
       ;; And the error is sets
       (is (= (get-in (comp-1) [1 :status :id-selection]) {:errors {::foo ::bar}}))
       ;; The user submits again
       (<! (on-article-id-submit))
       ;; And the success msg is set
       (is (= (get-in (comp-1) [1 :status :id-selection]) {:success-msg "Success!"}))
       (done)))))

(deftest test-article-editor--submits-success
  (let [get-article! #(async/to-chan [{:data factories/article}])
        [put-article!-args save-put-article!-args] (utils/args-saver)
        put-article!-chan (async/timeout 3000)
        put-article! #(do (save-put-article!-args %1 %2) put-article!-chan)
        comp-1 (rc/article-editor
                {:get-article! get-article!
                 :put-article! put-article!})
        on-article-id-change (get-in (comp-1) [1 :on-article-id-change])
        on-article-id-submit (get-in (comp-1) [1 :on-article-id-submit])
        on-raw-editted-article-change (get-in (comp-1) [1 :on-raw-editted-article-change])
        on-edit-article-submit (get-in (comp-1) [1 :on-edit-article-submit])
        get-success-msg #(get-in (comp-1) [1 :status :edit-article :success-msg])]
    ;; Success message is nil
    (is (nil? (get-success-msg)))
    ;; User selects an article
    (on-article-id-change (:id factories/article))
    (async
     done
     (go
       ;; And submits the selection
       (<! (on-article-id-submit))
       ;; Modifies the selected article
       (let [new-raw-article (assoc (articles/article->raw factories/article)
                                    :tags "a, b, c"
                                    :description "Foo")]
         (on-raw-editted-article-change new-raw-article)
         ;; And submits the changes
         (let [out-chan (on-edit-article-submit)]
           ;; put should have been called
           (is (= @put-article!-args
                  [[(:id factories/article)
                    (articles/serialize-article-spec new-raw-article)]]))
           ;; The request returns
           (>! put-article!-chan {:error? false :data {}})
           (<! out-chan)
           ;; And the success message is set
           (is (= (get-success-msg) "Success!"))
           (done)))))))

(deftest test-article-editor--sets-error-msg
  (let [error-resp {:error? true :data {::foo ::bar}}
        comp-1 (rc/article-editor {:get-article! #(async/to-chan [factories/article])
                                   :put-article! #(async/to-chan [error-resp])})
        on-article-id-change (get-in (comp-1) [1 :on-article-id-change])
        on-article-id-submit (get-in (comp-1) [1 :on-article-id-submit])
        on-edit-article-submit (get-in (comp-1) [1 :on-edit-article-submit])
        get-error #(get-in (comp-1) [1 :status :edit-article :errors])]
    ;; Error is nil
    (is (nil? (get-error)))
    (async
     done
     (go
       ;; User selects an article
       (on-article-id-change (:id factories/article))
       (<! (on-article-id-submit))
       ;; And submits the edit changes
       (<! (on-edit-article-submit))
       ;; Now the error is set
       (is (= (get-error) {::foo ::bar}))
       (done)))))
