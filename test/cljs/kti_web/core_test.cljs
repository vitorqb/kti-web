(ns kti-web.core-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [reagent.core :as reagent :refer [atom]]
   [kti-web.core :as rc]
   [kti-web.http :as http]
   [kti-web.utils]
   [kti-web.state :as state]
   [kti-web.components.utils :as component-utils]
   [kti-web.test-utils :as utils :refer [args-saver]]
   [kti-web.test-factories :as factories]
   [cljs.core.async :refer [>! <! take! put! go chan close!]]))


(def isClient (not (nil? (try (.-document js/window)
                              (catch js/Object e nil)))))

(def rflush reagent/flush)

(defn add-test-div [name]
  (let [doc     js/document
        body    (.-body js/document)
        div     (.createElement doc "div")]
    (.appendChild body div)
    div))

(defn with-mounted-component [comp f]
  (when isClient
    (let [div (add-test-div "_testreagent")]
      (let [comp (reagent/render-component comp div #(f comp div))]
        (reagent/unmount-component-at-node div)
        (reagent/flush)
        (.removeChild (.-body js/document) div)))))

(defn found-in [re div]
  (let [res (.-innerHTML div)]
    (if (re-find re res)
      true
      (do (println "Not found: " res)
          false))))

(deftest test-home
  (with-mounted-component (rc/home-page)
    (fn [c div]
      (is (found-in #"Welcome to" div)))))

(deftest test-capture-input
  (testing "Calls callback on change"
    (let [[callback-args callback] (args-saver)
          comp          (rc/capture-input {:on-change callback :value ""})
          on-change     (get-in comp [2 1 :on-change])]
      (on-change (clj->js (assoc-in {} [:target :value] "new-input")))
      (is (= @callback-args [["new-input"]]))))

  (testing "Sets value from prop"
    (let [comp    (rc/capture-input {:on-change (constantly nil) :value "hola"})]
      (is (= (get-in comp [2 1 :value]) "hola")))))

(deftest test-capture-form
  (testing "Updates capture-input value on change"
    (let [comp-1      (rc/capture-form {})
          comp        (comp-1)
          on-change   (get-in comp [2 2 1 :on-change])]
      (on-change "new-input")
      (is (= (get-in (comp-1) [2 2 1 :value]) "new-input")))))

(deftest test-capture-form--sets-error
  (let [get-on-submit #(get-in % [2 1 :on-submit])
        get-result-div #(get-in % [2 4])
        post-chan (chan 1)
        done-chan (chan 1)
        comp-1 (rc/capture-form {:post! (constantly post-chan)
                                 :c-done done-chan})]
    ;; Submits
    ((get-on-submit (comp-1)) (utils/prevent-default-event))
    ;; Result is nil
    (is (= [:div nil] (get-result-div (comp-1))))
    (async done
           (go
             ;; Simulates errored response
             (>! post-chan (http/parse-response factories/http-response-error-msg))
             ;; Waits for completion
             (<! done-chan)
             ;; Result is "Error!"
             (is (= [:div "Error!"] (get-result-div (comp-1))))
             (done)))))

(deftest test-capture-form--sets-result
  (let [post-chan (chan 1)
        done-chan (chan 1)
        comp-1 (rc/capture-form {:post! (constantly post-chan)
                                 :c-done done-chan})
        get-on-submit #(get-in (comp-1) [2 1 :on-submit])
        get-result-div #(get-in (comp-1) [2 4])]
    ;; Submits
    ((get-on-submit) (utils/prevent-default-event))
    (async done
           (go
             ;; Simulates okay response
             (>! post-chan (factories/parsed-ok-response factories/captured-ref))
             ;; Waits
             (<! done-chan)
             ;; Results is corrects
             (is (= [:div "Created with id 49 and ref Foobarbaz"] (get-result-div)))
             (done)))))
