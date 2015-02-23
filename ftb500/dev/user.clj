(ns user
  (:require [clojure.core.async :as async :refer [<!! >!! go <!]]
            [me.moocar.websocket :as websocket]
            [me.moocar.websocket.client :as client]
            [me.moocar.websocket.server :as server]))

(defn new-server []
  (let [port 8080
        recv-ch (async/chan 1)
        server (server/start-server (server/new-websocket-server {:port port} recv-ch))]
    (try
      (let [cli-recv-ch (async/chan 1)
            client (client/start (client/new-websocket-client cli-recv-ch {:port port :hostname "localhost"}))]
        (>!! (:send-ch (:conn client)) {:route :moo/car
                                        :body "this is my body"})
        (go (println "got" (<! recv-ch)))
        (Thread/sleep 100)

        (client/stop client))
      (finally
        (server/stop server)))))
