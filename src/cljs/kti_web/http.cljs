(ns kti-web.http
  (:require
   [cljs.core.async :refer [>! <! take! put! go chan close!] :as async]
   [cljs-http.client :as http]
   [kti-web.state :refer [token api-url]]))

(defn prepare-request-opts
  "Returns a map with options for running requests with cljs-http"
  ([] (prepare-request-opts nil))
  ([json-params]
   (merge {:with-credentials? false
           :headers {"authorization" (str "TOKEN " @token)}}
          (and json-params {:json-params json-params}))))

(defn parse-response
  "Parses an http response into a default format."
  [{:keys [success body] :as response}]
  (if success
    body
    {:error true :response response}))

(defn run-req!
  "Runs a request using http-fn and json-params, maps the response with
  parse-response and returns a channel with the parsed response."
  [{:keys [http-fn url json-params]}]
  (async/map
   parse-response
   [(http-fn url (prepare-request-opts json-params))]))

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

(defn post-article! [article-spec]
  (run-req!
   {:http-fn http/post
    :url (api-url "articles")
    :json-params article-spec}))
