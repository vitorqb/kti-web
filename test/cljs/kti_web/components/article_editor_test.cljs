(ns kti-web.components.article-editor-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.components.article-editor :as rc]
   [kti-web.models.articles :as articles]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))


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
        get-raw-editted-article #(get-in (comp-1) [1 :raw-editted-article])]
    ;; The article is nil
    (is (nil? (get-raw-editted-article)))
    ;; User selects an article id
    ((get-in (comp-1) [1 :on-article-id-change]) (:id factories/article))
    (let [out-chan ((get-in (comp-1) [1 :on-article-id-submit]))]
      ;; get-article! was called with id as arg
      (is (= @get-article!-args [[(:id factories/article)]]))
      (async done
             (go
               ;; The request returns
               (>! get-article!-chan {:data factories/article})
               (is (= (<! out-chan) :done))
               ;; And the article is now set (as its raw version)
               (is (= (get-raw-editted-article)
                      (articles/article->raw factories/article)))
               ;; And the status is Success!
               (is (= (get-in (comp-1) [1 :status :id-selection])
                      {:success-msg "Success!"}))
               (done))))))

(deftest test-article-editor--sets-error-from-article-id-submit
  (let [error-resp {:error? true :data {::foo ::bar}}
        article-resp {:data factories/article}
        comp-1
        (rc/article-editor
         {:get-article! (constantly (async/to-chan [error-resp article-resp]))})]
    ;; No error is set
    (is (= (get-in (comp-1) [1 :status :id-selection])
           {:errors nil :success-msg nil}))
    (async
     done
     (go
       ;; The user submits once
       (is (= (<! ((get-in (comp-1) [1 :on-article-id-submit]))) :done))
       ;; And the error is sets
       (is (= (get-in (comp-1) [1 :status :id-selection]) {:errors {::foo ::bar}}))
       ;; The user submits again
       (is (= (<! ((get-in (comp-1) [1 :on-article-id-submit]))) :done))
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
        get-success-msg #(get-in (comp-1) [1 :status :edit-article :success-msg])]
    ;; Success message is nil
    (is (nil? (get-success-msg)))
    ;; User selects an article
    ((get-in (comp-1) [1 :on-article-id-change]) (:id factories/article))
    (async
     done
     (go
       ;; And submits the selection
       (is (= (<! ((get-in (comp-1) [1 :on-article-id-submit]))) :done))
       ;; Modifies the selected article
       (let [new-raw-article (assoc (articles/article->raw factories/article)
                                    :tags "a, b, c"
                                    :description "Foo")]
         ((get-in (comp-1) [1 :on-raw-editted-article-change]) new-raw-article)
         ;; And submits the changes
         (let [out-chan ((get-in (comp-1) [1 :on-edit-article-submit]))]
           ;; put should have been called
           (is (= @put-article!-args [[(:id factories/article) new-raw-article]]))
           ;; The request returns
           (>! put-article!-chan {:error? false :data {}})
           (is (= (<! out-chan) :done))
           ;; And the success message is set
           (is (= (get-success-msg) "Success!"))
           (done)))))))

(deftest test-article-editor--sets-error-msg
  (let [error-resp {:error? true :data {::foo ::bar}}
        comp-1 (rc/article-editor {:get-article! #(async/to-chan [factories/article])
                                   :put-article! #(async/to-chan [error-resp])})
        get-error #(get-in (comp-1) [1 :status :edit-article :errors])]
    ;; Error is nil
    (is (nil? (get-error)))
    (async
     done
     (go
       ;; User selects an article
       ((get-in (comp-1) [1 :on-article-id-change]) (:id factories/article))
       (is (= (<! ((get-in (comp-1) [1 :on-article-id-submit]))) :done))
       ;; And submits the edit changes
       (is (= (<! ((get-in (comp-1) [1 :on-edit-article-submit]))) :done))
       ;; Now the error is set
       (is (= (get-error) {::foo ::bar}))
       (done)))))
