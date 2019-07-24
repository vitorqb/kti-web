(ns kti-web.components.captured-reference-table.helpers-test
  (:require [kti-web.components.captured-reference-table.helpers :as rc]
            [cljs.test :refer-macros [is are deftest testing use-fixtures async]]))

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
