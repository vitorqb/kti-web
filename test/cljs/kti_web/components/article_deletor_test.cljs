(ns kti-web.components.article-deletor-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [chan <! >! put! go] :as async]
   [kti-web.components.article-deletor :as rc]
   [kti-web.components.utils :as components-utils]
   [kti-web.test-utils :as utils]))

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

(deftest test-handle-on-delete-article-id-change
  (let [[args saver] (utils/args-saver)
        state (atom {})
        props {:delete-article-id ::foo}
        handler (rc/handle-on-delete-article-id-change state props)]
    (handler 999)
    (is (= @state {:delete-article-id 999}))))

(deftest test--on-delete-article-id-submit
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
        handler (rc/handle-on-delete-article-id-submit state props)]
    (async
     done
     (go
       (let [resp-chan (handler)]
         ;; State is reduced
         (is (= @state (rc/reduce-before-article-deletion initial-state)))
         ;; Called delete with id
         (is (= @args [[[delete-article-id]]]))
         ;; Response arrives
         (>! delete-article-chan {})
         (<! resp-chan))
       ;; State is reduced
       (is (= @state
              (-> initial-state
                  rc/reduce-before-article-deletion
                  (rc/reduce-after-article-deletion http-resp))))
       (done)))))

(deftest test-article-deletor--inner
  (let [mount rc/article-deletor--inner]

    (testing "Renders input for delete-article-id"
      (let [[args saver] (utils/args-saver)
            comp (mount {:delete-article-id ::id
                         :on-delete-article-id-change saver})]
        (is (= (get-in comp [3 2 0]) components-utils/input))
        (is (= (get-in comp [3 2 1 :value]) ::id))
        ((get-in comp [3 2 1 :on-change]) ::foo)
        (is (= @args [[::foo]]))))

    (testing "On submit"
      (let [[args saver] (utils/args-saver)
            comp (mount {:on-delete-article-id-submit saver})]
        ((get-in comp [3 1 :on-submit]) (utils/prevent-default-event))
        (is (= @args [[]]))))

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
      (with-redefs [rc/handle-on-delete-article-id-change
                    (constantly ::on-delete-article-id-change)
                    rc/handle-on-delete-article-id-submit
                    (constantly ::on-delete-article-id-submit)]
        (let [comp ((mount)) props (get comp 1)]
          (is (= (get-in comp [0]) rc/article-deletor--inner))
          (are [k v] (and (contains? props k) (= (get props k) v))
            :delete-article-id nil
            :status {:errors nil :success-msg nil}
            :loading? false
            :on-delete-article-id-change ::on-delete-article-id-change
            :on-delete-article-id-submit ::on-delete-article-id-submit))))))

(deftest test-article-deletor-success-submit
  (let [[delete-article!-args save-delete-article!-args] (utils/args-saver)
        delete-chan (async/timeout 3000)
        delete-article! #(do (save-delete-article!-args %) delete-chan)
        comp1 (rc/article-deletor {:delete-article! delete-article!})
        on-delete-article-id-change (get-in (comp1) [1 :on-delete-article-id-change])
        on-delete-article-id-submit (get-in (comp1) [1 :on-delete-article-id-submit])]
    ;; User selects an id
    (on-delete-article-id-change 9999)
    ;; And submits
    (let [out-chan (on-delete-article-id-submit)]
      (async
       done
       (go
         ;; The component is loading
         (is (true? (get-in (comp1) [1 :loading?])))
         ;; And the response arrives
         (>! delete-chan {:data ::data})
         (<! out-chan)
         ;; The component is no longer loading
         (is (not (get-in (comp1) [1 :loading?])))
         ;; The deletion was a success
         (is (= (get-in (comp1) [1 :status])
                {:success-msg (rc/make-success-msg 9999)}))
         (done))))))
