(ns kti-web.components.captured-reference-table-test
  (:require [cljs.core.async :refer [<! >! chan go] :as async]
            [cljs.test :refer-macros [async deftest is testing]]
            [kti-web.components.captured-reference-table :as rc]
            [kti-web.http :as http]
            [kti-web.test-factories :as factories]
            [kti-web.test-utils :as utils]
            [kti-web.components.rtable :refer [rtable]]))

(deftest test-handle-filters-change
  (let [state (atom {})
        handler (rc/handle-filters-change state {})]
    (is (= (handler [::foo]) {:filters [::foo]}))
    (is (= @state {:filters [::foo]}))

    (is (= (handler []) {:filters []}))
    (is (= @state {:filters []}))))

(deftest test-handle-add-empty-filter
  (let [state (atom {:filters []})
        handler (rc/handle-add-empty-filter state {})]
    (is (= (handler) {:filters [rc/empty-filter]}))
    (is (= @state {:filters [rc/empty-filter]}))))

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
                   :table {:page page :pageSize pageSize :pages pages}}]
        (is (= (rc/props->rtable-props props)
               {:data ::refs->data-response
                :page page
                :pageSize pageSize
                :on-fetch-data ::wrapper-response
                :columns ::make-columns-response
                :manual true
                :pages pages}))))))

(deftest test-add-filter-picker-button
  (testing "Calls on-add-empty-filter on click"
    (let [[args saver] (utils/args-saver)
          comp (rc/add-filter-picker-button {:on-add-empty-filter saver})]
      ((get-in comp [1 :on-click]))
      (is (= @args [[]])))))

(deftest test-filters-picker

  (testing "Empty"
    (let [comp (rc/filters-picker {:filters []})]
      (is (= (get comp 1) []))))

  (testing "One Long"
    (let [[args saver] (utils/args-saver)
          filter {:value "value" :name "name"}
          props {:filters [filter] :on-filters-change saver}
          comp (-> (rc/filters-picker props)
                   (update 1 vec))
          editted-filter (assoc filter :name "nam")]
      (is (= (get-in comp [1 0 0]) rc/filter-picker))
      (is (= (get-in comp [1 0 1 :filter]) filter))
      (is (= (get-in comp [1 0 1 :key]) 0))
      ;; The :on-filter-change must call :on-filters-change
      ((get-in comp [1 0 1 :on-filter-change]) editted-filter)
      (is (= @args [[[editted-filter]]]))))

  (testing "Add filter button"
    (let [props {:on-add-filter-button ::on-add-filter-button}
          comp (rc/filters-picker props)]
      (is (= (get-in comp [2]) [rc/add-filter-picker-button props])))))

(deftest test-filter-picker

  (let [[args saver] (utils/args-saver)
        filter {:name "name" :value "value"}
        props {:filter filter :on-filter-change saver}
        comp (rc/filter-picker props)]

    (testing "Renders filter name input"
      (reset! args [])
      (is (= (get-in comp [1 0]) rc/filter-picker-name-input))
      (is (= (get-in comp [1 1 :value]) "name"))
      ;; Calling on-change should trigger a call to on-filter-change
      ((get-in comp [1 1 :on-change]) "nam")
      (is (= @args [[(assoc filter :name "nam")]])))

    (testing "Renders filter value input"
      (reset! args [])
      (is (= (get-in comp [2 0]) rc/filter-picker-value-input))
      (is (= (get-in comp [2 1 :value]) "value"))
      ;; Calling on-change should trigger a call to on-filter-change
      ((get-in comp [2 1 :on-change]) "value1")
      (is (= @args [[(assoc filter :value "value1")]])))))

(deftest test-filters->filter-pickers
  (let [filters [{:name "foo" :value "bar"} {:name "baz" :value "boz"}]
        props {:filters filters}]
    (testing "Calls on-filter-change when removing a filter"
      (let [[args saver] (utils/args-saver)
            props (assoc props :on-filters-change saver)
            filter-pickers (rc/filters->filter-pickers filters props)
            first-filter-picker (first filter-pickers)]
        ((get-in first-filter-picker [1 :on-remove-filter]))
        (is (= @args [[(drop 1 filters)]]))))))

(deftest test-captured-refs-table-inner
  (let [mount rc/captured-refs-table-inner
        get-refresh-button #(get % 3)
        get-table #(get % 4)]
    (testing "Calls fn-refresh!"
      (let [[args fun] (utils/args-saver)
            table-state {:page 1 :pageSize 2}
            comp (mount {:fn-refresh! fun :table table-state})]
        ((-> comp get-refresh-button (get-in [1 :on-click])) ::foo)
        (is (= @args [[{:page 1 :pageSize 2}]]))))
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
               {:table {:loading true :page 1 :pageSize 2}
                :refs nil
                :status nil}))))
    (testing "action"
      (let [get-paginated-captured-references! identity
            filters [{:name "foo" :value "bar"} rc/empty-filter]
            state-page 2
            state-page-size 100
            state {:filters filters
                   :table {:page state-page :pageSize state-page-size}}
            event-page 3
            event-page-size 200
            event {:page event-page :pageSize event-page-size}
            extra-args {:get-paginated-captured-references!
                        get-paginated-captured-references!}]
        (is (= (action state extra-args event)
               {:page (inc event-page)
                :page-size event-page-size
                :filters {"foo" "bar"}}))))
    (testing "r-after"
      (is (= (r-after {::a ::b} {} nil {:error? true :data ::c})
             {::a ::b
              :table {:loading false}
              :status {:errors ::c}}))
      (let [resp-data {:page 1 :page-size 2 :total-items 3 :items [4 5]}]
        (is (= (r-after {} {} nil {:error? false :data resp-data})
               {:refs [4 5]
                :table {:pages 2 :loading false}}))))))

(deftest test-handler-wrapper-avoid-useless-fetching
  (testing "page/pageSize changes"
    (let [props {:table {:page 1 :pageSize 2}}
          event {:page 2 :pageSize 3}
          handler (constantly ::handler-resp)
          wrapped-handler (rc/handler-wrapper-avoid-useless-fetching handler props)]
      (is (= (wrapped-handler event) ::handler-resp))))
  (testing "no change"
    (let [event {:page 1 :pageSize 2}
          props {:table event}
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
            table-loading (get-in props [:table :loading])]
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
               table-loading (get-in props [:table :loading])]
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
                 table-loading (get-in props [:table :loading])]
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
               table-loading (get-in props [:table :loading])]
           (is (= false table-loading))
           (is (= nil refs))
           (is (= {:errors {::some "error"}} status)))
         (done))))))

;; (deftest test-captured-refs-table--integration--delete-modal
;;   (let []))
