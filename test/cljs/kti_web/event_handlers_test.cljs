(ns kti-web.event-handlers-test
  (:require
   [cljs.test :refer-macros [is are deftest testing use-fixtures async]]
   [cljs.core.async :refer [<! >! to-chan go]]
   [kti-web.event-handlers :as rc]))


(deftest test-gen-handler
  (let [state (atom 1)
        opts {:r-before (fn [state _] (+ state state))
              :action (fn [state extra] (to-chan [(+ state extra)]))
              :r-after (fn [state _ ctx] (+ state ctx))}
        handler (rc/gen-handler state 3 opts)]
    (async done (go
                  ;; We call the handler
                  (let [done-chan (handler)]
                    ;; The before was called and doubled state
                    (is (= @state 2))
                    ;; The handler finishes
                    (is (= :done (<! done-chan)))
                    ;; And state should be (+ state (+ state extra)) == (+ 2 (+ 1 3))
                    (is (= @state 6))
                    (done))))))

(deftest test-gen-handler-vec
  (let [initial-state 1
        state (atom initial-state)
        inject {:inject-value 2}
        event {:event-value 3}
        before (fn before [state inject event]
                 (is (= state initial-state))
                 (is (= inject inject))
                 (is (= event event))
                 ::before-result)
        action (fn action [state inject event]
                 (is (= state initial-state))
                 (is (= inject inject))
                 (is (= event event)) 
                 (to-chan [::result]))
        after  (fn after  [state inject event result]
                 (is (= inject inject))
                 (is (= event event))
                 (is (= result ::result))
                 ::after-result)
        handler (rc/gen-handler-vec state inject [before action after])]
    (async done (go
                  ;; Call the handler
                  (let [done-chan (handler event)]
                    ;; The before was called
                    (is (= @state ::before-result))
                    ;; The handler finished
                    (is (= :done (<! done-chan))))
                  ;; And state is set by after
                  (is (= @state ::after-result))
                  (done)))))
