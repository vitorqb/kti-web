(ns kti-web.components.modal-wrapper-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [kti-web.components.modal_wrapper :as rc]))

(deftest test-active?->display
  (are [active? display] (= (rc/active?->display active?) display)
    true "block" false "none"))

(deftest test-modal-wrapper
  (with-redefs [rc/active?->display (constantly ::foo)]
    (is (= (rc/modal-wrapper {:active? ::bar} [:span "HOLA"])
           [:div {:className "modal-wrapper-div" :style {:display ::foo}}
            [:div {:className "modal-wrapper-content-div"}
             [:span "HOLA"]]]))))
