(ns kti-web.http-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [>! <! take! put! go chan close!] :as a]
   [kti-web.http :as rc]
   [kti-web.test-utils :as utils]
   [kti-web.state :as state]
   [kti-web.test-factories :as factories]))

(deftest test-prepare-request-opts
  (testing "No json-body"
    (is (= (rc/prepare-request-opts)
           {:with-credentials? false
            :headers {"authorization" (str "TOKEN " @state/token)}})))
  (testing "With json-body"
    (is (= (rc/prepare-request-opts {:a :b})
           {:with-credentials? false
            :headers {"authorization" (str "TOKEN " @state/token)}
            :json-params {:a :b}})))
  (testing "With query-params"
    (is (= (rc/prepare-request-opts {::a ::b} {::c ::d})
           {:with-credentials? false
            :headers {"authorization" (str "TOKEN " @state/token)}
            :json-params {::a ::b}
            :query-params {::c ::d}}))))

(deftest test-parse-error-body
  (testing "Only errors"
    (let [errors {:a :b :c :d} body {:errors errors}]
      (is (= (rc/parse-error-body body) errors))))
  (testing "Only error-msg"
    (let [error-msg "Foo" body {:error-msg error-msg}]
      (is (= (rc/parse-error-body body) {:ROOT "Foo"}))))
  (testing "errors and error-msg"
    (let [error-msg "Foo"
          errors {:a :b}
          body {:errors errors :error-msg error-msg}]
      (is (= (rc/parse-error-body body)
             {:ROOT "Foo" :a :b})))))

(deftest test-parse-error-response
  (testing "500"
    (is (= (rc/parse-error-response {:status 500})
           {:ROOT rc/ERR-MSG--INTERNAL-SERVER-ERROR})))
  (testing "404"
    (is (= (rc/parse-error-response {:status 404})
           {:ROOT rc/ERR-MSG--NOT-FOUND})))
  (testing "401 and 403"
    (are [code msg]
        (= (rc/parse-error-response {:status code}) {:ROOT rc/ERR-MSG--INVALID-AUTH})
      401
      403))
  (testing "Others, use parse-error-response"
    (with-redefs [rc/parse-error-response (constantly {:ROOT ::foo})]
      (are [code] (= (rc/parse-error-response {:status code}) {:ROOT ::foo})
        400 300))))
      

(deftest test-parse-response
  (testing "Errored with error-msg"
    (is (= (rc/parse-response factories/http-response-error-msg)
           {:error? true
            :data
            {:ROOT (get-in factories/http-response-error-msg [:body :error-msg])}})))
  (testing "Errored with schema error"
    (is (= (rc/parse-response factories/http-response-schema-error)
           {:error? true
            :data (get-in factories/http-response-schema-error [:body :errors])})))
  (testing "OK"
    (is (= (rc/parse-response {:success true :body {:a :b}})
           {:data {:a :b}})))
  (testing "Calls parse-error-body"
    (with-redefs [rc/parse-error-body (constantly ::bar)]
      (is (= (rc/parse-response {:success false})
             {:error? true :data ::bar})))))

(deftest test-run-req!-base
  (let [http-fn-chan (chan 1)
        [http-fn-args save-http-fn-args] (utils/args-saver)
        {:keys [http-fn url json-params query-params] :as args}
        {:http-fn (fn [x y] (save-http-fn-args x y) http-fn-chan)
         :url "www.google.com"
         :json-params {:a 1}
         :query-params {:b 2}}
        chan (rc/run-req! args)]
    (is (= @http-fn-args [[url (assoc {:with-credentials? false
                                       :headers
                                       {"authorization" (str "TOKEN " @state/token)}}
                                      :json-params json-params
                                      :query-params query-params)]]))
    (async done
           (go (>! http-fn-chan {:success true :body 1})
               (is (= {:data 1} (<! chan)))
               (done)))))

(deftest test-run-req!-with-deserialization
  (let [http-fn-chan (a/to-chan [{:success true :body 1}])
        resp-chan (rc/run-req! {:http-fn (constantly http-fn-chan)
                                :deserialize-fn inc})]
    (async done (go (is (= (<! resp-chan) {:data 2}))
                    (done)))))

(deftest test-run-req!-error
  (let [http-fn-chan (chan)
        [http-fn-args save-http-fn-args] (utils/args-saver)
        http-fn #(do (save-http-fn-args %1 %2) http-fn-chan)]
    ;; Calls the function to run the request
    (let [res-chan (rc/run-req! {:http-fn http-fn :url :foo})]
      (async done
             (go
               ;; Simulates an errored response
               (>! http-fn-chan factories/http-response-schema-error)
               ;; And checks that we received the response we expected
               (is (= (<! res-chan)
                      (rc/parse-response factories/http-response-schema-error)))
               (done))))))
