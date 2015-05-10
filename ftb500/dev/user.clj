(ns user
  (:require [clojure.core.async :as async :refer [<!! >!! go <! >! go-loop]]
            [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]
            [me.moocar.inline :as inline]
            [me.moocar.websocket :as websocket]
            [me.moocar.websocket.client :as client]
            [me.moocar.websocket.server :as server]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]))

(defn new-server []
  (let [ws-config {:port 8080 :hostname "localhost"}
        srv-recv-ch (async/chan 1)
        ;server (component/start (server/new-websocket-server ws-config srv-recv-ch))
        server (component/start (inline/new-server srv-recv-ch))]
    (println "SERVER")
    (pprint server)
    (try
      (let [cli-recv-ch (async/chan 1)
            ;client (component/start (client/new-websocket-client cli-recv-ch ws-config))
            client (component/start (inline/new-client server cli-recv-ch))]
        (println "CLIENT")
        (pprint client)
        (go (println "I got a response!" (<! (:recv-ch client))))
        (>!! (:send-ch (:conn client)) {:route :moo/car
                                        :body "this is my body"})
        (go
          (let [msg (<! srv-recv-ch)]
            (pprint (:msg msg))
            (>! (:send-ch msg) {:this :is-another-thing})))
        (Thread/sleep 100)

        (component/stop client))
      (finally
        (component/stop server)))))
