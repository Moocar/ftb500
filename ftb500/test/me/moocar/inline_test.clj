(ns me.moocar.inline-test
  (:require [clojure.core.async :as async :refer [go >!! <!! <! >!]]
            [clojure.test :refer [deftest is run-tests]]
            [com.stuartsierra.component :as component]
            [me.moocar.inline :as inline]))

(deftest t-all
  (let [server-recv-ch (async/chan 1)
        server (component/start (inline/new-server server-recv-ch))]
    (try
      (let [client-transport-chans {:send-ch (async/chan 1)
                                    :recv-ch (async/chan 1)}
            client (component/start (assoc (inline/new-client server)
                                           :transport-chans client-transport-chans))]
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
