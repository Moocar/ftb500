(defproject me.moocar/ftb500 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-RC1"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.cognitect/transit-clj "0.8.259"]
                 [com.datomic/datomic-free "0.9.5173" :exclusions [joda-time]]
                 [com.stuartsierra/component "0.2.3"]
                 [com.stuartsierra/dependency "0.1.1"]

                 ;; Logging
                 [ch.qos.logback/logback-classic "1.0.13"]
                 [org.slf4j/log4j-over-slf4j "1.7.12"]
                 [org.slf4j/jcl-over-slf4j "1.7.12"]
                 [org.slf4j/jul-to-slf4j "1.7.12"]

                 ;; Clojurescript
                 [reagent "0.5.0"]
                 [org.clojure/clojurescript "0.0-3291" :scope "provided"]

                 [org.eclipse.jetty.websocket/websocket-api "9.3.0.M2"]
                 [org.eclipse.jetty.websocket/websocket-client "9.3.0.M2"]
                 [org.eclipse.jetty.websocket/websocket-server "9.3.0.M2"]]
  :min-lein-version "2.5.0"
  :clean-targets ^{:protect false} [[:cljsbuild :builds :app :compiler :output-dir]
                                    [:cljsbuild :builds :app :compiler :output-to]]
  :cljsbuild {:builds {:app {:source-paths ["src"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        :asset-path   "js/out"
                                        :optimizations :none
                                        :pretty-print  true}}}}
  :profiles {:dev {:source-paths #{"dev"}
                   :dependencies [[org.clojure/tools.namespace "0.2.10"]]
                   :plugins [[lein-cljsbuild "1.0.6"]]}})
