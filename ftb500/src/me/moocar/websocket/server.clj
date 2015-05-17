(ns me.moocar.websocket.server
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.lang :refer [uuid]]
            [me.moocar.websocket :as websocket])
  (:import (java.net HttpCookie)
           (org.eclipse.jetty.server Server ServerConnector)
           (org.eclipse.jetty.websocket.server WebSocketHandler)
           (org.eclipse.jetty.websocket.servlet WebSocketCreator
                                                WebSocketServletFactory)))

(def session-id-key
  "JSESSIONID")

(defn- websocket-handler
  "WebSocketHandler that creates creator. Boilerplate"
  [creator]
  (proxy [WebSocketHandler] []
    (configure [^WebSocketServletFactory factory]
      (.setCreator factory creator))))

(defn find-session-cookie
  [cookies]
  (some->> cookies
           (filter #(= session-id-key (.getName %)))
           (first)
           (.getValue)))

(defn session-cookie-string
  [session-id]
  (format "%s=%s; Max-Age=%d"
          session-id-key
          session-id
          (* 60 60 24 30) ;; 30 days
          ))

(defn find-or-set-session-id
  [response cookies]
  (let [persistent-cookie (find-session-cookie cookies)]
    (or persistent-cookie
        (let [session-id (str (uuid))]
          (.addHeader response "Set-Cookie" (session-cookie-string session-id))
          session-id))))

(defn- create-websocket
  "Returns a function that should be called upon a new connection. The
  function creates a new connection map, starts a connection loop and
  returns a listener"
  [this]
  (fn [_ request response]
    (let [request-cookies (.getCookies request)
          session-id (find-or-set-session-id response request-cookies)
          conn (merge (websocket/default-conn-f)
                      (select-keys this [:log-ch])
                      {:session-id session-id})
          transport-chans {:send-ch (async/chan)
                           :recv-ch (:recv-ch this)}
          listener (websocket/listener conn)]
      (websocket/conn-loop transport-chans conn)
      listener)))

(defn- websocket-creator
  "Boilerplate"
  [create-websocket-f]
  (reify WebSocketCreator
    (createWebSocket [this request response]
      (create-websocket-f this request response))))

(defrecord WebsocketServer [;; Configuration
                            port
                            ;; Depenencies
                            recv-ch log-ch
                            ;; After started
                            server connector local-port]
  component/Lifecycle
  (start [this]
    (if server
      this
      (do
        (async/put! log-ch {:starting :websocket-server})
        (async/put! log-ch {:websocket-server :started})
        (let [port (if (= :random port)
                     0
                     port)
              server (Server.)
              connector (doto (ServerConnector. server)
                          (.setPort port))
              create-websocket-f (create-websocket this)
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
                   :connector connector))))))
  (stop [this]
    (if server
      (do
        (.close connector)
        (.stop server)
        (assoc this :server nil :connector nil :local-port nil))
      this)))

(defn construct
  "Creates a new Websocket Server. config is a map that must include:
  :port - number, or :random for a random available port. After the
  component has been started, the bound port will be in :local-port
  :recv-ch - a channel upon which all new requests will be put as a map with the
  following keys:
   :msg - the raw clojure message sent from the client
   :send-ch - Channel that can be used to send messages back to the
  client"
  [config]
  (let [{:keys [port]} (get-in config [:server :websocket])]
    (assert (or (= :random port) (number? port)))
    (component/using
      (map->WebsocketServer {:port port})
      {:log-ch :log-ch
       :recv-ch :server-recv-ch})))
