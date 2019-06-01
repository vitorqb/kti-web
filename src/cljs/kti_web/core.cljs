(ns ^:figwheel-hooks kti-web.core
  (:require [accountant.core :as accountant]
            [clerk.core :as clerk]
            [cljs.core.async :refer [<! >! go]]
            [kti-web.components.article-viewer :refer [article-viewer]]
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
            [kti-web.components.captured-reference-deletor-modal
             :as captured-reference-deletor-modal]
            [kti-web.components.utils :refer [submit-button input]]
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
              post-review!
              put-review!
              get-review!
              delete-review!]]
            [kti-web.event-handlers :refer [gen-handler-vec]]
            [kti-web.state :refer [host init-state token]]
            [kti-web.utils :as utils :refer [call-prevent-default call-with-val]]
            [reagent.core :as r]
            [reagent.session :as session]
            [reitit.frontend :as reitit]))

(declare capture-form delete-captured-ref-form)

;; -------------------------
;; Routes
(def router
  (reitit/router
   [["/" :index]
    ["/items"
     ["" :items]
     ["/:item-id" :item]]
    ["/about" :about]
    ["/article" :article]
    ["/review" :review]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

(path-for :about)

;; -------------------------
;; Main Captured Reference Deletor Modal State
(defonce main-captured-reference-deletor-modal-state
  (r/atom {:active? false
           :delete-captured-ref-id nil}))
(def main-captured-reference-deletor-modal-handlers
  {:on-abortion
   (fn main-captured-reference-deletor-modal-on-abortion []
     (swap! main-captured-reference-deletor-modal-state
            captured-reference-deletor-modal/reduce-on-abortion
            nil
            nil))

   :on-modal-display-for-deletion
   (fn main-captured-reference-deletor-modal-on-modal-display-for-deletion [event]
     (swap! main-captured-reference-deletor-modal-state
            captured-reference-deletor-modal/reduce-on-modal-display-for-deletion
            nil
            event))

   :on-confirm-deletion
   (gen-handler-vec
    main-captured-reference-deletor-modal-state
    {:delete-captured-reference! delete-captured-reference!}
    captured-reference-deletor-modal/handler-fns-on-confirm-deletion)})

(defn main-captured-reference-deletor-modal
  []
  (let [props (merge @main-captured-reference-deletor-modal-state
                     main-captured-reference-deletor-modal-handlers)]
    [captured-reference-deletor-modal/captured-reference-deletor-modal props]))

;; -----------------------------------------------------------------------------
;; Page components
(defn home-page []
  (fn []
    [:span.main
     (main-captured-reference-deletor-modal)
     [:h1 "Welcome to kti-web"]
     [:div
      [:h3 "Options"]
      [input {:text "Host: " :value @host :on-change #(reset! host %)}]
      [input {:text "Token: "
              :value @token
              :on-change #(reset! token %)
              :type "password"}]]
     [:div
      [:h3 "Captured References"]
      [capture-form {:post! post-captured-reference!}]
      [captured-refs-table
       (merge
        {:get! get-captured-references!}
        (select-keys main-captured-reference-deletor-modal-handlers
                     [:on-modal-display-for-deletion]))]
      [edit-captured-ref-comp {:hput! put-captured-reference!
                               :hget! get-captured-reference!}]
      [delete-captured-ref-form {:delete! delete-captured-reference!}]]]))

(defn capture-input [{:keys [on-change value]}]
  [:div
   [:span "Capture: "]
   [:input {:type "text" :value value
            :on-change (call-with-val on-change)
            :style {:width "60%" :min-width "10cm"}}]
   [:div [:i "(current value: " value ")"]]])

(defn capture-form-inner [{:keys [loading? value result on-submit on-change]}]
  [:div
   [:h4 "Capture Form"]
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
      [:h4 "Delete Captured Ref. Form"]
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

(defn article-page
  "A page for articles =D"
  []
  (fn []
    [:div
     [:h3 "Articles!"]
     [article-viewer {:get-article! get-article!}]
     [article-creator {:hpost! post-article!}]
     [article-editor {:get-article! get-article! :put-article! put-article!}]
     [article-deletor {:delete-article! delete-article!}]]))

(defn review-page
  "A page for reviews =D"
  []
  [:div
   [:h3 "Reviews"]
   [review-creator {:post-review! post-review!}]
   [review-editor {:get-review! get-review! :put-review! put-review!}]
   [review-deletor {:get-review! get-review! :delete-review! delete-review!}]])

;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
    :about #'about-page
    :items #'items-page
    :item #'item-page
    :article #'article-page
    :review #'review-page))

;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p
         [:a {:href (path-for :index)} "Home"] " | "
         [:a {:href (path-for :article)} "Articles"] " | "
         [:a {:href (path-for :review)} "Reviews"] " | "
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
