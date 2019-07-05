(ns kti-web.components.article-deletor-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.components.article-deletor :as rc]
   [kti-web.components.utils :as components-utils]
   [kti-web.test-utils :as utils]))

(defn new-test-handler [calls-atom]
  (let [save-call! (fn [kw args] (swap! calls-atom update kw #(conj % args)))]
    (reify rc/ArticleDeletorEvents

      (on-delete-article-id-submit [_]
        (save-call! :on-delete-article-id-submit []))

      (on-delete-article-id-change [_ new-value]
        (save-call! :on-delete-article-id-change [new-value])))))

(def handler-calls (atom {}))
(def test-handler (new-test-handler handler-calls))
(def example-state {:delete-article-id ::a
                    :loading? ::b
                    :status {:errors ::c :success-msg ::d}})

(deftest test-reduce-before-article-deletion
  (let [reduce rc/reduce-before-article-deletion]
    (are [k v] (is (= (get (reduce example-state) k) v))
      :delete-article-id ::a
      :status {}
      :loading? true)))

(deftest test-reduce-after-article-deletion
  (let [reduce rc/reduce-before-article-deletion]
    (testing "Success response"
      (let [reduce #(rc/reduce-after-article-deletion % {:error? nil :data {}})]
        (are [k v] (is (= (get (reduce example-state) k) v))
          :delete-article-id ::a
          :status {:success-msg (rc/make-success-msg ::a)}
          :loading? false)))
    (testing "Errored response"
      (let [reduce #(rc/reduce-after-article-deletion % {:error? true :data {::a ::b}})]
        (are [k v] (is (= (get (reduce example-state) k) v))
          :delete-article-id ::a
          :status {:errors {::a ::b}}
          :loading? false)))))

(deftest test-new-handler--on-delete-article-id-change
  (let [state (atom {})
        handler (rc/new-handler state {:delete-article-id ::foo})]
    (rc/on-delete-article-id-change handler ::bar)
    (is (= @state {:delete-article-id ::bar}))))

(deftest test-new-handler--on-delete-article-id-submit
  (let [http-resp {}
        delete-article-chan (async/to-chan http-resp)
        [args saver] (utils/args-saver)
        delete-article! (fn delete-article! [& xs]
                          (saver (vec xs))
                          delete-article-chan)
        delete-article-id 99
        initial-state {:delete-article-id delete-article-id}
        state (atom initial-state)
        props {:delete-article! delete-article!}
        handler (rc/new-handler state props)]
    (async
     done
     (go
       (let [resp-chan (rc/on-delete-article-id-submit handler)]
         ;; State is reduced
         (is (= @state (rc/reduce-before-article-deletion initial-state)))
         ;; Called delete with id
         (is (= @args [[[delete-article-id]]]))
         ;; Response arrives
         (>! delete-article-chan {})
         (is (= (<! resp-chan) :done)))
       ;; State is reduced
       (is (= @state
              (-> initial-state
                  rc/reduce-before-article-deletion
                  (rc/reduce-after-article-deletion http-resp))))
       (done)))))

(deftest test-article-deletor--inner
  (let [mount rc/article-deletor--inner]

    (testing "Renders input for delete-article-id"
      (reset! handler-calls {})
      (let [comp (mount {:delete-article-id ::id :handler test-handler})]
        (is (= (get-in comp [3 2 0]) components-utils/input))
        (is (= (get-in comp [3 2 1 :value]) ::id))
        ((get-in comp [3 2 1 :on-change]) ::foo)
        (is (= (@handler-calls :on-delete-article-id-change)
               [[::foo]]))))

    (testing "On submit"
      (reset! handler-calls {})
      (let [comp (mount {:handler test-handler})]
        ((get-in comp [3 1 :on-submit]) (utils/prevent-default-event))
        (is (= (@handler-calls :on-delete-article-id-submit)
               [[]]))))

    (testing "Display errors"
      (let [status {:status {:errors {::a ::b}}}
            comp (mount status)]
        (is (= (get comp 4)
               [components-utils/errors-displayer status]))))

    (testing "Display success msg"
      (let [props {:status {:success-msg "FOO"}}
            comp (mount props)]
        (is (= (get comp 5) [components-utils/success-message-displayer props]))))

    (testing "Loading..."
      (let [comp (mount {:loading? true})]
        (is (= (get comp 3) [:div "Loading..."]))))))

(deftest test-article-deletor
  (let [mount rc/article-deletor]

    (testing "Mounts inner component with correct args"
      (with-redefs [rc/new-handler (constantly ::new-handler)]
        (let [comp ((mount)) props (get comp 1)]
          (is (= (get-in comp [0]) rc/article-deletor--inner))
          (are [k v] (and (contains? props k) (= (get props k) v))
            :delete-article-id nil
            :status {:errors nil :success-msg nil}
            :loading? false
            :handler ::new-handler))))))

(deftest test-article-deletor-success-submit
  (let [[delete-article!-args save-delete-article!-args] (utils/args-saver)
        delete-chan (async/timeout 3000)
        delete-article! #(do (save-delete-article!-args %) delete-chan)
        comp1 (rc/article-deletor {:delete-article! delete-article!})
        handler (get-in (comp1) [1 :handler])]
    ;; User selects an id
    (rc/on-delete-article-id-change handler ::id)
    ;; And submits
    (let [out-chan (rc/on-delete-article-id-submit handler)]
      (async
       done
       (go
         ;; The component is loading
         (is (true? (get-in (comp1) [1 :loading?])))
         ;; And the response arrives
         (>! delete-chan {:data ::data})
         (is (= (<! out-chan) :done))
         ;; The component is no longer loading
         (is (not (get-in (comp1) [1 :loading?])))
         ;; The deletion was a success
         (is (= (get-in (comp1) [1 :status])
                {:success-msg (rc/make-success-msg ::id)}))
         (done))))))
