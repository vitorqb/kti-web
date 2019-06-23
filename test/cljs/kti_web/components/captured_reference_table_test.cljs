(ns kti-web.components.captured-reference-table-test
  (:require [cljs.core.async :refer [<! >! chan go] :as async]
            [cljs.test :refer-macros [async deftest is testing]]
            [kti-web.components.captured-reference-table :as rc]
            [kti-web.http :as http]
            [kti-web.test-factories :as factories]
            [kti-web.test-utils :as utils]
            [kti-web.components.rtable :refer [rtable]]))

(deftest test-delete-captured-ref-action-button
  (let [[on-modal-display-for-deletion-args on-modal-display-for-deletion-fun]
        (utils/args-saver)
        row-captured-ref {:id ::row-captured-ref-id}
        props {:on-modal-display-for-deletion on-modal-display-for-deletion-fun
               :row-captured-ref row-captured-ref}
        comp (rc/delete-captured-ref-action-button props)]
    (is (= (get comp 0) :button))
    (is (= (get-in comp [1 :className]) "delete-button"))
    ((get-in comp [1 :on-click]) ::foo)
    (is (= @on-modal-display-for-deletion-args) [[::row-captured-ref-id]])))

(deftest test-make-action-buttons
  (let [props {:on-modal-display-for-deletion ::on-modal-display-for-deletion}
        button-props {:on-modal-display-for-deletion ::on-modal-display-for-deletion
                      :row-captured-ref ::row}
        comp (rc/make-action-buttons props ::row)]
    (is (= (get comp 0) :div))
    (is (= (get comp 1)
           [rc/delete-captured-ref-action-button button-props]))))

(deftest test-make-columns
  (testing "Constructs action-buttons with make-action-buttons"
    (with-redefs [rc/make-action-buttons (fn [props row]
                                           (is (= props ::props))
                                           (is (= row ::row))
                                           ::action-buttons)]
      (let [accessor (-> ::props rc/make-columns (get-in [6 :accessor]))
            cell-fn  (-> ::props rc/make-columns (get-in [6 :cell-fn]))]
        (is (= (accessor ::row) ::row))
        (is (= (cell-fn  ::row) ::action-buttons))))))

(deftest test-captured-refs-table-inner
  (let [mount rc/captured-refs-table-inner
        get-refresh-button #(get % 2)
        get-table #(get % 3)]
    (testing "Calls fn-refresh!"
      (let [[args fun] (utils/args-saver)
            comp (mount {:fn-refresh! fun})]
        ((-> comp get-refresh-button (get-in [1 :on-click])) ::foo)
        (is (= @args [[]]))))
    (testing "Mounts table..."
      (let [c (mount {})
            table (get-table c)]
        (is (= (get table 0) rtable))
        (is (not (nil? (get-in table [1 :columns]))))
        (is (not (nil? (get-in table [1 :data]))))))))

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
      (is (= (-> (comp1) (get 1) (dissoc :fn-refresh! :on-modal-display-for-deletion))
             {:loading? true :refs nil :status {}}))
      (async
       done
       (go
         ;; Then the response arrives and finished
         (>! get-chan {:data [factories/captured-ref]})
         (is (= (<! done-chan) 1))
         ;; And the new state is set
         (is (= (-> (comp1) (get 1) (dissoc :fn-refresh! :on-modal-display-for-deletion))
                {:loading? false
                 :status {:success-msg "Success!"}
                 :refs [factories/captured-ref]}))
         ;; The user refreshes
         ((get-in (comp1) [1 :fn-refresh!]))
         ;; We are loading again
         (is (= (-> (comp1) (get 1) (dissoc :fn-refresh! :on-modal-display-for-deletion))
                {:loading? true :status {} :refs nil}))
         ;; An error is returned
         (>! get-chan {:error? true :data {::some "error"}})
         ;; It ends
         (is (= (<! done-chan) 1))
         ;; And we see the error there
         (is (= (-> (comp1) (get 1) (dissoc :fn-refresh! :on-modal-display-for-deletion))
                {:loading? false
                 :status {:errors {::some "error"}}
                 :refs nil}))
         (done))))))

;; (deftest test-captured-refs-table--integration--delete-modal
;;   (let []))
