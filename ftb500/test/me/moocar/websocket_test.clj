(ns me.moocar.websocket-test
  (:require [clojure.core.async :as async :refer [go >!! <!! <! >!]]
            [clojure.test :refer [deftest is run-tests]]
            [com.stuartsierra.component :as component]
            [me.moocar.websocket.server :as server]
            [me.moocar.websocket.client :as client]))

(deftest t-all
  (let [server-config {:port :random}
        server-recv-ch (async/chan 1)
        server (component/start (server/new-websocket-server server-config server-recv-ch))]
    (try
      (let [server-port (:local-port server)
            client-config {:hostname "localhost" :port server-port}
            client-recv-ch (async/chan 1)
            client (component/start (client/new-websocket-client client-config client-recv-ch))]
        (try
          (let [msg {:route :moo/car
                     :body "this is my body"}]
            (>!! (:send-ch (:conn client)) msg)
            (go
              (when-let [packet (<! server-recv-ch)]
                (>! (:send-ch packet) (:msg packet))))
            (is (= msg (:msg (<!! client-recv-ch)))))
          (finally
            (component/stop client))))
      (finally
        (component/stop server)))))
