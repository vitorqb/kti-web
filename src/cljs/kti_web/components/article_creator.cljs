(ns kti-web.components.article-creator
  (:require
   [clojure.string :as str]
   [reagent.core :as r :refer [atom]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.utils :refer [call-with-val call-prevent-default] :as utils]
   [kti-web.components.utils :refer [submit-button] :as components-utils]))

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
  [{:keys [article-spec on-article-spec-update on-article-creation-submit errors]}]
  [:div
   [article-creator-form
    {:article-spec article-spec
     :on-article-spec-update on-article-spec-update
     :on-article-creation-submit on-article-creation-submit}]
   [components-utils/errors-displayer {:errors errors}]])

(defn article-creator [{:keys [hpost!]}]
  (let [state (r/atom {:article-spec {} :errors {}})
        handle-article-creation-submit
        (fn []
          (let [out-chan (async/timeout 3000)
                resp-chan (-> @state :article-spec parse-article-spec hpost!)]
            (go
              (let [{:keys [error? data]} (<! resp-chan)]
                (swap! state assoc :errors (if error? data {}))
                (>! out-chan :done)))
            out-chan))]
    (fn []
      [article-creator--inner
       {:article-spec (:article-spec @state)
        :errors (:errors @state)
        :on-article-spec-update #(swap! state assoc :article-spec %)
        :on-article-creation-submit handle-article-creation-submit}])))
