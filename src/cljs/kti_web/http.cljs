(ns kti-web.http
  (:require
   [cljs.core.async :refer [>! <! take! put! go chan close!]]
   [cljs-http.client :as http]
   [kti-web.state :refer [token api-url]]))

(defn run-req! [{:keys [http-fn url json-params]}]
  (let [out-chan (chan)
        req-chan (http-fn url (merge {:with-credentials? false}
                                     {:headers
                                      {"authorization" (str "TOKEN " @token)}}
                                     (and json-params {:json-params json-params})))]
    (go (let [{:keys [success body]} (<! req-chan)]
          (>! out-chan (if success body {:error true}))))
    out-chan))

(defn post-captured-reference! [ref]
  (run-req!
   {:http-fn http/post
    :url (api-url "captured-references")
    :json-params {:reference ref}}))

(defn get-captured-references! []
  (run-req!
   {:http-fn http/get
    :url (api-url "captured-references")}))

(defn get-captured-reference! [id]
  (run-req!
   {:http-fn http/get
    :url (api-url (str "captured-references/" id))}))

(defn put-captured-reference! [id {:keys [reference]}]
  (run-req!
   {:http-fn http/put
    :url (api-url (str "captured-references/" id))
    :json-params {:reference reference}}))

(defn delete-captured-reference! [id]
  (run-req!
   {:http-fn http/delete
    :url (api-url (str "captured-references/" id))}))
