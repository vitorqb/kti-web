(ns kti-web.components.confirmation-box-test
  (:require
   [kti-web.components.confirmation-box :as rc]
   [kti-web.components.utils :as comp-utils]
   [kti-web.test-utils :as utils]
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]))

(deftest test-confirmation-box
  (testing "Renders title"
    (let [title ::title props {:title title} comp (rc/confirmation-box props)]
      (is (= (get comp 2) [:h3 ::title]))))
  (testing "Renders text"
    (let [text ::text props {:text text} comp (rc/confirmation-box props)]
      (is (= (get comp 3) [:div ::text]))))
  (testing "Calls on-confirmation"
    (let [[args saver] (utils/args-saver)
          props {:on-confirmation saver}
          comp (rc/confirmation-box props)]
      ((get-in comp [4 1 :on-click]))
      (is (= @args [[]]))))
  (testing "Calls on-abortion"
    (let [[args saver] (utils/args-saver)
          props {:on-abortion saver}
          comp (rc/confirmation-box props)]
      ((get-in comp [5 1 :on-click]))
      (is (= @args [[]]))))
  (testing "Deactivates buttons if loading"
    (let [props   {:loading? true}
          komp    (rc/confirmation-box props)
          buttons (vec (map #(komp %) [4 5]))]
      (are [i] (= :button (get-in buttons [i 0])) 0 1)
      (are [i] (true? (get-in buttons [i 1 :disabled])) 0 1)))
  (testing "Shows status"
    (let [status        {:errors ::errors}
          props         {:status status}
          komp          (rc/confirmation-box props)
          err-displayer (komp 6)]
      (is (= err-displayer [comp-utils/errors-displayer props])))))
