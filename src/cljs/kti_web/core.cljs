(ns ^:figwheel-hooks kti-web.core
  (:require
   [reagent.core :as r]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]
   [cljs.core.async :refer [chan go take! put! <! >!]]
   [cljs-http.client :as http]
   [kti-web.local-storage :as local-storage]))

(declare capture-form captured-refs-table delete-captured-ref-form token-input-inner
         host-input-inner)

;; -------------------------
;; State & Globals
(def token (r/atom nil))
(add-watch token :save-token (fn [_ _ _ new] (local-storage/set-item! "TOKEN" new)))
(def host (r/atom "http://localhost:3333"))
(add-watch host :save-host (fn [_ _ _ new] (local-storage/set-item! "HOST" new)))
(defn api-url [x] (str @host "/api/" x))
(defn init-state []
  (some->> (local-storage/get-item "TOKEN") (reset! token))
  (some->> (local-storage/get-item "HOST") (reset! host)))

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
(defn run-req! [{:keys [http-fn url json-params]}]
  (let [out-chan (chan)
        req-chan (http-fn url (merge {:with-credentials? false}
                                     {:headers
                                      {"authorization" (str "TOKEN " @token)}}
                                     (and json-params {:json-params json-params})))]
    (go (let [{:keys [success body]} (<! req-chan)]
          (>! out-chan (if success body {:error true}))))
    out-chan))

(defn post-captured-reference! [ref]
  (run-req!
   {:http-fn http/post
    :url (api-url "captured-references")
    :json-params {:reference ref}}))

(defn get-captured-references! []
  (run-req!
   {:http-fn http/get
    :url (api-url "captured-references")}))

(defn delete-captured-reference! [id]
  (run-req!
   {:http-fn http/delete
    :url (api-url (str "captured-references/" id))}))

;; -------------------------
;; Utils
(defn call-with-val [f] #(-> % .-target .-value f))

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
      [delete-captured-ref-form {:delete! delete-captured-reference!}]
      [captured-refs-table {:get! get-captured-references!}]]]))

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
    [:button {:type "submit"} "Capture"]
    [:div result]
    (if loading? [:div "Loading..."])]])

(defn capture-form [{:keys [post! c-done]}]
  (let [state (r/atom {:value nil :loading? false :result nil})
        extract-result
        (fn [{:keys [id reference error]}]
          (if error "Error!" (str "Created with id " id " and ref " reference)))
        handle-submit
        (fn [e]
          (.preventDefault e)
          (swap! state assoc :loading? true :result nil)
          (go (let [resp (-> @state :value post! <!)]
                (swap! state assoc :loading? false :result (extract-result resp))
                (and c-done (>! c-done 1)))))]
    (fn [] (-> @state
               (assoc :on-submit handle-submit
                      :on-change #(swap! state assoc :value %))
               capture-form-inner))))

(defn captured-refs-table-inner [{:keys [loading? refs fn-refresh!]}]
  (let [headers ["id" "ref" "created at" "classified?"]
        ref->tr
        (fn [{:keys [id reference created-at classified]}]
          [:tr {:key id}
           [:td id]
           [:td reference]
           [:td created-at]
           [:td (str classified)]])]
    [:div
     [:h3 "Captured References Table"]
     [:button {:on-click fn-refresh!} "Update"]
     (if loading?
       [:div "LOADING..."]
       [:table
        [:thead [:tr (map (fn [x] [:th {:key x} x]) headers)]]
        [:tbody (->> refs (sort-by :created-at) reverse (map ref->tr))]])]))

(defn captured-refs-table [{:keys [get! c-done]}]
  (let [state (r/atom {:loading? true :refs nil})
        run-get!
        (fn []
          (swap! state assoc :loading? true :refs nil)
          (go (swap! state assoc :loading? false :refs (<! (get!)))
              (and c-done (>! c-done 1))))]
    (run-get!)
    #(captured-refs-table-inner (assoc @state :fn-refresh! run-get!))))

(defn delete-captured-ref-form-inner
  [{:keys [ref-id result update-ref-id! delete!]}]
  (let [handle-submit
        (fn [e]
          (.preventDefault e)
          (delete! ref-id))]
    [:div
     [:form {:on-submit handle-submit}
      [:h3 "Delete Captured Ref. Form"]
      [:span "Ref Id: "]
      [:input {:type "number" :value ref-id
               :on-change (call-with-val update-ref-id!)}]
      [:div [:i (str "(current value: " ref-id ")")]]
      [:div [:button {:type "submit"} "Delete"]]
      (when result [:div (str "Result: " result)])]]))

(defn delete-captured-ref-form [{:keys [delete! c-done]}]
  (let [state (r/atom {:ref-id nil :result nil})
        update-ref-id #(swap! state assoc :ref-id %)
        run-delete!
        (fn [id]
          (swap! state assoc :result nil)
          (go (let [{:keys [error]} (<! (delete! id))]
                (swap! state assoc :result (if error "Error!" "Deleted!"))
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
