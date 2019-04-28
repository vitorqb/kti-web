(ns ^:figwheel-hooks kti-web.core
  (:require [accountant.core :as accountant]
            [clerk.core :as clerk]
            [cljs.core.async :refer [<! >! go]]
            [kti-web.components.article-creator :refer [article-creator]]
            [kti-web.components.article-deletor :refer [article-deletor]]
            [kti-web.components.article-editor :refer [article-editor]]
            [kti-web.components.review-creator :refer [review-creator]]
            [kti-web.components.review-deletor :refer [review-deletor]]
            [kti-web.components.review-editor :refer [review-editor]]
            [kti-web.components.captured-reference-table
             :refer
             [captured-refs-table]]
            [kti-web.components.edit-captured-reference-component
             :refer
             [edit-captured-ref-comp]]
            [kti-web.components.utils :refer [submit-button]]
            [kti-web.http
             :refer
             [delete-article!
              delete-captured-reference!
              get-article!
              get-captured-reference!
              get-captured-references!
              post-article!
              post-captured-reference!
              put-article!
              put-captured-reference!
              post-review!]]
            [kti-web.state :refer [host init-state token]]
            [kti-web.utils :as utils :refer [call-prevent-default call-with-val]]
            [reagent.core :as r]
            [reagent.session :as session]
            [reitit.frontend :as reitit]))

(declare
 capture-form
 delete-captured-ref-form
 token-input-inner
 host-input-inner)

;; -------------------------
;; Routes
(def router
  (reitit/router
   [["/" :index]
    ["/items"
     ["" :items]
     ["/:item-id" :item]]
    ["/about" :about]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

(path-for :about)

;; -------------------------
;; Page components
(defn home-page []
  (fn []
    [:span.main
     [:h1 "Welcome to kti-web"]
     [:div
      [:h2 "Options"]
      [host-input-inner {:value @host :on-change #(reset! host %)}]
      [token-input-inner {:value @token :on-change #(reset! token %)}]]
     [:div
      [:h2 "Captured References"]
      [capture-form {:post! post-captured-reference!}]
      [edit-captured-ref-comp {:hput! put-captured-reference!
                               :hget! get-captured-reference!}]
      [delete-captured-ref-form {:delete! delete-captured-reference!}]
      [captured-refs-table {:get! get-captured-references!}]]
     [:div
      [:h2 "Articles!"]
      [article-creator {:hpost! post-article!}]
      [article-editor {:get-article! get-article! :put-article! put-article!}]
      [article-deletor {:delete-article! delete-article!}]]
     [:div
      [:h2 "Reviews"]
      [review-creator {:post-review! post-review!}]
      [review-editor {}]
      [review-deletor {}]]]))

(defn host-input-inner [{:keys [value on-change]}]
  [:div
   [:span "Host"]
   [:input {:value value :on-change (call-with-val on-change)}]])

(defn token-input-inner [{:keys [value on-change]}]
  [:div
   [:span "Token"]
   [:input {:value value :on-change (call-with-val on-change) :type "password"}]])

(defn capture-input [{:keys [on-change value]}]
  [:div
   [:span "Capture: "]
   [:input {:type "text" :value value
            :on-change (call-with-val on-change)
            :style {:width "60%" :min-width "10cm"}}]
   [:div [:i "(current value: " value ")"]]])

(defn capture-form-inner [{:keys [loading? value result on-submit on-change]}]
  [:div
   [:h3 "Capture Form"]
   [:form
    {:on-submit on-submit}
    [capture-input {:value value :on-change on-change}]
    [submit-button {:text "Capture!"}]
    [:div result]
    (if loading? [:div "Loading..."])]])

(defn capture-form [{:keys [post! c-done]}]
  (let [state (r/atom {:value nil :loading? false :result nil})
        extract-result
        (fn [{:keys [data error?]}]
          (if error?
            "Error!"
            (str "Created with id " (data :id) " and ref " (data :reference))))
        handle-submit
        (fn [e]
          (swap! state assoc :loading? true :result nil)
          (go (let [resp (-> @state :value post! <!)]
                (swap! state assoc :loading? false :result (extract-result resp))
                (and c-done (>! c-done 1)))))]
    (fn [] (-> @state
               (assoc :on-submit (call-prevent-default handle-submit)
                      :on-change #(swap! state assoc :value %))
               capture-form-inner))))

(defn delete-captured-ref-form-inner
  [{:keys [ref-id result update-ref-id! delete!]}]
  (let [handle-submit #(delete! ref-id)]
    [:div
     [:form {:on-submit (call-prevent-default handle-submit)}
      [:h3 "Delete Captured Ref. Form"]
      [:span "Ref Id: "]
      [:input {:type "number" :value ref-id
               :on-change (call-with-val update-ref-id!)}]
      [:div [:i (str "(current value: " ref-id ")")]]
      [:div [submit-button {:text "Delete!"}]]
      (when result [:div (str "Result: " result)])]]))

(defn delete-captured-ref-form [{:keys [delete! c-done]}]
  (let [state (r/atom {:ref-id nil :result nil})
        update-ref-id #(swap! state assoc :ref-id %)
        run-delete!
        (fn [id]
          (swap! state assoc :result nil)
          (go (let [{:keys [error? data]} (<! (delete! id))]
                (swap! state assoc :result
                       (if error?
                         (str "Error: " (utils/to-str data))
                         "Deleted!"))
                (and c-done (>! c-done 1)))))]
    #(delete-captured-ref-form-inner (assoc @state
                                            :update-ref-id! update-ref-id
                                            :delete! run-delete!))))

(defn items-page []
  (fn []
    [:span.main
     [:h1 "The items of kti-web"]
     [:ul (map (fn [item-id]
                 [:li {:name (str "item-" item-id) :key (str "item-" item-id)}
                  [:a {:href (path-for :item {:item-id item-id})} "Item: " item-id]])
               (range 1 60))]]))

(defn item-page []
  (fn []
    (let [routing-data (session/get :route)
          item (get-in routing-data [:route-params :item-id])]
      [:span.main
       [:h1 (str "Item " item " of kti-web")]
       [:p [:a {:href (path-for :items)} "Back to the list of items"]]])))

(defn about-page []
  (fn [] [:span.main
          [:h1 "About kti-web"]]))

;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
    :about #'about-page
    :items #'items-page
    :item #'item-page))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p [:a {:href (path-for :index)} "Home"] " | "
         [:a {:href (path-for :about)} "About kti-web"]]]
       [page]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (init-state)
  (r/render [current-page] (.getElementById js/document "app")))

(defn ^:after-load re-render [] (mount-root))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (r/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)
        ))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
