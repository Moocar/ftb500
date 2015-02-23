(ns me.moocar.websocket.client
  (:require [com.stuartsierra.component :as component]
            [me.moocar.websocket :as websocket])
  (:import (java.net URI)
           (org.eclipse.jetty.websocket.client WebSocketClient)))

(defn- make-uri
  "Creates the full uri using hostname, port and websocket scheme and
  path"
  [{:keys [hostname port] :as this}]
  (let [uri-string (format "ws://%s:%s" hostname port)]
    (URI. uri-string)))

(defrecord WebsocketClient [port hostname recv-ch
                            client]
  component/Lifecycle
  (start [this]
    (if client
      this
      (let [client (WebSocketClient.)
            uri (make-uri this)
            conn (websocket/default-conn-f)
            listener (websocket/listener conn)]
        (websocket/conn-loop recv-ch conn)
        (.start client)
        (if (deref (.connect client listener uri) 1000 nil)
          (assoc this
                 :client client
                 :conn (assoc conn :recv-ch recv-ch))
          (throw (ex-info "Failed to connect"
                          this))))))
  (stop [this]
    (if client
      (do
        (.stop client)
        (assoc this :client nil :conn nil))
      this)))

(defn new-websocket-client
  [recv-ch config]
  (let [{:keys [port hostname]} config]
    (assert port)
    (assert hostname)
    (assert recv-ch)
    (map->WebsocketClient {:recv-ch recv-ch
                           :hostname hostname
                           :port port})))
