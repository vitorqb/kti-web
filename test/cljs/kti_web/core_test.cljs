(ns kti-web.core-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [reagent.core :as reagent :refer [atom]]
   [kti-web.core :as rc]
   [oops.core :as oops]
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

(defn args-saver []
  "Returns an array atom and a function that conj's into the atom
   value all arguments it receives."
  (let [args-atom (atom [])
        save-args (fn [& args] (swap! args-atom conj (into [] args)))]
    [args-atom save-args]))

(deftest test-home
  (with-mounted-component (rc/home-page)
    (fn [c div]
      (is (found-in #"Welcome to" div)))))

(deftest test-select-captured-ref
  (testing "Calls get-captured-ref with chosen id on submit"
    (let [cap-ref-chan (chan 1)
          [get-captured-ref-args save-get-arg] (args-saver)
          get-captured-ref (fn [x] (save-get-arg x) cap-ref-chan)
          comp (rc/select-captured-ref {:id-value 123
                                        :get-captured-ref get-captured-ref
                                        :on-selection (constantly nil)})]
      (go (>! cap-ref-chan {}))
      (is (= 123 (get-in comp [1 3 1 :value])))
      ((get-in comp [1 1 :on-submit]) (clj->js {:preventDefault (fn [] nil)}))
      (is (= [[123]] @get-captured-ref-args))))
  (testing "Calls on-id-change on selected id change"
    (let [[on-id-change-args on-id-change] (args-saver)
          comp (rc/select-captured-ref {:on-id-change on-id-change})]
      ((get-in comp [1 3 1 :on-change]) (clj->js {:target {:value 99}}))
      (is (= [[99]] @on-id-change-args)))))

(deftest test-select-captured-ref--calls-on-selection-when-user-submits
  (let [[on-selection-args on-selection] (args-saver)
        cap-ref {:id 321 :reference "bar" :captured-at "foo"}
        cap-ref-chan (chan 1)
        get-captured-ref (constantly cap-ref-chan)
        comp (rc/select-captured-ref {:on-selection on-selection
                                      :get-captured-ref get-captured-ref})]
    (async done
           (go
             (>! cap-ref-chan cap-ref)
             (let [out-chan
                   ((get-in comp [1 1 :on-submit])
                    (clj->js {:preventDefault (fn [] nil)}))]
               (<! out-chan)
               (is (= [[cap-ref]] @on-selection-args))
               (done))))))

(deftest test-host-input
  (let [[on-change-args on-change] (args-saver)
        comp (rc/host-input-inner {:on-change on-change :value "bar"})]
    (testing "Inits with value"
      (is (= (get-in comp [2 1 :value] "bar"))))
    (testing "Calls onChange at input change"
      ((get-in comp [2 1 :on-change]) (clj->js {:target {:value "foo"}}))
      (is (= @on-change-args [["foo"]])))))

(deftest test-token-input
  (let [[on-change-args on-change] (args-saver)
        comp (rc/token-input-inner {:on-change on-change :value "foo"})]
    (testing "Mounts input"
      (let [input (get comp 2)]
        (is (= (first input) :input)
            (= (get-in input [1 :value]) "foo"))))
    (testing "Calls onChange on change"
      ((get-in comp [2 1 :on-change]) (clj->js {:target {:value "BAR"}}))
      (is (= @on-change-args [["BAR"]])))))

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

(deftest test-edit-captured-ref-form
  (testing "Updates select-captured-ref current id"
    (let [comp-1 (rc/edit-captured-ref-form)]
      (is (= nil (get-in (comp-1) [2 1 :id-value :reference])))
      ((get-in (comp-1) [2 1 :on-id-change]) 921)
      (is (= 921 (get-in (comp-1) [2 1 :id-value])))))
  (testing "Don't show captured-ref-form if no cap. ref. selected"
    (let [cap-ref {:id 3 :reference "foo" :captured-at "bar"}
          comp-1 (rc/edit-captured-ref-form)]
      (is (= true (get-in (comp-1) [3 1 :hidden])))
      ((get-in (comp-1) [2 1 :on-selection]) cap-ref)
      (is (= false (get-in (comp-1) [3 1 :hidden])))))
  (testing "Calls put! on submit"
    (let [[hput!-args save-hput!-args] (args-saver)
          put-chan (chan 1)
          hput! (fn [id cap-ref] (save-hput!-args id cap-ref) put-chan)
          comp-1 (rc/edit-captured-ref-form {:hput! hput!})]
      (put! put-chan {})
      ((get-in (comp-1) [2 1 :on-id-change]) 921)
      ((get-in (comp-1) [2 1 :on-selection]) {:id 921 :reference "foo"})
      ((get-in (comp-1) [3 3 1 :on-submit]) (clj->js {:preventDefault (fn [] nil)}))
      (is (= [[921 {:id 921 :reference "foo"}]] @hput!-args)))))

(deftest test-captured-ref-form
  (testing "Calls on-change if reference changes"
    (let [[on-change-args on-change] (args-saver)
          comp (rc/captured-ref-form
                {:value {:id 99 :reference "foo"} :on-change on-change})]
      (is (= "foo" (get-in comp [3 2 1 :value])))
      ((get-in comp [3 2 1 :on-change]) (clj->js {:target {:value "bar"}}))
      (is (= [[{:id 99 :reference "bar"}]] @on-change-args))))
  (testing "Id input is disabled"
    (let [id 141 comp (rc/captured-ref-form {:value {:id id}})]
      (is (= id (get-in comp [1 2 1 :value])))
      (is (= true (get-in comp [1 2 1 :disabled])))))
  (testing "Created at input is disabled"
    (let [comp (rc/captured-ref-form {:value {:created-at "foo"}})]
      (is (= "foo" (get-in comp [2 2 1 :value])))
      (is (= true (get-in comp [2 2 1 :disabled]))))))

(deftest test-captured-refs-table
  (let [c (chan 1)
        c-done (chan 1)
        comp-1 (rc/captured-refs-table {:get! (constantly c) :c-done c-done})
        comp (comp-1)]
    (is (= [:div "LOADING..."] (get comp 3)))
    (async done (go (>! c [{:id 1
                            :reference "foo"
                            :created-at "2018-01-01T00:00:00"
                            :classified true}])
                    (<! c-done)
                    (is (= :table (get-in (comp-1) [3 0])))
                    (is (= :thead (get-in (comp-1) [3 1 0])))
                    (is (= ["id" "ref" "created at" "classified?"]
                           (-> (comp-1)
                               (get-in [3 1 1 1])
                               (->> (map #(get % 2))))))
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
        ((get-in comp [1 1 :on-submit]) (clj->js {:preventDefault fn-nothing}))
        (is (= [[3]] @delete-args))))))

(deftest test-run-req!-base
  (let [http-fn-chan (chan 1)
        [http-fn-args save-http-fn-args] (args-saver)
        {:keys [http-fn url json-params] :as args}
        {:http-fn (fn [x y] (save-http-fn-args x y) http-fn-chan)
         :url "www.google.com"
         :json-params {:a 1}}
        chan (rc/run-req! args)]
    (is (= @http-fn-args [[url (assoc {:with-credentials? false
                                       :headers
                                       {"authorization" (str "TOKEN " @rc/token)}}
                                      :json-params json-params)]]))
    (async done
           (go (>! http-fn-chan {:success true :body 1})
               (is (= 1 (<! chan)))
               (done)))))

(deftest test-run-req!-error
  (let [http-fn-chan (chan 1)
        [http-fn-args save-http-fn-args] (args-saver)
        http-fn (fn [x y] (save-http-fn-args x y) http-fn-chan)
        res-chan (rc/run-req! {:http-fn http-fn :url 1})]
    (is (= @http-fn-args [[1 {:with-credentials? false
                              :headers
                              {"authorization" (str "TOKEN " @rc/token)}}]]))
    (async done
           (go (>! http-fn-chan {:success false})
               (is (= {:error true} (<! res-chan)))
               (done)))))
