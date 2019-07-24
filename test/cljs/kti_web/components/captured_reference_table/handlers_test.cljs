(ns kti-web.components.captured-reference-table.handlers-test
  (:require [kti-web.components.captured-reference-table.handlers :as rc]
            [kti-web.components.captured-reference-table.helpers :as helpers]
            [cljs.test :refer-macros [is are deftest testing use-fixtures async]]))

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
    (is (= (handler) {:filters [helpers/empty-filter]}))
    (is (= @state {:filters [helpers/empty-filter]}))))

(deftest test-handle-show-article
  ;; Simple call the props handler
  (let [on-show-article #(apply vector :on-show-article %&)
        props {:on-show-article on-show-article}
        handler (rc/handle-show-article {} props)]
    (is (= [:on-show-article 99]
           (handler 99)))))
