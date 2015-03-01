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

(defrecord WebsocketClient [port hostname recv-ch send-ch
                            jetty-client]
  component/Lifecycle
  (start [this]
    (if jetty-client
      this
      (let [jetty-client (WebSocketClient.)
            uri (make-uri this)
            conn (websocket/default-conn-f)
            listener (websocket/listener conn)]
        (websocket/conn-loop recv-ch send-ch conn)
        (.start jetty-client)
        (if (deref (.connect jetty-client listener uri) 1000 nil)
          (assoc this
                 :jetty-client jetty-client)
          (throw (ex-info "Failed to connect"
                          this))))))
  (stop [this]
    (if jetty-client
      (do
        (.stop jetty-client)
        (assoc this :jetty-client nil :conn nil))
      this)))

(defn new-websocket-client
  "Creates a new websocket client. config is a map of :port (number)
  and hostname (string) that represent the remote server to connect
  to. recv-ch is a channel upon which incoming messages from the
  server will be put, in the format:

  :msg - the raw clojure message sent from the client
  :send-ch - Channel that can be used to send messages back to the
  client"
  [config recv-ch send-ch]
  (let [{:keys [port hostname]} config]
    (assert port)
    (assert hostname)
    (assert recv-ch)
    (assert send-ch)
    (map->WebsocketClient {:recv-ch recv-ch
                           :send-ch send-ch
                           :hostname hostname
                           :port port})))
