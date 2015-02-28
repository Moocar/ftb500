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
  "Creates a new websocket client. config is a map of :port (number)
  and hostname (string) that represent the remote server to connect
  to. recv-ch is a channel upon which incoming messages from the
  server will be put, in the format:

  :msg - the raw clojure message sent from the client
  :send-ch - Channel that can be used to send messages back to the
  client"
  [config recv-ch]
  (let [{:keys [port hostname]} config]
    (assert port)
    (assert hostname)
    (assert recv-ch)
    (map->WebsocketClient {:recv-ch recv-ch
                           :hostname hostname
                           :port port})))
