(ns me.moocar.websocket.server
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.websocket :as websocket])
  (:import (org.eclipse.jetty.server Server ServerConnector)
           (org.eclipse.jetty.websocket.server WebSocketHandler)
           (org.eclipse.jetty.websocket.servlet WebSocketCreator
                                                WebSocketServletFactory)))

(defn- websocket-handler
  "WebSocketHandler that creates creator. Boilerplate"
  [creator]
  (proxy [WebSocketHandler] []
    (configure [^WebSocketServletFactory factory]
      (.setCreator factory creator))))

(defn- create-websocket
  "Returns a function that should be called upon a new connection. The
  function creates a new connection map, starts a connection loop and
  returns a listener"
  [recv-ch]
  (fn [this request response]
    (let [conn (websocket/default-conn-f)
          send-ch (async/chan 1)
          listener (websocket/listener conn)]
      (websocket/conn-loop recv-ch send-ch conn)
      listener)))

(defn- websocket-creator
  "Boilerplate"
  [create-websocket-f]
  (reify WebSocketCreator
    (createWebSocket [this request response]
      (create-websocket-f this request response))))

(defrecord WebsocketServer [port recv-ch
                            server connector local-port]
  component/Lifecycle
  (start [this]
    (if server
      this
      (let [port (if (= :random port)
                   0
                   port)
            server (Server.)
            connector (doto (ServerConnector. server)
                        (.setPort port))
            create-websocket-f (create-websocket recv-ch)
            creator (websocket-creator create-websocket-f)
            ws-handler (websocket-handler creator)]
        (.addConnector server connector)
        (.setHandler server ws-handler)
        (.start server)
        (let [local-port (.getLocalPort connector)]
          (assert local-port)
          (assoc this
                 :local-port local-port
                 :server server
                 :connector connector)))))
  (stop [this]
    (if server
      (do
        (.close connector)
        (.stop server)
        (assoc this :server nil :connector nil :local-port nil))
      this)))

(defn new-websocket-server
  "Creates a new Websocket Server. config is a map that must include:
  :port - number, or :random for a random available port (returns
  bound port in :local-port)
  :recv-ch - a channel upon which all new requests will be put as a map with the
  following keys:
   :msg - the raw clojure message sent from the client
   :send-ch - Channel that can be used to send messages back to the
  client"
  [config recv-ch]
  (let [{:keys [port]} config]
    (assert (or (= :random port) (number? port)))
    (map->WebsocketServer {:port port :recv-ch recv-ch})))
