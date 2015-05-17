(ns me.moocar.websocket.client
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.websocket :as websocket])
  (:import (java.net URI HttpCookie)
           (org.eclipse.jetty.websocket.api UpgradeResponse)
           (org.eclipse.jetty.websocket.client WebSocketClient ClientUpgradeRequest)
           (org.eclipse.jetty.websocket.client.io UpgradeListener)))

(defn- make-uri
  "Creates the full uri using hostname, port and websocket scheme and
  path"
  [{:keys [hostname port] :as this}]
  (let [uri-string (format "ws://%s:%s" hostname port)]
    (URI. uri-string)))

(defn cookie-response-listener
  "Returns an UpgradeListener that adds cookies in the
  HandshakeResponse to the cookie-store for the uri. Required because
  the WebSocketClient doesn't support cookies in the upgrade response"
  [cookie-store uri]
  (reify UpgradeListener
    (onHandshakeRequest [this request])
    (onHandshakeResponse ^void [this response]
      (let [headers (.getHeaders response)]
        (when-let [cookie (first (get headers "Set-Cookie"))]
          (doseq [c (HttpCookie/parse cookie)]
            (.add cookie-store uri c)))))))

(defrecord WebsocketClient [;; configuration
                            port hostname
                            ;; dependencies
                            transport-chans log-ch cookie-store
                            ;; after started
                            jetty-client]
  component/Lifecycle
  (start [this]
    (async/put! log-ch {:websocket-client :started})
    (if jetty-client
      this
      (let [jetty-client (WebSocketClient.)
            uri (make-uri this)
            conn (assoc (websocket/default-conn-f)
                        :log-ch log-ch
                        :client true)
            listener (websocket/listener conn)
            upgrade-request (doto (ClientUpgradeRequest.)
                              (.setRequestURI uri)
                              (.setCookiesFrom cookie-store))
            upgrade-response-listener (cookie-response-listener cookie-store uri)]
        (.setCookieStore jetty-client cookie-store)
        (websocket/conn-loop transport-chans conn)
        (.start jetty-client)
        (let [f (.connect jetty-client listener uri upgrade-request upgrade-response-listener)]
          (if (deref f 1000 nil)
            (assoc this
                   :jetty-client jetty-client)
            (throw (ex-info "Failed to connect"
                            this)))))))
  (stop [this]
    (if jetty-client
      (do
        ;; jetty-client.stop removes all cookies from the cookie
        ;; store, which isn't what we want. Instead, remove the cookie
        ;; store and add it back afterwards
        (.setCookieStore jetty-client nil)
        (.stop jetty-client)
        (.setCookieStore jetty-client cookie-store)
        (assoc this :jetty-client nil :conn nil))
      this)))

(defn construct
  "Creates a new websocket client. config is a map of :port (number)
  and hostname (string) that represent the remote server to connect
  to. recv-ch is a channel upon which incoming messages from the
  server will be put, in the format:

  :msg - the raw clojure message sent from the client
  :send-ch - Channel that can be used to send messages back to the
  client"
  [config]
  (let [{:keys [port hostname]} (get-in config [:server :websocket])]
    (assert port)
    (assert hostname)
    (component/using
      (map->WebsocketClient {:hostname hostname
                             :port port})
      [:transport-chans :log-ch :cookie-store])))
