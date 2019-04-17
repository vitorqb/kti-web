(ns kti-web.models.articles
  (:require
   [clojure.string :as str]))

(defn parse-tags [x]
  "Transforms tags from string to a list of keywords"
  (if (= x "") [] (->> (str/split x #",") (map str/trim) (map keyword))))

(defn make-parser [k]
  "Returns a function that knows how to parse a raw input of this tag
   into it's value"
  (case k
    :tags parse-tags
    :id-captured-reference js/parseInt
    :action-link #(if (= % "") nil %)
    identity))

(defn parse-article-spec [x]
  "Parses an article spec."
  (->> x
       (map (fn [[k v]] [k ((make-parser k) v)]))
       (into {:action-link nil})))
