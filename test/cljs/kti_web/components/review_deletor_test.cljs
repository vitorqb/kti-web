(ns kti-web.components.review-deletor-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.models.reviews :as reviews-models]
   [kti-web.components.review-deletor :as rc]
   [kti-web.components.review-selector :refer [review-selector]]
   [kti-web.components.utils :as components-utils]
   [kti-web.models.reviews :as models-reviews]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))

(deftest test-review-displayer
  (let [mount rc/review-displayer
        mount-get-in #(-> %1 mount (get-in %2))]
    (testing "Displays all inputs with values"
      (let [review factories/review props {:selected-review review}]
        (doseq [[i k] (map vector (map #(+ % 2) (range)) rc/inputs-keys)]
          (is (= (mount-get-in props [i 0]) (get-in rc/inputs [k 0])))
          (is (= (mount-get-in props [i 1 :value]) (get review k)))
          (is (true? (mount-get-in props [i 1 :disabled]))))))))

(deftest test-delete-button
  (let [mount rc/delete-button]
    (testing "Calls on-delete-review-submit on click"
      (let [[args fun] (utils/args-saver) comp
            (comp (mount {:on-delete-review-submit fun}))]
        ((get-in comp [1 :on-click]) ::arg)
        (is (= @args [[]]))))))

(deftest test-review-deletor-inner
  (let [mount rc/review-deletor-inner
        mount-get-in #(-> %1 mount (get-in %2))
        p-review-displayer 4
        p-delete-button 5]
    (testing "Renders component to select review"
      (is (= (mount-get-in {} [3 0]) review-selector))
      (are [k] (= (mount-get-in {k ::a} [3 1 k]) ::a)
        :selected-review-id :selection-status :on-review-selection-submit
        :on-selected-review-id-change))
    (testing "Dont' render review-displayer component if no selected-review"
      (is (nil? (mount-get-in {} [p-review-displayer]))))
    (testing "Render's review-displayer component if selected-review"
      (let [props {:selected-review factories/review}]
        (is (= (mount-get-in props [p-review-displayer 0]) rc/review-displayer))
        (is (= (mount-get-in (assoc props :selected-review factories/review)
                             [p-review-displayer 1 :selected-review])
               factories/review))))
    (testing "Dont' render delete-button if no selected-review"
      (is (nil? (mount-get-in {} [p-delete-button]))))
    (testing "Render delete-button if selected-review"
      (let [props {:selected-review factories/review :on-delete-review-submit ::a}]
        (is (= (mount-get-in props [p-delete-button 0]) rc/delete-button))
        (is (= (mount-get-in props [p-delete-button 1 :on-delete-review-submit])
               ::a))))
    (testing "Render success-msg component"
      (is (= (mount-get-in {:status ::a} [6])
             [components-utils/success-message-displayer {:status ::a}])))
    (testing "Render errors-displayer component"
      (is (= (mount-get-in {:status ::a} [7])
             [components-utils/errors-displayer {:status ::a}])))))

(deftest test-reduce-before-review-selection-submit
  (let [reduce (rc/review-selection-submit :r-before)]
    (is (= (reduce {:deleted-reviews ::a} nil)
           {:deleted-reviews ::a
            :loading? true
            :status {}
            :selection-status {}
            :selected-review nil}))))

(deftest test-reduce-on-review-selection-submit
  (let [reduce (rc/review-selection-submit :r-after)]
    (let [state {:deleted-reviews ::a}
          resp {:error? false :data ::b}]
      (is (= (reduce state {} resp)
             {:deleted-reviews ::a
              :loading? false
              :status {}
              :selection-status {:success-msg "Success"}
              :selected-review ::b})))
    (let [state {:deleted-reviews ::a}
          resp {:error? true :data ::b}]
      (is (= (reduce state {} resp)
             {:deleted-reviews ::a
              :loading? false
              :status {}
              :selection-status {:errors ::b}
              :selected-review nil})))))

(deftest test-reduce-before-delete-review-submit
  (let [reduce (:r-before rc/delete-review-submit)]
    (is (= (reduce {} {}) {:loading? true :status {}}))))

(deftest test-reduce-on-delete-review-submit
  (let [reduce (:r-after rc/delete-review-submit)]
    (let [state {:deleted-reviews [] :selected-review ::a}
          resp {:data {}}]
      (is (= (reduce state nil resp)
             {:loading? false
              :status {:success-msg "Deleted!"}
              :selected-review nil
              :deleted-reviews [::a]})))
    (let [state {:deleted-reviews [] :selected-review ::a}
          resp {:error? true :data ::b}]
      (is (= (reduce state nil resp)
             {:loading? false
              :status {:errors ::b}
              :selected-review ::a
              :deleted-reviews []})))))

(deftest test-review-deletor
  (let [mount rc/review-deletor]
    (testing "Initializes inner component with all props"
      (is (= (get-in ((mount {})) [0]) rc/review-deletor-inner))
      (are [k] (-> {} mount (apply []) (get-in [1 k] ::nf) (= ::nf) not)
        :on-selected-review-id-change :on-review-selection-submit
        :on-delete-review-submit)
      (are [k v] (= (get-in ((mount {})) [1 k] ::not-found) v)
        :selected-review-id nil
        :status {}
        :selection-status {}
        :selected-review nil
        :deleted-reviews []
        :loading? false))))

(deftest test-review-deletor--selection-success
  (let [[args saver] (utils/args-saver)
        get-chan (async/chan)
        comp1 (rc/review-deletor {:get-review! #(do (apply saver %&) get-chan)})]
    ;; The user selects an id
    ((get-in (comp1) [1 :on-selected-review-id-change]) 76)
    ;; And submits
    (let [resp-chan ((get-in (comp1) [1 :on-review-selection-submit]))]
      (async done (go ;; We are loading
                    (is (true? (get-in (comp1) [1 :loading?])))
                    ;; The get returns
                    (>! get-chan {:data factories/review})
                    (is (= :done (<! resp-chan)))
                    ;; And the get was called with correct args
                    (is (= @args [[76]]))
                    ;; No more loading
                    (is (false? (get-in (comp1) [1 :loading?])))
                    ;; And the review is now set
                    (is (= (get-in (comp1) [1 :selected-review]) factories/review))
                    (done))))))

(deftest test-review-deletor--selection-error
  (let [error-resp {:error? true :data ::a}
        comp1 (rc/review-deletor {:get-review! #(async/to-chan [error-resp])})]
    ;; The user selects an id
    ((get-in (comp1) [1 :on-selected-review-id-change]) 76)
    ;; And submits
    (let [resp-chan ((get-in (comp1) [1 :on-review-selection-submit]))]
      (async done (go (is (= :done (<! resp-chan)))
                      ;; And the errors is now set
                      (is (= (get-in (comp1) [1 :selection-status]) {:errors ::a}))
                      (done))))))

(deftest test-review-deletor--delete-submit-success
  (let [[args saver] (utils/args-saver)
        delete-chan (async/chan)
        review factories/review
        comp1 (rc/review-deletor {:delete-review! #(do (apply saver %&) delete-chan)
                                  :get-review! #(async/to-chan [{:data review}])})]
    (async
     done
     (go
       ;; The user selects an id
       ((get-in (comp1) [1 :on-selected-review-id-change]) 76)
       ;; And submits the selection
       (let [get-resp-chan ((get-in (comp1) [1 :on-review-selection-submit]))]
         (is (= :done (<! get-resp-chan)))
         ;; It submits the deletion
         (let [resp-chan ((get-in (comp1) [1 :on-delete-review-submit]))]
           ;; And it is now loading
           (is (true? (get-in (comp1) [1 :loading?])))
           ;; The delete was called
           (is (= @args [[(:id review)]]))
           ;; The response arrives
           (>! delete-chan {:error? false})
           (is (= :done (<! resp-chan)))
           ;; No longer loading
           (is (false? (get-in (comp1) [1 :loading?])))
           ;; Success msg
           (is (= (get-in (comp1) [1 :status]) {:success-msg "Deleted!"}))
           ;; Deleted review is stored
           (is (= (get-in (comp1) [1 :deleted-reviews]) [review]))
           ;; But no longer selected
           (is (nil? (get-in (comp1) [1 :selected-review] ::nf)))
           (done)))))))

(deftest test-review-deletor--delete-submit-errors
  (let [[args saver] (utils/args-saver)
        resp-error {:error? true :data ::a}
        comp1 (rc/review-deletor {:delete-review! #(async/to-chan [resp-error])})]
    (async
     done
     (go
       ;; the user  submits the deletion
       (let [resp-chan ((get-in (comp1) [1 :on-delete-review-submit]))]
         (is (= :done (<! resp-chan)))
         ;; Error is set
         (is (= (get-in (comp1) [1 :status]) {:errors ::a}))
         ;; Deleted review is not stored
         (is (= (get-in (comp1) [1 :deleted-reviews]) []))
         (done))))))
