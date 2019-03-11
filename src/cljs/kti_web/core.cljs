(ns ^:figwheel-hooks kti-web.core
  (:require
   [reagent.core :as r]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]
   [cljs.core.async :refer [chan go take! put! <! >!]]))

(declare capture-form)

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
     (let [submit-chan (chan) result-chan (chan) value (atom 0)]
       (go
         (while true
           (let [submit-arg (<! submit-chan)]
             (prn "Submitted " submit-arg)
             (put! result-chan @value)
             (swap! value + 1))))
       [capture-form {:submit-chan submit-chan :result-chan result-chan}])
     [:ul
      [:li [:a {:href (path-for :items)} "Items of kti-web"]]
      [:li [:a {:href "/borken/link"} "Borken link"]]]]))

(defn capture-input [{:keys [on-change value]}]
  [:div
   [:span "Capture: "]
   [:input {:type "text"
            :style {:width "60%" :min-width "10cm"}
            :on-change #(-> % .-target .-value on-change)
            :value value}]
   [:div [:i "(current value: " value ")"]]])

(defn capture-form [{:keys [submit-chan result-chan]}]
  (let [value (r/atom "") result (r/atom "")]
    (fn []
      [:div
       [:h3 "Capture Form"]
       [:form
        {:on-submit (fn [e]
                      (.preventDefault e)
                      (put! submit-chan @value)
                      (take! result-chan #(reset! result %)))}
        [capture-input {:value @value :on-change #(reset! value %)}]
        [:button {:type "submit"} "Submit"]
        [:div @result]]])))

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
