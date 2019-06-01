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

(deftest test-home-page
  (let [comp1 (rc/home-page)]
    (testing "Set's host"
      (is (= (get-in (comp1) [3 2 0]) component-utils/input))
      ((get-in (comp1) [3 2 1 :on-change]) "foo")
      (is (= @state/host "foo"))
      (is (= (get-in (comp1) [3 2 1 :value]) "foo")))
    (testing "Set's token"
      (is (= (get-in (comp1) [3 3 0]) component-utils/input))
      ((get-in (comp1) [3 3 1 :on-change]) "bar")
      (is (= @state/token "bar"))
      (is (= (get-in (comp1) [3 3 1 :value]) "bar")))))

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

(deftest test-delete-captured-ref-form
  (let [get-result-div #(get-in % [1 7])
        get-input-div #(get-in % [1 4])
        fn-nothing (constantly nil)]
    (testing "base"
      (let [ref-id 2 result "foo"
            comp (rc/delete-captured-ref-form-inner
                  {:ref-id ref-id :result result
                   :update-ref-id! fn-nothing :delete! fn-nothing})]
        (is (= :input (-> comp get-input-div (get 0))))
        (is (= {:type "number" :value 2}
               (-> comp get-input-div (get 1) (select-keys [:type :value]))))
        (is (= [:div [:i "(current value: 2)"]] (get-in comp [1 5])))
        (is (= [:div "Result: foo"] (get-result-div comp)))))
    (let [[update-ref-id-args update-ref-id!] (args-saver)
          [delete-args delete!] (args-saver)
          comp (rc/delete-captured-ref-form-inner
                {:ref-id 3 :result nil
                 :update-ref-id! update-ref-id!
                 :delete! delete!})]
      (testing "Dont show result if no result"
        (is (nil? (get-result-div comp))))
      (testing "Calls update-ref-id! on input value change"
        (is (= [] @update-ref-id-args))
        ((get-in comp [1 4 1 :on-change]) (clj->js {:target {:value "foo"}}))
        (is (= [["foo"]] @update-ref-id-args)))
      (testing "Calls delete! on submit"
        (is (= [] @delete-args))
        ((get-in comp [1 1 :on-submit]) (utils/prevent-default-event))
        (is (= [[3]] @delete-args))))))
