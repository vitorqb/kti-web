(ns kti-web.http
  (:require
   [cljs.core.async :refer [>! <! take! put! go chan close!] :as async]
   [cljs-http.client :as http]
   [kti-web.state :refer [token api-url]]))

(def ERR-MSG--INTERNAL-SERVER-ERROR
  (concat
   "Something went wrong and potentially it is not your fault."
   " Internal Server Error (500)"))
(def ERR-MSG--NOT-FOUND "The requested resource was not found!")
(def ERR-MSG--INVALID-AUTH "Invalid authentication - did you forgot the token?")

(defn prepare-request-opts
  "Returns a map with options for running requests with cljs-http"
  ([] (prepare-request-opts nil))
  ([json-params]
   (merge {:with-credentials? false
           :headers {"authorization" (str "TOKEN " @token)}}
          (and json-params {:json-params json-params}))))

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
