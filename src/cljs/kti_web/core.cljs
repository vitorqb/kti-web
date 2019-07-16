(ns kti-web.core
  (:require [accountant.core :as accountant]
            [clerk.core :as clerk]
            [cljs.core.async :refer [<! >! go] :as async]
            [kti-web.components.header :as header]
            [kti-web.components.article-viewer :refer [article-viewer]]
            [kti-web.components.article-creator :refer [article-creator]]
            [kti-web.components.article-deletor :refer [article-deletor]]
            [kti-web.components.article-editor :refer [article-editor]]
            [kti-web.components.review-creator :refer [review-creator]]
            [kti-web.components.review-deletor :refer [review-deletor]]
            [kti-web.components.review-editor :refer [review-editor]]
            [kti-web.components.captured-reference-deletor-modal
             :refer [captured-reference-deletor-modal]]
            [kti-web.components.captured-reference-table
             :refer [captured-refs-table]]
            [kti-web.components.edit-captured-reference-component
             :refer [edit-captured-ref-comp]]
            [kti-web.components.capture-form :refer [capture-form]]
            [kti-web.components.utils :refer [submit-button input]]
            [kti-web.http
             :refer
             [delete-article!
              delete-captured-reference!
              get-article!
              get-captured-reference!
              get-captured-references!
              get-paginated-captured-references!
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
            [kti-web.navigation-subscription :as navigation-subscription]
            [reagent.core :as r]
            [reagent.session :as session]
            [reitit.frontend :as reitit]))

;; -------------------------
;; Routes
(def router
  (reitit/router
   [["/" :index]
    ["/about" :about]
    ["/article" :article]
    ["/review" :review]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -----------------------------------------------------------------------------
;; Navigation channel
(let [page-nav-sub (navigation-subscription/create-page-navigation-subscription)]
  (defonce publish-page-nav! (:publish! page-nav-sub))
  (defonce subscribe-page-nav! (:subscribe! page-nav-sub)))

;; -----------------------------------------------------------------------------
;; Page components
(defonce modal-display-for-deletion-chan (async/chan))
(defonce article-viewer-events-chan (async/chan))
(defn home-page []
  (fn []
    [:span.main
     [captured-reference-deletor-modal
      {:delete-captured-reference! delete-captured-reference!
       :events-chan modal-display-for-deletion-chan}]
     [:h1 "Welcome to kti-web"]
     [:div
      [:h3 "Captured References"]
      [capture-form {:post! post-captured-reference!}]
      [captured-refs-table
       {:get-paginated-captured-references! get-paginated-captured-references!
        :on-modal-display-for-deletion
        #(go (>! modal-display-for-deletion-chan [:on-modal-display-for-deletion %]))
        :on-show-article
        #(accountant/navigate! (path-for :article) {:id %})}]
      [edit-captured-ref-comp {:hput! put-captured-reference!
                               :hget! get-captured-reference!}]]]))

(defn about-page []
  (fn [] [:span.main [:h1 "About kti-web"]]))

(defn- navigated-page->show-article-event [{{id :id} :query-params}]
  (and id [:on-show-article (int id)]))

(defn article-page
  "A page for articles =D"
  []
  (let [navigation-subs-chan (async/chan)]
    (subscribe-page-nav! ::article-page :article navigation-subs-chan)
    (async/go-loop []
      (some->> (<! navigation-subs-chan)
               navigated-page->show-article-event
               (>! article-viewer-events-chan))
      (recur)))
  (fn []
    [:div
     [:h3 "Articles!"]
     [article-viewer {:get-article! get-article!
                      :events-chan article-viewer-events-chan}]
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
    :article #'article-page
    :review #'review-page))

;; -------------------------
;; Page mounting component
(defn statefull-header []
  (fn []
    (let [token-value @token
          host-value  @host
          on-token-value-change #(reset! token %)
          on-host-value-change  #(reset! host %)
          props {:on-token-value-change on-token-value-change
                 :on-host-value-change  on-host-value-change
                 :token-value token-value
                 :host-value  host-value
                 :path-for-fn path-for}]
      [header/header props])))

(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [statefull-header]
       [page]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (init-state)
  (r/render [current-page] (.getElementById js/document "app")))

(defn re-render [] (mount-root))

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
        (publish-page-nav! match)))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
