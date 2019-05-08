(ns kti-web.components.review-editor-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.components.review-editor :as rc]
   [kti-web.components.utils :as components-utils]
   [kti-web.models.reviews :as models-reviews]
   [kti-web.test-utils :as utils]
   [kti-web.test-factories :as factories]))


(deftest test-review-editor-form
  (let [mount rc/review-editor-form
        get-id-input-props #(get-in % [2 1])
        get-id-article-input-props #(get-in % [3 1])
        get-feedback-text-input-props #(get-in % [4 1])
        get-status-input-props #(get-in % [5 1])]
    (testing "On submit"
      (let [[args fun] (utils/args-saver)
            comp (mount {:on-edited-article-submit fun})]
        ((get-in comp [1 :on-submit]) (utils/prevent-default-event))
        (is (= @args [[]]))))
    (testing "Id input value"
      (is (= (-> {:edited-review {:id 9}} mount get-id-input-props :value) 9)))
    (testing "Id disabled"
      (is (true? (-> {} mount get-id-input-props :disabled))))
    (testing "Article id value"
      (is (= (-> {:edited-review {:id-article 99}}
                 mount
                 get-id-article-input-props
                 :value)
             99)))
    (testing "Article id on-change"
      (let [[args fun] (utils/args-saver)
            comp (mount {:edited-review {::a ::b} :on-edited-review-change fun})]
        ((-> comp get-id-article-input-props :on-change) 81)
        (is (= @args [[{::a ::b :id-article 81}]]))))
    (testing "Feedback text value"
      (is (= (-> {:edited-review {:feedback-text "foo"}}
                 mount
                 get-feedback-text-input-props
                 :value)
             "foo")))
    (testing "Feedback Text on-change"
      (let [[args fun] (utils/args-saver)
            comp (mount {:edited-review {::a ::b} :on-edited-review-change fun})]
        ((-> comp get-feedback-text-input-props :on-change) "bar")
        (is (= @args [[{::a ::b :feedback-text "bar"}]]))))
    (testing "Status value"
      (is (= (-> {:edited-review {:status :in-progress}}
                 mount
                 get-status-input-props
                 :value)
             :in-progress)))
    (testing "Status on-change"
      (let [[args fun] (utils/args-saver)
            comp (mount {:edited-review {::a ::b} :on-edited-review-change fun})]
        ((-> comp get-status-input-props :on-change) :completed)
        (is (= @args [[{::a ::b :status :completed}]]))))))
