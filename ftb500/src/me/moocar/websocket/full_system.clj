(ns me.moocar.websocket.full-system
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.server.system :as server-system]
            [me.moocar.websocket.server :as websocket-server]
            [me.moocar.websocket.client :as websocket-client])
  (:import (org.eclipse.jetty.util HttpCookieStore)))

(defn new-client-system
  [config]
  (let [transport-chans {:send-ch (async/chan 1)
                         :recv-ch (async/chan 1)}
        tag (keyword (str "CLI" (+ 1000 (rand-int 1000))))
        cookie-store (HttpCookieStore.)]
    (component/system-map
     :transport-chans transport-chans
     :client (client/new-pub-client config transport-chans)
     :websocket-client (websocket-client/new-websocket-client config)
     :cookie-store cookie-store
     :log-ch (async/chan 1 (map #(assoc % :system tag))))))

(defn new-server-system
  [config]
  (let [server-recv-ch (async/chan 100)]
    (merge
     (server-system/new-system config server-recv-ch)
     {:websocket-server (websocket-server/new-websocket-server config server-recv-ch)})))

(defn new-system
  [config]
  (merge
   (new-server-system config)
   (new-client-system config)))
