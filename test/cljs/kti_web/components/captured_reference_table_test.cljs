(ns kti-web.components.captured-reference-table-test
  (:require [cljs.core.async :refer [<! >! chan go]]
            [cljs.test :refer-macros [async deftest is]]
            [kti-web.components.captured-reference-table :as rc]
            [kti-web.http :as http]
            [kti-web.test-factories :as factories]
            [kti-web.test-utils :as utils]))

(deftest test-captured-refs-table
  (let [c (chan 1)
        c-done (chan 1)
        comp-1 (rc/captured-refs-table {:get! (constantly c) :c-done c-done})]
    (is (= [:div "LOADING..."] (get (comp-1) 3)))
    (async done
           (go (>! c [factories/captured-ref])
               (<! c-done)
               (is (= :table (get-in (comp-1) [3 0])))
               (is (= :thead (get-in (comp-1) [3 1 0])))
               (is (= ["id" "ref" "created at" "classified?"]
                      (-> (comp-1) (get-in [3 1 1 1]) (->> (map #(get % 2))))))
               (done)))))

(deftest test-captured-refs-table--alerts-on-error
  (let [req-chan (chan)
        done-chan (chan)
        error (http/parse-response factories/http-response-error-msg)
        comp-1 (rc/captured-refs-table
                {:get! (constantly req-chan) :c-done done-chan})]
    (async done
           (go
             ;; Captures js/alert
             (let [[js-alert-args js-alert] (utils/args-saver)]
               (with-redefs [kti-web.utils/js-alert js-alert]
                 ;; Requests returns an error
                 (>! req-chan error)
                 (<! done-chan)
                 ;; That was passed to js/alert
                 (is (= @js-alert-args
                        [[(str "Error during get: " (get-in error [:data :ROOT]))]]))
                 (done)))))))
