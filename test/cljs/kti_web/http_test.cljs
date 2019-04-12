(ns kti-web.http-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [>! <! take! put! go chan close!]]
   [kti-web.http :as rc]
   [kti-web.test-utils :as utils]
   [kti-web.state :as state]))

(deftest test-prepare-request-opts
  (testing "No json-body"
    (is (= (rc/prepare-request-opts)
           {:with-credentials? false
            :headers {"authorization" (str "TOKEN " @state/token)}})))
  (testing "With json-body"
    (is (= (rc/prepare-request-opts {:a :b})
           {:with-credentials? false
            :headers {"authorization" (str "TOKEN " @state/token)}
            :json-params {:a :b}}))))

(deftest test-parse-response
  (testing "Errored"
    (let [response {:success false :a :b}]
      (is (= (rc/parse-response response) {:error true :response response}))))
  (testing "Success"
    (is (= (rc/parse-response {:success true :body :a})) :a)))

(deftest test-run-req!-base
  (let [http-fn-chan (chan 1)
        [http-fn-args save-http-fn-args] (utils/args-saver)
        {:keys [http-fn url json-params] :as args}
        {:http-fn (fn [x y] (save-http-fn-args x y) http-fn-chan)
         :url "www.google.com"
         :json-params {:a 1}}
        chan (rc/run-req! args)]
    (is (= @http-fn-args [[url (assoc {:with-credentials? false
                                       :headers
                                       {"authorization" (str "TOKEN " @state/token)}}
                                      :json-params json-params)]]))
    (async done
           (go (>! http-fn-chan {:success true :body 1})
               (is (= 1 (<! chan)))
               (done)))))

(deftest test-run-req!-error
  (let [http-fn-chan (chan 1)
        [http-fn-args save-http-fn-args] (utils/args-saver)
        http-fn (fn [x y] (save-http-fn-args x y) http-fn-chan)]
    ;; Runs the request and stores the channel
    (let [res-chan (rc/run-req! {:http-fn http-fn :url 1})]
      ;; http-fn should have been called with those args
      (is (= @http-fn-args
             [[1 {:with-credentials? false
                  :headers {"authorization" (str "TOKEN " @state/token)}}]]))
      (let [response {:success false :status 404}]
        (async done
               (go
                 ;; Simulates the errored http response
                 (>! http-fn-chan response)
                 ;; And ensures that the error is written on the response channel
                 (is (= {:error true :response response} (<! res-chan)))
                 (done)))))))
