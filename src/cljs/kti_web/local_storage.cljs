(ns kti-web.local-storage)
;; From https://gist.github.com/daveliepmann/cf923140702c8b1de301

(defn set-item! [key val]
  (.setItem (.-localStorage js/window) key val))

(defn get-item [key]
  (.getItem (.-localStorage js/window) key))

(defn remove-item! [key]
  (.removeItem (.-localStorage js/window) key))
