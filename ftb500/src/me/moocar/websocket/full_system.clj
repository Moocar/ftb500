(ns me.moocar.websocket.full-system
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.server.system :as server-system]
            [me.moocar.websocket.server :as websocket-server]
            [me.moocar.websocket.client :as websocket-client]))

(defn new-system
  [config]
  (let [transport-chans {:send-ch (async/chan 1)
                         :recv-ch (async/chan 1)}
        server-recv-ch (async/chan 100)]
    (merge
     (server-system/new-system config server-recv-ch)
     (component/system-map
      :transport-chans transport-chans
      :client (client/new-pub-client config transport-chans)
      :websocket-client (websocket-client/new-websocket-client config)
      :websocket-server (websocket-server/new-websocket-server config server-recv-ch)))))
