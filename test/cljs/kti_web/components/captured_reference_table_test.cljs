(ns kti-web.components.captured-reference-table-test
  (:require [cljs.core.async :refer [<! >! chan go] :as async]
            [cljs.test :refer-macros [async deftest is testing]]
            [kti-web.components.captured-reference-table :as rc]
            [kti-web.http :as http]
            [kti-web.test-factories :as factories]
            [kti-web.test-utils :as utils]))

(deftest test-make-thead
  (let [make-thead #'kti-web.components.captured-reference-table/make-thead]
    (is (= (make-thead [{:head-text "foo"} {:head-text "bar"}])
           [:thead [:tr [:th {:key "foo"} "foo"] [:th {:key "bar"} "bar"]]]))))

(deftest test-make-tbody
  (let [make-tbody #'kti-web.components.captured-reference-table/make-tbody]
    (is (= (make-tbody [{:fn-get ::foo} {:fn-get ::bar}]
                       [{::foo "a" ::bar "b"} {::foo "c" ::bar "d"}])
           [:tbody
            [:tr [:td "a"] [:td "b"]]
            [:tr [:td "c"] [:td "d"]]]))))

(deftest test-captured-refs-table-inner
  (let [mount rc/captured-refs-table-inner
        make-thead #'kti-web.components.captured-reference-table/make-thead
        make-tbody #'kti-web.components.captured-reference-table/make-tbody
        get-refresh-button #(get % 2)
        get-thead #(get-in % [3 1])
        get-tbody #(get-in % [3 2])]
    (testing "Calls fn-refresh!"
      (let [[args fun] (utils/args-saver)
            comp (mount {:fn-refresh! fun})]
        ((-> comp get-refresh-button (get-in [1 :on-click])) ::foo)
        (is (= @args [[]]))))
    (testing "Mounts thead..."
      (let [comp (mount)]
        (is (= (get-thead comp) (make-thead rc/columns)))))
    (testing "Mounts tbody..."
      (let [refs [factories/captured-ref] comp (mount {:refs refs})]
        (is (= (get-tbody comp) (make-tbody rc/columns refs)))))))

(deftest test-refresh
  (testing "r-before"
    (let [r-before (:r-before rc/refresh)]
      (is (= (r-before {::a ::b} {}) {::a ::b :loading? true :refs nil :status {}}))))
  (testing "r-after"
    (let [r-after (:r-after rc/refresh)]
      (is (= (r-after {::a ::b} {} {:error? true :data ::c})
             {::a ::b :loading? false :status {:errors ::c} :refs nil}))
      (is (= (r-after {::a ::b} {} {:data ::c})
             {::a ::b :loading? false :status {:success-msg "Success!"} :refs ::c})))))

(deftest test-captured-refs-table--integration
  (let [mount rc/captured-refs-table
        get-chan (async/timeout 2000)
        done-chan (async/timeout 2000)]
    (let [comp1 (mount {:get! (constantly get-chan) :c-done done-chan})]
      ;; First state must be while loading
      (is (= (get (comp1) 0) rc/captured-refs-table-inner))
      (is (= (-> (comp1) (get 1) (dissoc :fn-refresh!))
             {:loading? true :refs nil :status {}}))
      (async
       done
       (go
         ;; Then the response arrives and finished
         (>! get-chan {:data [factories/captured-ref]})
         (is (= (<! done-chan) 1))
         ;; And the new state is set
         (is (= (-> (comp1) (get 1) (dissoc :fn-refresh!))
                {:loading? false
                 :status {:success-msg "Success!"}
                 :refs [factories/captured-ref]}))
         ;; The user refreshes
         ((get-in (comp1) [1 :fn-refresh!]))
         ;; We are loading again
         (is (= (-> (comp1) (get 1) (dissoc :fn-refresh!))
                {:loading? true :status {} :refs nil}))
         ;; An error is returned
         (>! get-chan {:error? true :data {::some "error"}})
         ;; It ends
         (is (= (<! done-chan) 1))
         ;; And we see the error there
         (is (= (-> (comp1) (get 1) (dissoc :fn-refresh!))
                {:loading? false
                 :status {:errors {::some "error"}}
                 :refs nil}))
         (done))))))
