(ns me.moocar.websocket-test
  (:require [clojure.core.async :as async :refer [go >!! <!! <! >! go-loop]]
            [clojure.test :refer [deftest is run-tests]]
            [com.stuartsierra.component :as component]
            [me.moocar.websocket.server :as server]
            [me.moocar.websocket.client :as client])
  (:import (org.eclipse.jetty.util HttpCookieStore)))

(deftest t-all
  (let [server-config {:server {:websocket {:port :random}}}
        server-recv-ch (async/chan 1)
        log-ch (async/chan 1000)
        cookie-store (HttpCookieStore.)
        server (component/start (assoc (server/new-websocket-server server-config server-recv-ch)
                                       :log-ch log-ch))
        server-port (:local-port server)
        client-config {:server {:websocket {:hostname "localhost" :port server-port}}}]
    (is (empty? (.getCookies cookie-store)))
    (try
      (let [client-transport-chans {:send-ch (async/chan 1)
                                    :recv-ch (async/chan 1)}
            client (component/start (assoc (client/new-websocket-client client-config)
                                           :transport-chans client-transport-chans
                                           :log-ch log-ch
                                           :cookie-store cookie-store))]
        (try
          (let [msg {:route :moo/car
                     :body "this is my body"}]
            (>!! (:send-ch client-transport-chans) msg)
            (go
              (when-let [packet (<! server-recv-ch)]
                (is (:session-id packet))
                (>! (:send-ch packet) (:msg packet))))
            (is (= msg (:msg (<!! (:recv-ch client-transport-chans)))))
            (is (= 1 (count (.getCookies cookie-store)))))
          (finally
            (component/stop client))))
      (let [client-transport-chans {:send-ch (async/chan 1)
                                    :recv-ch (async/chan 1)}
            client (component/start (assoc (client/new-websocket-client client-config)
                                           :transport-chans client-transport-chans
                                           :log-ch log-ch
                                           :cookie-store cookie-store))]
        (try
          (is (= 1 (count (.getCookies cookie-store))))
          (finally
            (component/stop client))))
      (finally
        (component/stop server)))
    (async/close! log-ch)
    (go-loop []
      (when-let [msg (<! log-ch)]
        (clojure.pprint/pprint msg)
        (recur)))))
