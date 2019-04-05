(ns kti-web.http-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [>! <! take! put! go chan close!]]
   [kti-web.http :as rc]
   [kti-web.test-utils :as utils]
   [kti-web.state :as state]))


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
        http-fn (fn [x y] (save-http-fn-args x y) http-fn-chan)
        res-chan (rc/run-req! {:http-fn http-fn :url 1})]
    (is (= @http-fn-args [[1 {:with-credentials? false
                              :headers
                              {"authorization" (str "TOKEN " @state/token)}}]]))
    (async done
           (go (>! http-fn-chan {:success false})
               (is (= {:error true} (<! res-chan)))
               (done)))))
