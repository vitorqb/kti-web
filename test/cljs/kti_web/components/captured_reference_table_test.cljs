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

(deftest test-props->rtable-props
  (testing "Base"
    (with-redefs [rc/make-columns (constantly ::make-columns-response)
                  rc/refs->data   (constantly ::refs->data-response)
                  rc/handler-wrapper-avoid-useless-fetching
                  (constantly ::wrapper-response)]
      (let [refs [{:created-at 2} {:created-at 1}]
            page 2
            pageSize 10
            pages 4
            fn-refresh! (constantly ::fn-refresh-response)
            props {:refs refs
                   :fn-refresh! fn-refresh!
                   :table-state {:page page :pageSize pageSize :pages pages}}]
        (is (= (rc/props->rtable-props props)
               {:data ::refs->data-response
                :page page
                :pageSize pageSize
                :on-fetch-data ::wrapper-response
                :columns ::make-columns-response
                :manual true
                :pages pages}))))))

(deftest test-captured-refs-table-inner
  (let [mount rc/captured-refs-table-inner
        get-refresh-button #(get % 2)
        get-table #(get % 3)]
    (testing "Calls fn-refresh!"
      (let [[args fun] (utils/args-saver)
            table-state {:page 1 :pageSize 2}
            comp (mount {:fn-refresh! fun :table-state table-state})]
        ((-> comp get-refresh-button (get-in [1 :on-click])) ::foo)
        (is (= @args [[]]))))
    (testing "Mounts table..."
      (with-redefs [rc/props->rtable-props (constantly ::rtable-props)]
        (let [c (mount {})
              table (get-table c)]
          (is (= (get table 0) rtable))
          (is (= (get table 1) ::rtable-props)))))))

(deftest test-refresh-paginated
  (let [[r-before action r-after] rc/refresh-paginated-vec]
    (testing "r-before"
      (let [state {}
            event {:page 1 :pageSize 2}]
        (is (= (r-before state nil event)
               {:table-state {:loading true :page 1 :pageSize 2}
                :refs nil
                :status nil}))))
    (testing "action"
      (let [get-paginated-captured-references! identity
            state-page 2
            state-page-size 100
            state {:table-state {:page state-page :pageSize state-page-size}}
            event-page 3
            event-page-size 200
            event {:page event-page :pageSize event-page-size}
            extra-args {:get-paginated-captured-references!
                        get-paginated-captured-references!}]
        (is (= (action state extra-args event)
               {:page (inc event-page) :page-size event-page-size}))))
    (testing "r-after"
      (is (= (r-after {::a ::b} {} nil {:error? true :data ::c})
             {::a ::b
              :table-state {:loading false}
              :status {:errors ::c}}))
      (let [resp-data {:page 1 :page-size 2 :total-items 3 :items [4 5]}]
        (is (= (r-after {} {} nil {:error? false :data resp-data})
               {:refs [4 5]
                :table-state {:pages 2 :loading false}}))))))

(deftest test-handler-wrapper-avoid-useless-fetching
  (testing "page/pageSize changes"
    (let [props {:table-state {:page 1 :pageSize 2}}
          event {:page 2 :pageSize 3}
          handler (constantly ::handler-resp)
          wrapped-handler (rc/handler-wrapper-avoid-useless-fetching handler props)]
      (is (= (wrapped-handler event) ::handler-resp))))
  (testing "no change"
    (let [event {:page 1 :pageSize 2}
          props {:table-state event}
          handler (constantly ::handler-resp)
          wrapped-handler (rc/handler-wrapper-avoid-useless-fetching handler props)]
      (is (= (wrapped-handler event) nil)))))

(deftest test-captured-refs-table--integration
  (let [mount rc/captured-refs-table
        get-chan (async/timeout 2000)
        page 2
        page-size 20
        total-items 100
        items [factories/captured-ref]
        response {:data {:page page
                         :page-size page-size
                         :total-items total-items
                         :items items}}]
    ;; Ensure we are at a clean state
    (reset! rc/state rc/initial-state)
    (let [comp1 (mount {:get-paginated-captured-references! (constantly get-chan)})]
      ;; First state must be while loading
      (let [comp (comp1)
            props (get comp 1)
            {:keys [refs status]} props
            table-loading (get-in props [:table-state :loading])]
        (is (= true table-loading))
        (is (= nil refs))
        (is (= nil status)))
      (is (= (get (comp1) 0) rc/captured-refs-table-inner))
      (async
       done
       (go
         ;; Then the response arrives and finishes
         (>! get-chan response)
         (<! (async/timeout 100))
         ;; And the new state is set
         (let [comp (comp1)
               props (get comp 1)
               {:keys [refs status]} props
               table-loading (get-in props [:table-state :loading])]
           (is (= false table-loading))
           (is (= [factories/captured-ref] refs))
           (is (= nil status)))
         ;; The user refreshes
         (let [done-chan ((get-in (comp1) [1 :fn-refresh!])
                          {:page 1 :pageSize 9})]
           
           ;; We are loading again
           (let [comp (comp1)
                 props (get comp 1)
                 {:keys [refs status]} props
                 table-loading (get-in props [:table-state :loading])]
             (is (= true table-loading))
             (is (= nil refs))
             (is (= nil status)))
           ;; An error is returned
           (>! get-chan {:error? true :data {::some "error"}})
           ;; It ends
           (is (= (<! done-chan) :done)))
         ;; And we see the error there
         (let [comp (comp1)
               props (get comp 1)
               {:keys [refs status]} props
               table-loading (get-in props [:table-state :loading])]
           (is (= false table-loading))
           (is (= nil refs))
           (is (= {:errors {::some "error"}} status)))
         (done))))))

;; (deftest test-captured-refs-table--integration--delete-modal
;;   (let []))
