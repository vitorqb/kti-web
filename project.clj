(defproject kti-web "0.8.1"
  :description "FIXME: A frontend for KTI"
  :url "https://github.com/vitorqb/kti-web/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring-server "0.5.0"]
                 [ring "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [hiccup "1.0.5"]
                 [yogthos/config "1.1.1"]
                 [metosin/reitit "0.2.13"]]

  :plugins [[lein-environ "1.1.0"]]
  :ring {:handler kti-web.handler/app :uberwar-name "kti-web.war"}
  :min-lein-version "2.5.0"
  :uberjar-name "kti-web.jar"
  :main kti-web.server
  :source-paths ["src/clj" "src/cljc"]
  :resource-paths ["resources"]
  :profiles {:dev {:repl-options {:init-ns kti-web.repl}
                   :dependencies [[ring/ring-mock "0.3.2"]
                                  [ring/ring-devel "1.7.1"]
                                  [prone "1.6.1"]
                                  [nrepl "0.6.0"]
                                  [pjstadig/humane-test-output "0.9.0"]]
                   :source-paths ["env/dev/clj"]
                   :plugins [[cider/cider-nrepl "0.22.0-beta6"]
                             [refactor-nrepl "2.5.0-SNAPSHOT"
                              :exclusions [org.clojure/clojure]]]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]
                   :env {:dev true}}
             :uberjar {:source-paths ["env/prod/clj"]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
