(ns kti-web.models.articles
  (:require
   [kti-web.components.utils :as components-utils :refer [input]]
   [clojure.string :as str]))

(def inputs
  {:id [input {:text "Id" :disabled true}]
   :id-captured-reference [input {:text "Captured Ref. Id"}]
   :description [components-utils/textarea {:text "Description"}]
   :tags [input {:text "Tags"}]
   :action-link [input {:text "Action link"}]})

(defn serialize-tags [x]
  "Transforms tags from string to a list of keywords"
  (if (= x "") [] (->> (str/split x #",") (map str/trim) (map keyword))))

(defn make-serializer [k]
  "Returns a function that knows how to serialize a raw input of this tag
   into it's value"
  (case k
    :tags serialize-tags
    :id-captured-reference js/parseInt
    :action-link #(if (= % "") nil %)
    identity))

(defn make-deserializer [k]
  "Returns a function that knows how to deserialize the value for the key k"
  (case k
    :tags #(str/join ", " %)
    :id-captured-reference str
    identity))

(defn serialize-article-spec [x]
  "Serializes an article spec."
  (->> x
       (map (fn [[k v]] [k ((make-serializer k) v)]))
       (into {:action-link nil :tags []})))

(defn article->raw
  "Converts an article in it's raw (string) representation."
  [article]
  (->> (map (fn [[k v]] [k ((make-deserializer k) v)]) article)
       (into {})))
