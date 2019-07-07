(ns kti-web.components.captured-reference-deletor-modal-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [reagent.core :as r]
   [kti-web.test-utils :as utils]
   [kti-web.components.captured-reference-deletor-modal :as rc]))

(deftest test-reduce-on-modal-display-for-deletion
  (let [state   {}
        inject   {}
        reducer #(rc/reduce-on-modal-display-for-deletion state inject %)]
    (is (= (reducer ::delete-captured-ref-id)
           {:status {}
            :active? true
            :delete-captured-ref-id ::delete-captured-ref-id}))))

(deftest test-reduce-on-abortion
  (let [state   {:active? true :delete-captured-ref-id 99}
        inject   {}
        reducer #(rc/reduce-on-abortion state inject %)]
    (is (= (reducer nil) {:active? false :delete-captured-ref-id 99}))))

(deftest test-handler-fns-on-confirm-deletion
  (let [[before action after] rc/handler-fns-on-confirm-deletion]
    (testing "Before"
      (let [state   {:active? ::active? :status ::status}
            inject  {}
            reducer #(before state inject %)]
        (is (= (reducer state) {:active? ::active? :loading? true :status {}}))))
    (testing "Action"
      (let [delete-captured-reference! (fn delete-captured-reference! [id]
                                         (is (= id ::delete-captured-ref-id))
                                         ::delete-captured-reference!)
            state     {:delete-captured-ref-id ::delete-captured-ref-id}
            inject    {:delete-captured-reference! delete-captured-reference!}
            do-action #(action state inject %)]
        (is (= (do-action nil) ::delete-captured-reference!))))
    (testing "After"
      (let [state     {:active?                ::active?
                       :loading?               ::loading?
                       :delete-captured-ref-id ::delete-captured-ref-id}
            inject    {}
            event-val nil
            reducer   #(after state inject event-val %)]
        (is (= (reducer {:error? true :data ::data})
               (assoc state :status {:errors ::data} :loading? false)))
        (is (= (reducer {:error? false :data ::data})
               (assoc state :status {:success-msg "Success!"}
                      :loading? false
                      :active? false)))))))

(deftest test-confirmation-text
  (is (= (rc/confirmation-text 9) "Delete Captured Reference with id 9?")))

(deftest test-captured-reference-deletor-modal--inner
  (let [mount rc/captured-reference-deletor-modal--inner]

    (testing "on-confirmation calls on-confirm-deletion"
      (let [[args saver] (utils/args-saver)
            comp (mount {:on-confirm-deletion saver})]
        ((get-in comp [2 1 :on-confirmation]))
        (is (= @args [[]]))))

    (testing "on-abortion calls on-abortion"
      (let [[args saver] (utils/args-saver)
            comp (mount {:on-abortion saver})]
        ((get-in comp [2 1 :on-abortion]))
        (is (= @args [[]]))))

    (testing "displays confirmation box title"
      (is (= (get-in (mount) [2 1 :title]) rc/title)))

    (testing "Parses :active? to modal"
      (is (= (get-in (mount {:active? ::active?}) [1 :active?])
             ::active?)))

    (testing "displays confirmation box text"
      (let [props {:delete-captured-ref-id ::delete-captured-ref-id}]
        (with-redefs [rc/confirmation-text (fn [id]
                                             (is (= id ::delete-captured-ref-id))
                                             ::confirmation-text)]
          (is (= (get-in (mount props) [2 1 :text]) ::confirmation-text)))))))
