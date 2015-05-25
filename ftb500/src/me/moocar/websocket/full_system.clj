(ns me.moocar.websocket.full-system
  (:require [clojure.core.async :as async :refer [go-loop <!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.system :as client-system]
            [me.moocar.ftb500.server.system :as server-system]
            [me.moocar.websocket.server :as websocket-server]
            [me.moocar.websocket.client :as websocket-client]
            [me.moocar.websocket.system :as websocket-system])
  (:import (org.eclipse.jetty.util HttpCookieStore)))

(defn new-server-system
  [config]
  (merge
   (websocket-system/new-server config)
   (server-system/construct config)))

(defn new-client-system
  [config]
  (merge
   (websocket-system/new-client config)
   (client-system/construct config)))

(defn new-system
  [config]
  (merge
   (new-server-system config)
   (new-client-system config)))

(defonce ^:dynamic *system* nil)

(defn start []
  (alter-var-root #'*system* (fn [system]
                             (component/start
                              (new-server-system
                               (read-string (slurp "config.edn"))))))
  (go-loop []
    (when-let [msg (<! (:log-ch *system*))]
      (println msg)
      (recur))))

(defn refresh-server
  []
  (component/stop *system*)
  (clojure.tools.namespace.repl/refresh :after 'me.moocar.websocket.full-system/start))
