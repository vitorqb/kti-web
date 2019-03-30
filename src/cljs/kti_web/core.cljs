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
         host-input-inner edit-captured-ref-form captured-ref-form select-captured-ref)

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

(defn get-captured-reference! [id]
  (run-req!
   {:http-fn http/get
    :url (api-url (str "captured-references/" id))}))

(defn put-captured-reference! [id {:keys [reference]}]
  (run-req!
   {:http-fn http/put
    :url (api-url (str "captured-references/" id))
    :json-params {:reference reference}}))

(defn delete-captured-reference! [id]
  (run-req!
   {:http-fn http/delete
    :url (api-url (str "captured-references/" id))}))

;; -------------------------
;; Utils
(defn call-with-val [f] #(-> % .-target .-value f))
(defn call-prevent-default [f] #(do (.preventDefault %) (f %)))

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
      [edit-captured-ref-form {:hput! put-captured-reference!}]
      [delete-captured-ref-form {:delete! delete-captured-reference!}]
      [captured-refs-table {:get! get-captured-references!}]]]))

(defn select-captured-ref
  [{:keys [get-captured-ref id-value on-id-change on-selection]}]
  "A form to select a captured reference."
  (letfn [(handle-submit [e]
            (let [cap-ref-chan (get-captured-ref id-value) out-chan (chan)]
              (go (on-selection (<! cap-ref-chan))
                  (>! out-chan 1))
              out-chan))]
    [:div
     [:form {:on-submit (call-prevent-default handle-submit)}
      [:span "Choose an id: "]
      [:input {:value id-value
               :on-change (call-with-val on-id-change)
               :type "number"}]
      [:button {:type "Submit"} "Submit!"]]]))

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
          (swap! state assoc :loading? true :result nil)
          (go (let [resp (-> @state :value post! <!)]
                (swap! state assoc :loading? false :result (extract-result resp))
                (and c-done (>! c-done 1)))))]
    (fn [] (-> @state
               (assoc :on-submit (call-prevent-default handle-submit)
                      :on-change #(swap! state assoc :value %))
               capture-form-inner))))

(defn edit-captured-ref-form [{:keys [hput!]}]
  "A form to edit a captured reference."
  (let [selected-id-value (r/atom nil)
        selected-cap-ref (r/atom nil)
        editted-cap-ref (r/atom nil)
        status (r/atom nil)
        handle-submit
        (fn []
          (let [resp-chan (hput! @selected-id-value @editted-cap-ref)]
            (go (let [{:keys [error]} (<! resp-chan)]
                  (reset! status (if error "Error!" "Success!"))))))]
    (fn []
      [:div
       [:h3 "Edit Captured Reference Form"]
       [select-captured-ref
        {:get-captured-ref get-captured-reference!
         :on-selection #(do (reset! selected-cap-ref %) (reset! editted-cap-ref %))
         :id-value @selected-id-value
         :on-id-change #(reset! selected-id-value %)}]
       [:div {:hidden (nil? @editted-cap-ref)}
        [captured-ref-form {:value @editted-cap-ref
                            :on-change #(reset! editted-cap-ref %)}]
        [:form {:on-submit (call-prevent-default handle-submit)}
         [:button {:type "Submit"} "Submit"]]
        [:div @status]]])))

(defn captured-ref-form [{:keys [value on-change]}]
  (letfn [(handle-change [k] (fn [x] (on-change (assoc value k x))))]
    [:div
     [:div
      [:span "Id"]
      [:input {:value (:id value "") :disabled true}]]
     [:div
      [:span "Created at"]
      [:input {:value (:created-at value "") :disabled true}]]
     [:div 
      [:span "Reference"]
      [:input {:value (:reference  value "")
               :on-change (call-with-val (handle-change :reference))}]]]))

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
  (let [handle-submit #(delete! ref-id)]
    [:div
     [:form {:on-submit (call-prevent-default handle-submit)}
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
