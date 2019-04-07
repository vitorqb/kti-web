(ns kti-web.components.select-captured-ref
  (:require
   [cljs.core.async :refer [<! chan go >!]]
   [kti-web.utils :refer [call-with-val call-prevent-default]]
   [kti-web.components.utils :refer [submit-button]]))

(defn select-captured-ref
  [{:keys [get-captured-ref id-value on-id-change on-selection toggle-loading]
    :or {toggle-loading (constantly nil)}}]
  "A form to select a captured reference."
  (letfn [(handle-submit [e]
            (toggle-loading true)
            (let [cap-ref-chan (get-captured-ref id-value) out-chan (chan)]
              (go (on-selection (<! cap-ref-chan))
                  (toggle-loading false)
                  (>! out-chan 1))
              out-chan))]
    [:div
     [:form {:on-submit (call-prevent-default handle-submit)}
      [:span "Choose an id: "]
      [:input {:value id-value
               :on-change (call-with-val on-id-change)
               :type "number"}]
      [submit-button]]]))
