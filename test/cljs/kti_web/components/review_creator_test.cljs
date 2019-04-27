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
