(defproject kti-web "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [ring-server "0.5.0"]
                 [reagent "0.8.1"]
                 [reagent-utils "0.3.2"]
                 [ring "1.7.1"]
                 [ring/ring-defaults "0.3.2"]
                 [hiccup "1.0.5"]
                 [yogthos/config "1.1.1"]
                 [org.clojure/clojurescript "1.10.520"
                  :scope "provided"]
                 [metosin/reitit "0.2.13"]
                 [pez/clerk "1.0.0"]
                 [venantius/accountant "0.2.4"
                  :exclusions [org.clojure/tools.reader]]]

  :plugins [[lein-environ "1.1.0"]
            [lein-cljsbuild "1.1.7"]
            [lein-asset-minifier "0.2.7"
             :exclusions [org.clojure/clojure]]]

  :ring {:handler kti-web.handler/app
         :uberwar-name "kti-web.war"}

  :min-lein-version "2.5.0"
  :uberjar-name "kti-web.jar"
  :main kti-web.server
  :clean-targets ^{:protect false}
  [:target-path
   [:cljsbuild :builds :app :compiler :output-dir]
   [:cljsbuild :builds :app :compiler :output-to]]

  :source-paths ["src/clj" "src/cljc" "src/cljs"]
  :resource-paths ["resources" "target/cljsbuild"]

  :minify-assets
  {:assets
   {"resources/public/css/site.min.css" "resources/public/css/site.css"}}

  :cljsbuild
  {:builds {:min
            {:source-paths ["src/cljs" "src/cljc" "env/prod/cljs"]
             :compiler
             {:output-to        "target/cljsbuild/public/js/app.js"
              :output-dir       "target/cljsbuild/public/js"
              :source-map       "target/cljsbuild/public/js/app.js.map"
              :optimizations :advanced
              :infer-externs true
              :pretty-print  false}}
            :app
            {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
             :figwheel {:on-jsload "kti-web.core/mount-root"}
             :compiler
             {:main "kti-web.dev"
              :asset-path "/js/out"
              :output-to "target/cljsbuild/public/js/app.js"
              :output-dir "target/cljsbuild/public/js/out"
              :source-map true
              :optimizations :none
              :pretty-print  true}}
            :test
            {:source-paths ["src/cljs" "src/cljc" "test/cljs"]
             :compiler {:main kti-web.doo-runner
                        :asset-path "/js/out"
                        :output-to "target/test.js"
                        :output-dir "target/cljstest/public/js/out"
                        :optimizations :whitespace
                        :pretty-print true}}


            }
   }
   :doo {:build "test"
         :alias {:default [:chrome]}}

  :figwheel
  {:http-server-root "public"
   :server-port 3449
   :nrepl-port 7002
   :nrepl-middleware [cider.piggieback/wrap-cljs-repl
                      cider.nrepl/cider-middleware
                      refactor-nrepl.middleware/wrap-refactor
                      ]
   :css-dirs ["resources/public/css"]
   :ring-handler kti-web.handler/app}



  :profiles {:dev {:repl-options {:init-ns kti-web.repl}
                   :dependencies [[cider/piggieback "0.4.0"]
                                  [binaryage/devtools "0.9.10"]
                                  [ring/ring-mock "0.3.2"]
                                  [ring/ring-devel "1.7.1"]
                                  [prone "1.6.1"]
                                  [figwheel-sidecar "0.5.18"]
                                  [nrepl "0.6.0"]
                                  [pjstadig/humane-test-output "0.9.0"]
                                  [com.bhauman/figwheel-main "0.2.1-SNAPSHOT"]
                                  
 ]

                   :source-paths ["env/dev/clj" "env/dev/cljs"]
                   :plugins [[lein-figwheel "0.5.18"]
                             [lein-doo "0.1.10"]
                             [cider/cider-nrepl "0.21.0"]
                             [org.clojure/tools.namespace "0.3.0-alpha4"
                              :exclusions [org.clojure/tools.reader]]
                             [refactor-nrepl "2.4.0"
                              :exclusions [org.clojure/clojure]]
]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :env {:dev true}}

             :uberjar {:hooks [minify-assets.plugin/hooks]
                       :source-paths ["env/prod/clj"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true}})
