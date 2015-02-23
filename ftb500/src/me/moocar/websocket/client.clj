(ns me.moocar.websocket.client
  (:require [me.moocar.websocket :as websocket])
  (:import (java.net URI)
           (org.eclipse.jetty.websocket.client WebSocketClient)))

(defn- make-uri
  "Creates the full uri using hostname, port and websocket scheme and
  path"
  [{:keys [hostname port] :as this}]
  (let [uri-string (format "ws://%s:%s" hostname port)]
    (URI. uri-string)))

(defn start
  [{:keys [client recv-ch] :as this}]
  (if client
    client
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

(defn stop
  "Immediately stops the client and closes the underlying connection."
  [{:keys [^WebSocketClient client] :as this}]
  (if client
    (do
      (.stop client)
      (assoc this :client nil :conn nil))
    this))

(defn new-websocket-client
  [recv-ch config]
  {:pre [(:hostname config) (:port config)]}
  (merge config {:recv-ch recv-ch}))
