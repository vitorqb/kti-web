(ns ^:figwheel-hooks kti-web.core
  (:require
   [reagent.core :as r]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]
   [cljs.core.async :refer [chan go take! put! <! >!]]
   [cljs-http.client :as http]))

(declare capture-form captured-refs-table)

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
;; -----------------------------------------------------------------------------
;; Ajax
(defn post-captured-reference! [ref]
  (let [out-chan (chan)]
    (go (let [{:keys [success body]}
              (->> {:with-credentials? false :json-params {:reference ref}}
                   (http/post "http://localhost:3333/api/captured-references")
                   (<!))]
          (>! out-chan (if success body {:error true}))))
    out-chan))

(defn get-captured-references! []
  (let [out-chan (chan)]
    (go (let [{:keys [success body]}
              (->> {:with-credentials? false}
                   (http/get "http://localhost:3333/api/captured-references")
                   (<!))]
          (>! out-chan (if success body {:error true}))))
    out-chan))

;; -------------------------
;; Page components

(defn home-page []
  (fn []
    [:span.main
     [:h1 "Welcome to kti-web"]
     [capture-form {:post! post-captured-reference!}]
     [captured-refs-table {:get! get-captured-references!}]]))

(defn capture-input [{:keys [on-change value]}]
  [:div
   [:span "Capture: "]
   [:input {:type "text"
            :style {:width "60%" :min-width "10cm"}
            :on-change #(-> % .-target .-value on-change)
            :value value}]
   [:div [:i "(current value: " value ")"]]])

(defn capture-form [{:keys [post! c-done]}]
  (let [state (r/atom {:value nil :loading false :result nil})
        set-loading! #(swap! state assoc :loading true :result nil)
        set-result! #(swap! state assoc :loading false :result %)
        extract-result
        (fn [{:keys [id reference error]}]
          (if error "Error!" (str "Created with id " id " and ref " reference)))
        handle-submit
        (fn [e]
          (.preventDefault e)
          (set-loading!)
          (go (let [resp (-> @state :value post! <!)]
                (set-result! (extract-result resp))
                (and c-done (>! c-done 1)))))]
    (fn []
      [:div
       [:h3 "Capture Form"]
       [:form
        {:on-submit handle-submit}
        [capture-input {:value (:value @state)
                        :on-change #(swap! state assoc :value %)}]
        [:button {:type "submit"} "Submit"]
        [:div (:result @state)]
        (if (:loading state) [:div "Loading..."])]])))

(defn captured-refs-table [{:keys [get! c-done]}]
  (let [state (r/atom {:loading true :refs nil})
        headers ["id" "ref" "created at" "classified?"]
        ref->tr
        (fn [{:keys [id reference created-at classified]}]
          [:tr {:key id}
           [:td id]
           [:td reference]
           [:td created-at]
           [:td (str classified)]])
        run-get!
        (fn []
          (swap! state assoc :loading true :refs nil)
          (go (swap! state assoc :loading false :refs (<! (get!)))
              (and c-done (>! c-done 1))))]
    (run-get!)
    (fn []
      [:div
       [:h3 "Captured References Table"]
       [:button {:on-click run-get!} "Update"]
       (if (:loading @state)
         [:div "LOADING..."]
         [:table
          [:thead [:tr (map (fn [x] [:th {:key x} x]) headers)]]
          [:tbody (->> @state :refs (sort-by :created-at) reverse (map ref->tr))]])])))

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
