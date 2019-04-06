(ns kti-web.components.select-captured-ref-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [>! <! take! put! go chan close!]]
   [kti-web.components.select-captured-ref :as rc]
   [kti-web.test-utils :as utils :refer [args-saver]]))

(deftest test-select-captured-ref
  (testing "Calls get-captured-ref with chosen id on submit"
    (let [cap-ref-chan (chan 1)
          [get-captured-ref-args save-get-arg] (args-saver)
          get-captured-ref (fn [x] (save-get-arg x) cap-ref-chan)
          comp (rc/select-captured-ref {:id-value 123
                                        :get-captured-ref get-captured-ref
                                        :on-selection (constantly nil)})]
      (go (>! cap-ref-chan {}))
      (is (= 123 (get-in comp [1 3 1 :value])))
      ((get-in comp [1 1 :on-submit]) (utils/prevent-default-event))
      (is (= [[123]] @get-captured-ref-args))))
  (testing "Calls on-id-change on selected id change"
    (let [[on-id-change-args on-id-change] (args-saver)
          comp (rc/select-captured-ref {:on-id-change on-id-change})]
      ((get-in comp [1 3 1 :on-change]) (clj->js {:target {:value 99}}))
      (is (= [[99]] @on-id-change-args)))))

(deftest test-select-captured-ref--calls-on-selection-when-user-submits
  (let [[on-selection-args on-selection] (args-saver)
        cap-ref {:id 321 :reference "bar" :captured-at "foo"}
        cap-ref-chan (chan 1)
        get-captured-ref (constantly cap-ref-chan)
        comp (rc/select-captured-ref {:on-selection on-selection
                                      :get-captured-ref get-captured-ref})]
    (async done
           (go
             (>! cap-ref-chan cap-ref)
             (let [out-chan
                   ((get-in comp [1 1 :on-submit]) (utils/prevent-default-event))]
               (<! out-chan)
               (is (= [[cap-ref]] @on-selection-args))
               (done))))))
