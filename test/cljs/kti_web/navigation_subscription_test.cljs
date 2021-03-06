(ns kti-web.navigation-subscription-test
  (:require [kti-web.navigation-subscription :as sut]
            [cljs.core.async
             :refer [<! >!]
             :as async]
            [cljs.test :refer-macros [is are deftest testing use-fixtures async]]))

(deftest test-navigated-route->route-name
  (let [navigated-route->route-name #'sut/navigated-route->route-name]
    (is (= :index
           (navigated-route->route-name {:data {:name :index}})))))

(deftest test-create-page-navigation-subscription--sync
  (testing "Does not subscribe twice"
    (let [call-count (atom 0)
          {:keys [subscribe!]} (sut/create-page-navigation-subscription)]
      (with-redefs [sut/sub! #(swap! call-count inc)]
        (is (= 0 @call-count))
        (subscribe! ::foo :article (async/chan))
        (is (= 1 @call-count))
        (subscribe! ::foo :article (async/chan))
        (is (= 1 @call-count))))))

(deftest test-create-page-navigation-subscription--async
  (let [subscription (sut/create-page-navigation-subscription)
        subscribe! (:subscribe! subscription)
        publish! (:publish! subscription)
        page-name ::foo
        route {:data {:name page-name}}
        subscribed-buff (cljs.core.async/buffer 1)
        subscribed-chan (async/chan)
        _ (subscribe! ::foo page-name subscribed-chan)]
    (async
     done
     (async/go
       ;; Writes the route on the input channel
       (publish! route)
       ;; And receives it from the subscribed chan
       (is (= route (<! subscribed-chan)))
       (done)))))
