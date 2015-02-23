(ns me.moocar.websocket.server
  (:require [com.stuartsierra.component :as component]
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

(defrecord WebsocketServer [port recv-ch
                            server connector]
  component/Lifecycle
  (start [this]
    (if server
      this
      (let [server (Server.)
            connector (doto (ServerConnector. server)
                        (.setPort port))
            create-websocket-f (create-websocket recv-ch)
            creator (websocket-creator create-websocket-f)
            ws-handler (websocket-handler creator)]
        (.addConnector server connector)
        (.setHandler server ws-handler)
        (.start server)
        (assoc this
               :server server
               :connector connector))))
  (stop [this]
    (if server
      (do
        (.close connector)
        (.stop server)
        (assoc this :server nil :connector nil))
      this)))

(defn new-websocket-server
  [config recv-ch]
  {:pre [(number? (:port config))]}
  (let [{:keys [port]} config]
    (map->WebsocketServer {:port port :recv-ch recv-ch})))
