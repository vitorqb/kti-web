(ns kti-web.test-factories)

(def http-response-schema-error
  {:status 400
   :success false
   :body
   {:schema
    (str "{:id-captured-reference java.lang.Integer, :description java.lang.Str"
         "ing, :action-link (maybe Str), :tags #{java.lang.String}}")
    :errors
    {:id-captured-reference "missing-required-key"
     :action-link "missing-required-key"
     :tags "missing-required-key"}
    :type "compojure.api.exception/request-validation"
    :coercion "schema"
    :value {:description "Hola"}
    :in ["request" "body-params"]}
   :headers {"content-type" "application/json; charset=utf-8"}
   :trace-redirects
   ["http://159.65.192.68//api/articles"
    "http://159.65.192.68//api/articles"]
   :error-code :http-error,
   :error-text "Bad Request [400]"})

(def http-response-error-msg
  {:status 400
   :success false
   :body
   {:error-msg "Error message"}
   :headers {"content-type" "application/json; charset=utf-8"}
   :trace-redirects
   ["http://159.65.192.68//api/articles"
    "http://159.65.192.68//api/articles"]
   :error-code :http-error,
   :error-text "Bad Request [400]"})

(def captured-ref
  {:id 49
   :reference "Foobarbaz"
   :created-at "1993-11-23T11:12:15"
   :classified false})

(def article-raw-spec
  {:id-captured-reference "12"
   :description "Foo Bar"
   :tags "tag1, tag2"
   :action-link "www.google.com"})

(defn ok-response
  "Factory for responses with 200 and some data"
  [body]
  {:status 200,
   :success true,
   :body body,
   :headers {"content-type" "application/json; charset=utf-8"},
   :trace-redirects
   ["http://159.65.192.68/api/captured-references"
    "http://159.65.192.68/api/captured-references"],
   :error-code :no-error, 
   :error-text ""})

(defn parsed-ok-response
  "Factory for ok parsed http responses"
  [data]
  {:error? false :data data})
