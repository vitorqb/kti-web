(ns kti-web.components.review-selector-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.models.reviews :as reviews-models]
   [kti-web.components.review-selector :as rc]
   [kti-web.components.utils :as components-utils]
   [kti-web.models.reviews :as models-reviews]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))

(deftest test-review-selector
  (let [mount rc/review-selector]
    (testing "Calls on-review-selection-submit"
      (let [[args fun] (utils/args-saver)
            comp (mount {:on-review-selection-submit fun})]
        ((get-in comp [1 :on-submit]) (utils/prevent-default-event))
        (is (= @args [[]]))))
    (testing "Set's selected-review-id"
      (is (= (-> {:selected-review-id 9} mount (get-in [2 1 :value])) 9)))
    (testing "On selected-review-id change"
      (let [[args fun] (utils/args-saver)
            comp (mount {:on-selected-review-id-change fun})]
        ((get-in comp [2 1 :on-change]) 9)
        (is (= @args [[9]]))))
    (testing "Displays error message based on selection-status"
      (is (= (-> {:selection-status {::a ::b}} mount (get 4))
             [components-utils/errors-displayer {:status {::a ::b}}])))))
