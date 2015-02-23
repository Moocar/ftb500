(ns user
  (:require [clojure.core.async :as async :refer [<!! >!! go <! >!]]
            [clojure.pprint :refer [pprint]]
            [me.moocar.websocket :as websocket]
            [me.moocar.websocket.client :as client]
            [me.moocar.websocket.server :as server]))

(defn new-server []
  (let [port 8080
        recv-ch (async/chan 1)
        server (server/start-server (server/new-websocket-server {:port port} recv-ch))]
    (println "SERVER")
    (pprint server)
    (try
      (let [cli-recv-ch (async/chan 1)
            client (client/start (client/new-websocket-client cli-recv-ch {:port port :hostname "localhost"}))]
        (println "CLIENT")
        (pprint client)
        (go (println "I got a response!" (<! (:recv-ch (:conn client)))))
        (>!! (:send-ch (:conn client)) {:route :moo/car
                                        :body "this is my body"})
        (go
          (let [msg (<! recv-ch)]
            (pprint (:msg msg))
            (>! (:send-ch (:conn msg)) {:this :is-another-thing})))
        (Thread/sleep 100)

        (client/stop client))
      (finally
        (server/stop server)))))
