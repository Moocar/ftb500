(ns me.moocar.websocket-test
  (:require [clojure.core.async :as async :refer [go >!! <!! <! >!]]
            [clojure.test :refer [deftest is run-tests]]
            [com.stuartsierra.component :as component]
            [me.moocar.websocket.server :as server]
            [me.moocar.websocket.client :as client]))

(deftest t-all
  (let [server-config {:server {:websocket {:port :random}}}
        server-recv-ch (async/chan 1)
        log-ch (async/chan 1000)
        server (component/start (assoc (server/new-websocket-server server-config server-recv-ch)
                                       :log-ch log-ch))]
    (try
      (let [server-port (:local-port server)
            client-config {:server {:websocket {:hostname "localhost" :port server-port}}}
            client-transport-chans {:send-ch (async/chan 1)
                                    :recv-ch (async/chan 1)}
            client (component/start (assoc (client/new-websocket-client client-config)
                                           :transport-chans client-transport-chans
                                           :log-ch log-ch))]
        (try
          (let [msg {:route :moo/car
                     :body "this is my body"}]
            (>!! (:send-ch client-transport-chans) msg)
            (go
              (when-let [packet (<! server-recv-ch)]
                (>! (:send-ch packet) (:msg packet))))
            (is (= msg (:msg (<!! (:recv-ch client-transport-chans))))))
          (finally
            (component/stop client))))
      (finally
        (component/stop server)))))
