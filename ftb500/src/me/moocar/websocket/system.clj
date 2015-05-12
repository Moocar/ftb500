(ns me.moocar.websocket.system
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.websocket.client :as client]
            [me.moocar.websocket.server :as server])
  (:import (org.eclipse.jetty.util HttpCookieStore)))

(defn new-server [config]
  (let [tag :SERVER1]
    (component/system-map
     :log-ch (async/chan (async/dropping-buffer 100)
                         (map #(assoc % :system tag)))
     :server-recv-ch (async/chan (async/dropping-buffer 100))
     :me.moocar.websocket/server (server/construct config))))

(defn new-client [config]
  (let [tag (keyword (str "CLI" (+ 1000 (rand-int 1000))))]
    (component/system-map
     :log-ch (async/chan (async/dropping-buffer 100)
                         (map #(assoc % :system tag)))
     :transport-chans {:send-ch (async/chan 1)
                       :recv-ch (async/chan 1)}
     :cookie-store (HttpCookieStore.)
     :me.moocar.websocket/client (client/construct config))))

(defn new-full-system [config]
  (merge (new-server config)
         (new-client config)))
