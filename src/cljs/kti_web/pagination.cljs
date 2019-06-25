(ns kti-web.pagination)

(defn is-paginated?
  "Returns true if `r` is a paginated response, according to back end specs."
  [r]
  (and (every? #(% r) [:page :page-size :total-items :items])
       (sequential? (:items r))))
