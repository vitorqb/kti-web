(ns kti-web.http
  (:require
   [cljs.core.async :refer [>! <! take! put! go chan close!] :as async]
   [cljs-http.client :as http]
   [kti-web.state :refer [token api-url]]
   [kti-web.models.reviews :as reviews]))

(def ERR-MSG--INTERNAL-SERVER-ERROR
  (concat
   "Something went wrong and potentially it is not your fault."
   " Internal Server Error (500)"))
(def ERR-MSG--NOT-FOUND "The requested resource was not found!")
(def ERR-MSG--INVALID-AUTH "Invalid authentication - did you forgot the token?")

(defn prepare-request-opts
  "Returns a map with options for running requests with cljs-http"
  ([] (prepare-request-opts nil nil))
  ([json-params] (prepare-request-opts json-params nil))
  ([json-params query-params]
   (merge {:with-credentials? false
           :headers {"authorization" (str "TOKEN " @token)}}
          (and json-params {:json-params json-params})
          (and query-params {:query-params query-params}))))

(defn parse-error-body
  "Given a http response body, returns a map of error messages"
  [{:keys [error-msg errors] :as body}]
  (if-not (or error-msg errors)
    {:ROOT "Unkown error!"}
    (-> (or errors {})
        (cond-> error-msg
          (assoc :ROOT error-msg)))))

(defn parse-error-response
  "Given an http response, returns a map of error messages"
  [{:keys [body status]}]
  (case status
    (401 403) {:ROOT ERR-MSG--INVALID-AUTH}
    404 {:ROOT ERR-MSG--NOT-FOUND}
    500 {:ROOT ERR-MSG--INTERNAL-SERVER-ERROR}
    (parse-error-body body)))

(defn parse-response
  "Parses an http repsonse into a default format."
  [{:keys [success body] :as response}]
  (if success
    {:data body}
    {:error? true :data (parse-error-response response)}))

(defn deserialize-on-success
  "Deserializes using deserialize-fn if the response is not an error."
  [{:keys [error?] :as resp} deserialize-fn]
  (update resp :data (if error? identity deserialize-fn)))

(defn run-req!
  "Runs a request using http-fn and json-params, maps the response with
  parse-response and returns a channel with the parsed response."
  [{:keys [http-fn url json-params query-params deserialize-fn]
    :or {deserialize-fn identity}}]
  (as-> (prepare-request-opts json-params query-params) it
    (http-fn url it)
    (async/map parse-response [it])
    (async/map #(deserialize-on-success % deserialize-fn) [it])))

(defn post-captured-reference! [ref]
  (run-req!
   {:http-fn http/post
    :url (api-url "captured-references")
    :json-params {:reference ref}}))

(defn get-captured-references! []
  (run-req!
   {:http-fn http/get
    :url (api-url "captured-references")}))

(defn get-paginated-captured-references! [{:keys [page page-size]}]
  {:pre [(number? page) (number? page-size)]}
  (run-req!
   {:http-fn http/get
    :url (api-url "captured-references")
    :query-params {:page page :page-size page-size}}))

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

(defn get-article! [id]
  (run-req!
   {:http-fn http/get
    :url (api-url (str "articles/" id))}))

(defn post-article! [article-spec]
  (run-req!
   {:http-fn http/post
    :url (api-url "articles")
    :json-params article-spec}))

(defn put-article! [id article-spec]
  (run-req!
   {:http-fn http/put
    :url (api-url (str "articles/" id))
    :json-params article-spec}))

(defn delete-article! [id]
  (run-req!
   {:http-fn http/delete
    :url (api-url (str "articles/" id))}))

(defn get-review! [id]
  (run-req!
   {:http-fn http/get
    :url (api-url (str "reviews/" id))
    :deserialize-fn reviews/server-resp->review}))

(defn post-review! [spec]
  (run-req!
   {:http-fn http/post
    :url (api-url "reviews")
    :json-params spec}))

(defn put-review! [id spec]
  (run-req!
   {:http-fn http/put
    :url (api-url (str "reviews/" id))
    :json-params spec
    :deserialize-fn reviews/server-resp->review}))

(defn delete-review! [id]
  (run-req!
   {:http-fn http/delete
    :url (api-url (str "reviews/" id))}))
