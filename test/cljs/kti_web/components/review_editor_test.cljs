(ns kti-web.components.review-editor-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.models.reviews :as reviews-models]
   [kti-web.components.review-editor :as rc]
   [kti-web.components.review-selector :refer [review-selector]]
   [kti-web.components.utils :as components-utils]
   [kti-web.models.reviews :as models-reviews]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))

(deftest test-review-editor-form
  (let [mount rc/review-editor-form
        mount-get-in #(-> %1 mount (get-in %2))
        get-id-input-props #(get-in % [2 1])
        get-id-article-input-props #(get-in % [3 1])
        get-feedback-text-input-props #(get-in % [4 1])
        get-status-input-props #(get-in % [5 1])]
    (testing "On submit"
      (let [[args fun] (utils/args-saver)
            comp (mount {:on-edited-review-submit fun})]
        ((get-in comp [1 :on-submit]) (utils/prevent-default-event))
        (is (= @args [[]]))))
    (testing "Id input value"
      (is (= (-> {:edited-review {:id 9}} mount get-id-input-props :value) 9)))
    (testing "Id disabled"
      (is (true? (-> {} mount get-id-input-props :disabled))))
    (testing "Article id value"
      (is (= (mount-get-in {:edited-review {:id-article 99}} [3 1 :value]) 99)))
    (testing "Article id on-change"
      (let [[args fun] (utils/args-saver)
            comp (mount {:edited-review {::a ::b} :on-edited-review-change fun})]
        ((-> comp get-id-article-input-props :on-change) 81)
        (is (= @args [[{::a ::b :id-article 81}]]))))
    (testing "Feedback text value"
      (is (= (mount-get-in {:edited-review {:feedback-text "foo"}} [4 1 :value])
             "foo")))
    (testing "Feedback Text on-change"
      (let [[args fun] (utils/args-saver)
            comp (mount {:edited-review {::a ::b} :on-edited-review-change fun})]
        ((-> comp get-feedback-text-input-props :on-change) "bar")
        (is (= @args [[{::a ::b :feedback-text "bar"}]]))))
    (testing "Status value"
      (is (= (mount-get-in {:edited-review {:status :in-progress}} [5 1 :value])
             :in-progress)))
    (testing "Status on-change"
      (let [[args fun] (utils/args-saver)
            comp (mount {:edited-review {::a ::b} :on-edited-review-change fun})]
        ((-> comp get-status-input-props :on-change) :completed)
        (is (= @args [[{::a ::b :status :completed}]]))))
    (testing "Inputs are disabled if loading?"
      (are [i] (true? (-> {:loading? true} mount (get-in [i 1]) :disabled))
        2 3 4 5))))

(deftest test-review-editor--inner
  (let [mount rc/review-editor--inner
        get-review-selector #(get % 2)
        get-form #(get % 3)
        get-err-comp #(get % 4)
        get-success-msg-comp #(get % 5)]
    (testing "Mounts form with props"
      (is (= (-> {:edited-review {::a ::b}}
                 mount
                 get-form)
             [rc/review-editor-form {:edited-review {::a ::b}}])))
    (testing "Hides form if no edited-review"
      (is (= (-> {} mount get-form) nil)))
    (testing "Renders error-msg component"
      (is (= (-> {::a ::b} mount get-err-comp)
             [components-utils/errors-displayer {::a ::b}])))
    (testing "Renders success-msg component"
      (is (= (-> {::a ::b} mount get-success-msg-comp)
             [components-utils/success-message-displayer {::a ::b}])))
    (testing "Renders review-selector"
      (is (= (-> {::a ::b} mount get-review-selector)
             [review-selector {::a ::b}])))))

(deftest test-reduce-on-selection-submit
  (let [reduce (:r-after rc/review-selection-submit)
        review factories/review]
    (testing "Set's status on success"
      (let [response {:data review}]
        (is (= (reduce {} {} response)
               {:loading? false
                :selection-status {:success-msg "SUCCESS"}
                :edited-review (reviews-models/review->raw-spec review)}))))
    (testing "Set's error on failure"
      (let [response {:error? true :data {::a ::b}}]
        (is (= (reduce {} {} response)
               {:loading? false
                :selection-status {:errors {::a ::b}}
                :edited-review nil}))))))

(deftest test-reduce-on-edited-review-submit
  (let [reduce (:r-after rc/edited-review-submit)
        review factories/review]
    (testing "Set's status on success"
      (let [response {:data review}]
        (is (= (reduce {} {} response)
               {:loading? false
                :status {:success-msg "SUCCESS"}
                :edited-review (reviews-models/review->raw-spec review)}))))
    (testing "Set's status on error"
      (let [response {:error? true :data {::a ::b}}]
        (is (= (reduce {:edited-review ::a} {} response)
               {:loading? false
                :status {:errors {::a ::b}}
                :edited-review ::a}))))))

(deftest test-review-editor
  (let [mount rc/review-editor]
    (testing "Renders inner"
      (is (= (-> {} mount (apply [{}]) (get-in [0]))
             rc/review-editor--inner)))
    (testing "Inner initial inputs"
      (let [comp ((mount {:put-review! ::put}) {})]
        (are [k v] (= (get-in comp [1 k]) v)
          :selection-status {}
          :status {}
          :edited-review nil
          :select-review-id nil
          :loading? false)))))

(deftest test-review-editor--on-selection-okay
  (let [get-chan (chan)
        [args fun] (utils/args-saver)
        comp1 (rc/review-editor {:get-review! #(do (fun %&) get-chan)})]
    ;; User selects an id
    ((get-in (comp1) [1 :on-selected-review-id-change]) 99)
    ;; And submits the selection
    (let [resp-chan ((get-in (comp1) [1 :on-review-selection-submit]))]
      (async done
             (go
               ;; The app is loading
               (is (true? (get-in (comp1) [1 :loading?])))
               ;; The answer is given
               (>! get-chan {:data (reviews-models/server-resp->review
                                    factories/review-server-resp)})
               (is (= :done (<! resp-chan)))
               ;; No longer loading
               (is (false? (get-in (comp1) [1 :loading?])))
               ;; and we have the edited-review set
               (is (= (get-in (comp1) [1 :edited-review])
                      (-> factories/review-server-resp
                          reviews-models/server-resp->review
                          reviews-models/review->raw-spec)))
               (done))))))

(deftest test-review-editor--on-selection-error
  (let [get-chan (chan)
        [args fun] (utils/args-saver)
        comp1 (rc/review-editor {:get-review! #(do (fun %&) get-chan)})]
    (let [resp-chan ((get-in (comp1) [1 :on-review-selection-submit]))]
      (async done
             (go
               ;; An errored response is given
               (>! get-chan {:error? true :data {::a ::b}})
               (is (= :done (<! resp-chan)))
               ;; and is set on the props
               (is (= (get-in (comp1) [1 :selection-status]) {:errors {::a ::b}}))
               (done))))))

(deftest test-review-editor--on-edited-article-submit-okay
  (let [get-chan (async/to-chan [{:data factories/review-server-resp}])
        put-chan (chan)
        [args fun] (utils/args-saver)
        comp1 (rc/review-editor {:get-review! (constantly get-chan)
                                 :put-review! #(do (apply fun %&) put-chan)})
        updated-review-raw-spec {:id-article 99
                                 :status "completed"
                                 :feedback-text "It sucks!"}]
    (async
     done
     (go
       ;; User selects an id
       ((get-in (comp1) [1 :on-selected-review-id-change])
        (:id factories/review-server-resp))
       ;; And submits the selection
       (let [selection-resp-chan ((get-in (comp1) [1 :on-review-selection-submit]))]
         (is (= :done (<! selection-resp-chan)))
         ;; It edits the review
         ((get-in (comp1) [1 :on-edited-review-change]) updated-review-raw-spec)
         ;; And submits
         (let [edit-resp-chan ((get-in (comp1) [1 :on-edited-review-submit]))]
           ;; It is loading
           (is (true? (get-in (comp1) [1 :loading?])))
           ;; It receives the response
           (>! put-chan {:data factories/review})
           (is (= :done (<! edit-resp-chan)))
           ;; And is no longer loading
           (is (false? (get-in (comp1) [1 :loading?])))
           ;; And put was called correctly
           (is (= @args
                  [[(:id factories/review-server-resp)
                    (reviews-models/raw-spec->spec updated-review-raw-spec)]]))
           ;; And the response is set as edited response
           (is (= (get-in (comp1) [1 :edited-review])
                  (reviews-models/review->raw-spec factories/review)))
           ;; And status is success
           (is (= (get-in (comp1) [1 :status]) {:success-msg "SUCCESS"}))
           (done)))))))

(deftest test-review-editor--on-edited-article-submit-error
  (let [get-chan (async/to-chan [{:data factories/review-server-resp}])
        put-chan (async/to-chan [{:error? true :data {::a ::b}}])
        [args fun] (utils/args-saver)
        comp1 (rc/review-editor {:get-review! (constantly get-chan)
                                 :put-review! (constantly put-chan)})]
    (async
     done
     (go
       ;; User submits the editting
       (is (= :done (<! ((get-in (comp1) [1 :on-edited-review-submit])))))
       ;; And we are not loading anymore
       (is (false? (get-in (comp1) [1 :loading?])))
       ;; And the error is set
       (is (= (get-in (comp1) [1 :status]) {:errors {::a ::b}}))
       (done)))))
      
      
