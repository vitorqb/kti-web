(ns kti-web.components.rtable-test
  (:require [kti-web.components.rtable :as sut]
            [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
            [reagent.core :as r]))

(deftest test-rtable
  (testing "Returns adapted react class with parsed props"
    (with-redefs [r/adapt-react-class (constantly ::adapted-class)
                  sut/parse-props (constantly ::parsed-props)]
      (is (= (sut/rtable {:data [1] :columns [{}]})
             [::adapted-class ::parsed-props])))))

(deftest test-parse-prop
  (let [parse-prop sut/parse-prop]
    (testing "Default"
      (is (= (parse-prop [::key ::value]) [::key ::value])))
    (testing "Columns"
      (with-redefs [sut/parse-column (constantly ::parsed-col)]
        (is (= (parse-prop [:columns [{} {}]])
               [:columns [::parsed-col ::parsed-col]]))))))

(deftest test-parse-column
  (are [in out] (is (= out (sut/parse-column in)))
    {:header "foo" :id "bar"} {:Header "foo" :id "bar"}
    {:header "FOO"} {:Header "FOO" :id "foo"}
    {:header "BaR" :width 100} {:Header "BaR" :width 100 :id "bar"})
  (with-redefs [sut/wrap-cell-fn (constantly ::wrapped-cell-fn)]
    (is (= (sut/parse-column {:header "foo" :cell-fn (fn [x] x)})
           {:Header "foo" :id "foo" :Cell ::wrapped-cell-fn}))))
