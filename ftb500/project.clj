(defproject me.moocar/ftb500 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.stuartsierra/component "0.2.2"]
                 [me.moocar/lang "0.1.0-SNAPSHOT"]

                 [com.cognitect/transit-clj "0.8.259"]

                 [org.eclipse.jetty.websocket/websocket-api "9.3.0.M1"]
                 [org.eclipse.jetty.websocket/websocket-client "9.3.0.M1"]
                 [org.eclipse.jetty.websocket/websocket-server "9.3.0.M1"]])
