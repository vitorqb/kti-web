(ns kti-web.core-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures]]
   [reagent.core :as reagent :refer [atom]]
   [kti-web.core :as rc]
   [oops.core :as oops]))55


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
    (let [callback-args (atom nil)
          callback      (fn [arg] (reset! callback-args arg))
          comp          (rc/capture-input {:on-change callback :value ""})
          on-change     (get-in comp [2 1 :on-change])]
      (on-change (clj->js (assoc-in {} [:target :value] "new-input")))
      (is (= @callback-args "new-input"))))

  (testing "Sets value from prop"
    (let [comp    (rc/capture-input {:on-change (constantly nil) :value "hola"})]
      (is (= (get-in comp [2 1 :value]) "hola")))))

(deftest test-capture-form
  (testing "Updates capture-input value on change"
    (let [comp-1    (rc/capture-form)
          comp      (comp-1)
          on-change (get-in comp [2 2 1 :on-change])]
      (on-change "new-input")
      (is (= (get-in (comp-1) [2 2 1 :value]) "new-input"))))
  (testing "Calls ajax-capture! on submit"
    (let [ajax-capture!-arg   (atom nil)
          ajax-capture!       (fn [x] (reset! ajax-capture!-arg x))
          prevent-default-call-count (atom 0)
          prevent-default     (fn [x] (swap! prevent-default-call-count + 1))
          comp                ((rc/capture-form {:ajax-capture! ajax-capture!}))
          on-change           (get-in comp [2 2 1 :on-change])
          on-submit           (get-in comp [2 1 :on-submit])]
      (on-change "foo")
      (on-submit (clj->js {:preventDefault prevent-default}))
      (is (= @prevent-default-call-count 1))
      (is (= @ajax-capture!-arg "foo")))))
