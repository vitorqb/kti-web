(ns kti-web.components.article-viewer-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.models.articles :as articles]
   [kti-web.components.article-viewer :as rc]
   [kti-web.components.utils :as components-utils]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))

(deftest test-article-viewer-inner
  (let [mount rc/article-viewer-inner]
    (testing "Inputs values from article"
      (let [article (assoc factories/article-raw-spec :id 928)
            comp (mount {:view-article article})]
        (are [i k] (and (= (get-in comp [2 (+ 2 i) 0]) components-utils/input)
                        (= (get-in comp [2 (+ 2 i) 1 :value] ::nf) (get article k))
                        (true? (get-in comp [2 (+ 2 i) 1 :disabled])))
          0 :id
          1 :id-captured-reference
          2 :description
          3 :tags
          4 :action-link)))
    (testing "Id input"
      (let [[args saver] (utils/args-saver)
            comp (mount {:selected-view-article-id 999
                         :on-selected-view-article-id-change #(apply saver %&)})]
        (is (= (get-in comp [1 2 1 :value] 999)))
        ((get-in comp [1 2 1 :on-change]) 888)
        (is (= @args [[888]]))))
    (testing "Displays errors"
      (is (= [components-utils/errors-displayer {:status ::a}]
             (-> {:status ::a} mount (get 3)))))))

(deftest test-article-id-selection

  (testing "r-before"
    (let [[f _ _] rc/view-article-id-selection]
      (is (= (f {::a ::b} {} {})
             {::a ::b :loading? true :status {} :view-article nil}))))

  (testing "action"
    (let [[_ f _] rc/view-article-id-selection
          [args saver] (utils/args-saver)]
      (is (= (f {:selected-view-article-id 987} {:get-article! #(do (saver %) ::a)} {})
             ::a))
      (is (= @args [[987]]))))

  (testing "after"
    (let [[_ _ f] rc/view-article-id-selection]
      (is (= (f {::a ::b} nil {} {:error? true :data ::a})
             {::a ::b
              :loading? false
              :status {:errors ::a}
              :view-article nil}))
      (with-redefs [articles/article->raw #(do (assert (= % ::a)) ::b)]
        (is (= (f {::a ::b} nil {} {:data ::a})
               {::a ::b
                :loading? false
                :status {:success-msg "Success!"}
                :view-article ::b}))))))

(deftest test-article-viewer
  (testing "Renders inner"
    (is (= (get ((rc/article-viewer {})) 0) rc/article-viewer-inner)))
  (testing "Passes state"
    (with-redefs [rc/state (atom {::a ::b ::c ::d})]
      (is (= (-> ((rc/article-viewer {})) (get 1) (select-keys [::a ::c]))
             {::a ::b ::c ::d}))))
  (testing "Passes handlers"
    (are [k] (not (nil? (-> ((rc/article-viewer {})) (get-in [1 k]))))
      :on-selected-view-article-id-change
      :on-selected-view-article-id-submit))
  (testing "Changing selected-view-article-id"
    (with-redefs [rc/state (atom {::a ::b :selected-view-article-id ::c})]
      ((get-in ((rc/article-viewer {})) [1 :on-selected-view-article-id-change]) ::d)
      (is (= @rc/state {::a ::b :selected-view-article-id ::d})))))

(deftest test-article-viewer-submit
  (let [[args saver] (utils/args-saver)
        get-chan (chan 1)
        comp1 (rc/article-viewer {:get-article! #(do (apply saver %&) get-chan)})]
    (async
     done
     (go
       ;; User selects an id
       ((get-in (comp1) [1 :on-selected-view-article-id-change]) 987)
       ;; And submits
       (let [resp-chan ((get-in (comp1) [1 :on-selected-view-article-id-submit]))]
         ;; get-article! called with correct args
         (is (= @args [[987]]))
         ;; and we are loading
         (is (true? (get-in (comp1) [1 :loading?])))
         ;; response is sent
         (>! get-chan {:data factories/article})
         (is (= :done (<! resp-chan)))
         ;; no longer loading
         (is (false? (get-in (comp1) [1 :loading?])))
         ;; correct view-article is set
         (is (= (get-in (comp1) [1 :view-article])
                (articles/article->raw factories/article)))
         ;; and success msg
         (is (= (get-in (comp1) [1 :status]) {:success-msg "Success!"}))
         ;; user updates a new id
         ((get-in (comp1) [1 :on-selected-view-article-id-change]) 654)
         (let [resp-chan ((get-in (comp1) [1 :on-selected-view-article-id-submit]))]
           ;; We respond with failure
           (>! get-chan {:error? true :data ::error})
           (is (= :done (<! resp-chan)))
           ;; Which is now set
           (is (= (get-in (comp1) [1 :status]) {:errors ::error}))
           ;; And the view-article is null
           (is (nil? (get-in (comp1) [1 :view-article])))
           (done)))))))
