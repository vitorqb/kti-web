(ns kti-web.core-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [reagent.core :as reagent :refer [atom]]
   [kti-web.core :as rc]
   [oops.core :as oops]
   [cljs.core.async :refer [>! <! take! put! go chan]]))


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
    (let [comp-1      (rc/capture-form)
          comp        (comp-1)
          on-change   (get-in comp [2 2 1 :on-change])]
      (on-change "new-input")
      (is (= (get-in (comp-1) [2 2 1 :value]) "new-input"))))
  (testing "Sets result after submit"
    (let [c (chan 1)
          c-done (chan 1)
          prevent-default-call-count (atom 0)
          prevent-default #(swap! prevent-default-call-count + 1)
          comp-1 (rc/capture-form {:post! (constantly c) :c-done c-done})
          comp (comp-1)
          on-change (get-in comp [2 2 1 :on-change])
          on-submit (get-in comp [2 1 :on-submit])]
      (on-change "foo")
      (on-submit (clj->js {:preventDefault prevent-default}))
      (is (= @prevent-default-call-count 1))
      (async done (go (>! c {:id 1 :reference "foo"})
                      (<! c-done)
                      (is (= (get-in (comp-1) [2 4 1])
                             "Created with id 1 and ref foo"))
                      (done))))))
