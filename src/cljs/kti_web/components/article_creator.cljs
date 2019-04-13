(ns kti-web.components.article-creator
  (:require
   [clojure.string :as str]
   [reagent.core :as r :refer [atom]]
   [cljs.core.async :refer [chan <! >! put! go]]
   [kti-web.utils :refer [call-with-val call-prevent-default] :as utils]
   [kti-web.components.utils :refer [submit-button]]))

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
  (into {} (map (fn [[k v]] [k ((make-parser k) v)]) x)))

(defn make-input [{:keys [text type]}]
  "Makes an input component"
  (fn [{:keys [value on-change]}]
    [:div
     [:span text]
     [:input {:value value
              :on-change (call-with-val on-change)
              :type type}]
     [:div (str "(current value: " value ")")]]))

(def article-creator-inputs--id-captured-reference
  (make-input {:text "Id Captued Reference" :type "number"}))
(def article-creator-inputs--description (make-input {:text "Description"}))
(def article-creator-inputs--tags (make-input {:text "Tags"}))
(def article-creator-inputs--action-link (make-input {:text "Action Link"}))

(defn article-creator-form
  [{:keys [article-spec on-article-spec-update on-article-creation-submit]}]
  (letfn [(change-handler [k] #(on-article-spec-update (assoc article-spec k %)))]
    [:form {:on-submit (call-prevent-default #(on-article-creation-submit))}
     [:h3 "Create Article"]
     [article-creator-inputs--id-captured-reference
      {:value (:id-captured-reference article-spec)
       :on-change (change-handler :id-captured-reference)}]
     [article-creator-inputs--description
      {:value (:description article-spec)
       :on-change (change-handler :description)}]
     [article-creator-inputs--tags
      {:value (:tags article-spec)
       :on-change (change-handler :tags)}]
     [article-creator-inputs--action-link
      {:value (:action-link article-spec)
       :on-change (change-handler :action-link)}]
     [submit-button]]))

(defn article-creator--inner
  [{:keys [article-spec on-article-spec-update on-article-creation-submit]}]
  [:div
   [article-creator-form
    {:article-spec article-spec
     :on-article-spec-update on-article-spec-update
     :on-article-creation-submit on-article-creation-submit}]])

(defn article-creator [{:keys [hpost!]}]
  (let [article-spec (atom {})
        handle-article-creation-submit
        (fn []
          (let [out-chan (chan)
                resp-chan (hpost! (parse-article-spec @article-spec))]
            (go
              (let [{:keys [error? data]} (<! resp-chan)]
                (when error?
                  (utils/js-alert (str "Error: " (utils/to-str data))))
                (>! out-chan 1)))
            out-chan))]
    (fn []
      [article-creator--inner
       {:article-spec @article-spec
        :on-article-spec-update #(reset! article-spec %)
        :on-article-creation-submit handle-article-creation-submit}])))
