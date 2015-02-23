(ns me.moocar.websocket.server
  (:require [me.moocar.websocket :as websocket])
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

(defn- create-websocket [recv-ch]
  (fn [this request response]
    (let [conn (websocket/default-conn-f)
          listener (websocket/listener conn)]
      (websocket/conn-loop recv-ch conn)
      listener)))

(defn- websocket-creator
  [create-websocket-f]
  (reify WebSocketCreator
    (createWebSocket [this request response]
      (create-websocket-f this request response))))

(defn start-server
  [{:keys [port server recv-ch] :as websocket-server}]
  (if server
    websocket-server
    (let [server (Server.)
          connector (doto (ServerConnector. server)
                      (.setPort port))
          create-websocket-f (create-websocket recv-ch)
          creator (websocket-creator create-websocket-f)
          ws-handler (websocket-handler creator)]
      (.addConnector server connector)
      (.setHandler server ws-handler)
      (.start server)
      (assoc websocket-server
        :server server
        :connector connector))))

(defn stop
  "Blocks while Gracefully shutting down the server instance. First,
  the connector is closed to ensure no new connections are accepted.
  Then waits for all in flight requests to finish and finally closes
  the underlying jetty server. Returns immediately if server has
  already been stopped."
  [{:keys [^Server server
           ^ServerConnector connector] :as this}]
  (if server
    (do
      (.close connector)
      (.stop server)
      (assoc this :server nil :connector nil))
    this))

(defn new-websocket-server
  "Creates a new websocket-server (but doesn't start it). config can
  include the following:

  port: The port the server should bind to. Required

  handler-xf: A transducer for handling requests. Input is a request
  map of :conn (connection map), :body-bytes ([bytes offest len])
  and :request-id (long). If a response should be sent back to the
  other side of the connection, the transducer should put request
  object onto result with :response-bytes ([bytes offset len]) assoc'd
  on. Required.

  new-conn-f: a function that takes the original HTTP upgrading
  request and returns a connection map. Defaults to default-conn-f

  If server has already been started, immediately returns"
  [config recv-ch]
  {:pre [(number? (:port config))]}
  (assoc config :recv-ch recv-ch))
