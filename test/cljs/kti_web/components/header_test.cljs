(ns kti-web.components.header-test
  (:require [kti-web.components.header :as rc]
            [kti-web.test-utils :as utils]
            [kti-web.components.utils :as component-utils]
            [cljs.test :refer-macros [is are deftest testing use-fixtures async]]))

(deftest test-host-input
  (let [props {:value ::value :on-change ::on-change}
        inp (rc/host-input props)]
    (is (= (get-in inp [0]) component-utils/input))
    (is (= (get-in inp [1 :value]) ::value))
    (is (= (get-in inp [1 :on-change]) ::on-change))
    (is (= (get-in inp [1 :placeholder]) "Host"))
    (is (= (get-in inp [1 :className]) "host-input"))))

(deftest test-token-input
  (let [props {:value ::value :on-change ::on-change}
        inp (rc/token-input props)]
    (is (= (get-in inp [0]) component-utils/input))
    (is (= (get-in inp [1 :value]) ::value))
    (is (= (get-in inp [1 :on-change]) ::on-change))
    (is (= (get-in inp [1 :placeholder]) "Token"))
    (is (= (get-in inp [1 :className]) "token-input"))
    (is (= (get-in inp [1 :type]) "password"))))
