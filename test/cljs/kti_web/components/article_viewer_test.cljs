(ns kti-web.components.article-viewer-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.event-handlers :as event-handlers]
   [kti-web.models.articles :as articles]
   [kti-web.components.article-viewer :as rc]
   [kti-web.components.utils :as components-utils]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))

(deftest test-handle-on-selected-view-article-id-change
  (let [state (atom {:selected-view-article-id 999})
        handler (rc/handle-on-selected-view-article-id-change state {})]
    (handler 888)
    (is (= (:selected-view-article-id @state) 888))))

(deftest test-handle-on-selected-view-article-id-submit
  (let [[args saver] (utils/args-saver)]
    (with-redefs [event-handlers/handle!-vec #(do (apply saver %&) (constantly nil))]
      (let [state ::state
            props ::props
            handler (rc/handle-on-selected-view-article-id-submit state props)]
        (handler)
        (is (= @args [[nil ::state ::props rc/view-article-id-selection]]))))))

(deftest test-handle-on-show-article
  (let [[args saver] (utils/args-saver)]
    ;; We just test the two other handlers are called
    (with-redefs [rc/handle-on-selected-view-article-id-change
                  (fn [s p] (fn [e] (saver :change s p e)))
                  rc/handle-on-selected-view-article-id-submit
                  (fn [s p] (fn [e] (saver :submit s p e)))]
      (let [handler (rc/handle-on-show-article ::state ::props)]
        (handler ::event)
        (is (= @args
               [[:change ::state ::props ::event]
                [:submit ::state ::props nil]]))))))

(deftest test-wrap-disable-input
  (let [wrap-disable-input #'rc/wrap-disable-input
        comp :div
        props {}]
    (is (= [:div {:disabled true :className "invalid-input"}]
           (wrap-disable-input [comp props])))))

(deftest test-article-viewer-inner
  (let [mount rc/article-viewer-inner]

    (testing "Inputs values from article"
      (let [article (assoc factories/article-raw-spec :id 928)
            comp (mount {:view-article article})]
        (are [i k component]
            (and (= (get-in comp [2 (+ 2 i) 0]) component)
                 (= (get-in comp [2 (+ 2 i) 1 :value] ::nf) (get article k))
                 (true? (get-in comp [2 (+ 2 i) 1 :disabled])))
          0 :id components-utils/input
          1 :id-captured-reference components-utils/input
          2 :description components-utils/textarea
          3 :tags components-utils/input
          4 :action-link components-utils/input)))

    (testing "Id input"
      (let [[args saver] (utils/args-saver)
            comp (mount {:selected-view-article-id 999
                         :on-selected-view-article-id-change saver})]
        (is (= (get-in comp [1 2 1 :value] 999)))
        (get-in comp [1 2 1 :value] 999)
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
    (with-redefs [rc/handle-on-selected-view-article-id-change
                  (constantly ::on-selected-view-article-id-change)
                  rc/handle-on-selected-view-article-id-submit
                  (constantly ::on-selected-view-article-id-submit)]
      (let [comp ((rc/article-viewer {}))]
        (are [k v] (= (get-in comp [1 k]) v)
          :on-selected-view-article-id-change ::on-selected-view-article-id-change
          :on-selected-view-article-id-submit ::on-selected-view-article-id-submit))))

  (testing "Changing selected-view-article-id"
    (with-redefs [rc/state (atom {::a ::b :selected-view-article-id ::c})]
      (let [comp1 (rc/article-viewer {})
            on-selected-view-article-id-change
            (get-in (comp1) [1 :on-selected-view-article-id-change]) ]
        (on-selected-view-article-id-change ::d)
        (is (= @rc/state {::a ::b :selected-view-article-id ::d}))))))

(deftest test-article-viewer-submit
  (let [[args saver] (utils/args-saver)
        get-chan (chan 1)
        comp1 (rc/article-viewer {:get-article! #(do (apply saver %&) get-chan)})
        on-selected-view-article-id-change
        (get-in (comp1) [1 :on-selected-view-article-id-change])
        on-selected-view-article-id-submit
        (get-in (comp1) [1 :on-selected-view-article-id-submit])]
    (async
     done
     (go
       ;; User selects an id
       (on-selected-view-article-id-change 987)
       ;; And submits
       (let [resp-chan (on-selected-view-article-id-submit)]
         ;; get-article! called with correct args
         (is (= @args [[987]]))
         ;; and we are loading
         (is (true? (get-in (comp1) [1 :loading?])))
         ;; response is sent
         (>! get-chan {:data factories/article})
         (<! resp-chan)
         ;; no longer loading
         (is (false? (get-in (comp1) [1 :loading?])))
         ;; correct view-article is set
         (is (= (get-in (comp1) [1 :view-article])
                (articles/article->raw factories/article)))
         ;; and success msg
         (is (= (get-in (comp1) [1 :status]) {:success-msg "Success!"}))
         ;; user updates a new id
         (on-selected-view-article-id-change 654)
         (let [resp-chan (on-selected-view-article-id-submit)]
           ;; We respond with failure
           (>! get-chan {:error? true :data ::error})
           (<! resp-chan)
           ;; Which is now set
           (is (= (get-in (comp1) [1 :status]) {:errors ::error}))
           ;; And the view-article is null
           (is (nil? (get-in (comp1) [1 :view-article])))
           (done)))))))
