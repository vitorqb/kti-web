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
      (let [reduce (rc/reduce-after-article-deletion {:error? nil :data {}})]
        (are [k v] (is (= (get (reduce example-state) k) v))
          :delete-article-id ::a
          :status {:success-msg (rc/make-success-msg ::a)}
          :loading? false)))
    (testing "Errored response"
      (let [reduce (rc/reduce-after-article-deletion {:error? true :data {::a ::b}})]
        (are [k v] (is (= (get (reduce example-state) k) v))
          :delete-article-id ::a
          :status {:errors {::a ::b}}
          :loading? false)))))

(deftest test-article-deletor--inner
  (let [mount rc/article-deletor--inner]
    (testing "Renders input for delete-article-id"
      (is (= (-> {:delete-article-id ::id :on-delete-article-id-change ::fun}
                 (mount)
                 (get-in [3 2]))
             [rc/article-id-input {:value ::id :on-change ::fun}])))
    (testing "On submit"
      (let [[args fun] (utils/args-saver)
            comp (mount {:on-delete-article-id-submit fun})]
        ((get-in comp [3 1 :on-submit]) (utils/prevent-default-event))
        (is (= @args [[]]))))
    (testing "Display errors"
      (let [comp (mount {:status {:errors {::a ::b}}})]
        (is (= (get comp 4)
               [components-utils/errors-displayer {:errors {::a ::b}}]))))
    (testing "Display success msg"
      (let [comp (mount {:status {:success-msg "FOO"}})]
        (is (= (get comp 5) [:div "FOO"]))))
    (testing "Loading..."
      (let [comp (mount {:loading? true})]
        (is (= (get comp 3) [:div "Loading..."]))))))

(deftest test-article-deletor
  (let [mount rc/article-deletor]
    (testing "Mounts inner component with correct args"
      (let [comp ((mount)) props (get comp 1)]
        (is (= (get-in comp [0]) rc/article-deletor--inner))
        (are [k v] (and (contains? props k) (= (get props k) v))
          :delete-article-id nil
          :status {:errors nil :success-msg nil}
          :loading? false)))
    (testing "Updates article-id on change"
      (let [comp1 (mount)
            get-delete-article-id #(get-in (comp1) [1 :delete-article-id])
            get-on-delete-article-id-change
            #(get-in (comp1) [1 :on-delete-article-id-change])]
        (is (nil? (get-delete-article-id)))
        ((get-on-delete-article-id-change) ::foo)
        (is (= (get-delete-article-id)) ::foo)))))

(deftest test-article-deletor-success-submit
  (let [[delete-article!-args save-delete-article!-args] (utils/args-saver)
        delete-chan (async/timeout 3000)
        delete-article! #(do (save-delete-article!-args %) delete-chan)
        comp1 (rc/article-deletor {:delete-article! delete-article!})]
    ;; User selects an id
    ((get-in (comp1) [1 :on-delete-article-id-change]) ::id)
    ;; And submits
    (let [out-chan ((get-in (comp1) [1 :on-delete-article-id-submit]))]
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
